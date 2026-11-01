package com.latexgold.rooftoprun;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Locale;

/** Skyline Sprint is a cleaner, more stable rooftop runner built on the same core idea. */
public final class GameView extends View {
    private static final float VIEW_W = 1280f;
    private static final float VIEW_H = 720f;
    private static final float GRAVITY = 1850f;

    private static final int MENU = 0;
    private static final int PLAY = 1;
    private static final int PAUSE = 2;
    private static final int GUIDE = 3;
    private static final int MAP = 4;
    private static final int CLEAR = 5;
    private static final int FAIL = 6;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SharedPreferences prefs;
    private final ArrayList<Platform> platforms = new ArrayList<>();
    private final ArrayList<Enemy> enemies = new ArrayList<>();
    private final ArrayList<Pickup> pickups = new ArrayList<>();
    private final ArrayList<Checkpoint> checkpoints = new ArrayList<>();
    private final ArrayList<Shot> shots = new ArrayList<>();

    private int screen = MENU;
    private int guideBack = MENU;
    private int mission = 0;
    private int unlocked = 0;
    private int score = 0;
    private int lives = 3;
    private int tokensTotal = 0;
    private int tokensThisRun = 0;
    private int kills = 0;
    private int combo = 0;
    private int leftId = -1;
    private int rightId = -1;
    private int jumpId = -1;
    private int dashId = -1;
    private int shootId = -1;
    private int facing = 1;

    private float worldW = 4200f;
    private float playerX = 120f;
    private float playerY = 520f;
    private float playerW = 30f;
    private float playerH = 42f;
    private float playerVx = 0f;
    private float playerVy = 0f;
    private float camX = 0f;
    private float clock = 0f;
    private float invuln = 0f;
    private float fireCooldown = 0f;
    private float dashCooldown = 0f;
    private float comboTime = 0f;
    private float noticeTime = 0f;
    private float shake = 0f;
    private float checkpointX = 120f;
    private float checkpointY = 520f;
    private float gateX = worldW - 160f;
    private float gateY = 500f;

    private boolean leftHeld = false;
    private boolean rightHeld = false;
    private boolean jumpHeld = false;
    private boolean shootHeld = false;
    private boolean grounded = false;
    private boolean jumpQueued = false;
    private boolean dashQueued = false;
    private boolean doubleJumpUnlocked = false;

    private long lastFrame = 0L;
    private String notice = "READY";

    private static final class Platform {
        final float x;
        final float y;
        final float w;
        final float h;

        Platform(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private static final class Enemy {
        float x;
        float y;
        final float minX;
        final float maxX;
        float vx;
        final float h = 32f;
        final float w = 28f;
        boolean alive = true;

        Enemy(float x, float y, float minX, float maxX, float vx) {
            this.x = x;
            this.y = y;
            this.minX = minX;
            this.maxX = maxX;
            this.vx = vx;
        }
    }

    private static final class Pickup {
        final float x;
        final float y;
        boolean taken = false;

        Pickup(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class Checkpoint {
        final float x;
        final float y;
        boolean active = false;

        Checkpoint(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class Shot {
        float x;
        float y;
        float vx;
        float life = 1.2f;

        Shot(float x, float y, float vx) {
            this.x = x;
            this.y = y;
            this.vx = vx;
        }
    }

    public GameView(Context context) {
        super(context);
        setFocusable(true);
        prefs = context.getSharedPreferences("rooftop_progress", Context.MODE_PRIVATE);
        unlocked = prefs.getInt("unlocked", 0);
        tokensTotal = prefs.getInt("tokens", 0);
        buildWorld();
    }

    public void pauseGame() {
        if (screen == PLAY) {
            screen = PAUSE;
        }
    }

    public void resumeGame() {
        lastFrame = SystemClock.uptimeMillis();
        invalidate();
    }

    public void backAction() {
        if (screen == PLAY) {
            screen = PAUSE;
        } else if (screen != MENU) {
            screen = MENU;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        float sx = getWidth() / VIEW_W;
        float sy = getHeight() / VIEW_H;
        c.save();
        c.scale(sx, sy);

        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0L ? 0.016f : Math.min(0.033f, (now - lastFrame) / 1000f);
        lastFrame = now;
        clock += dt;

        if (screen == PLAY) {
            updateGame(dt);
        } else if (noticeTime > 0f) {
            noticeTime = Math.max(0f, noticeTime - dt);
        }

        render(c);
        c.restore();

        if (screen == PLAY) {
            invalidate();
        }
    }

    private void startMission(int index) {
        mission = Math.max(0, Math.min(11, index));
        screen = PLAY;
        lives = 3;
        score = 0;
        kills = 0;
        tokensThisRun = 0;
        combo = 0;
        playerX = 120f;
        playerY = 520f;
        playerVx = 0f;
        playerVy = 0f;
        checkpointX = playerX;
        checkpointY = playerY;
        invuln = 0f;
        fireCooldown = 0f;
        dashCooldown = 0f;
        grounded = false;
        jumpQueued = false;
        dashQueued = false;
        buildWorld();
        show("MISSION START", 1.0f);
    }

    private void updateGame(float dt) {
        if (noticeTime > 0f) {
            noticeTime -= dt;
        }
        if (fireCooldown > 0f) {
            fireCooldown -= dt;
        }
        if (dashCooldown > 0f) {
            dashCooldown -= dt;
        }
        if (comboTime > 0f) {
            comboTime -= dt;
        } else {
            combo = 0;
        }
        shake = Math.max(0f, shake - dt * 2.5f);

        float direction = (rightHeld ? 1f : 0f) - (leftHeld ? 1f : 0f);
        if (direction != 0f) {
            facing = direction > 0 ? 1 : -1;
        }

        float targetVx = direction * 340f;
        playerVx = approach(playerVx, targetVx, 2200f * dt);

        if (jumpQueued) {
            if (grounded) {
                playerVy = -740f;
                grounded = false;
                doubleJumpUnlocked = true;
            } else if (mission >= 2 && doubleJumpUnlocked) {
                playerVy = -680f;
                doubleJumpUnlocked = false;
                show("AIR HOP", 0.5f);
            }
            jumpQueued = false;
        }

        if (dashQueued && dashCooldown <= 0f && mission >= 4) {
            playerVx = facing * 760f;
            playerVy = -90f;
            dashCooldown = 0.7f;
            invuln = Math.max(invuln, 0.25f);
            shake = 0.18f;
            dashQueued = false;
        } else {
            dashQueued = false;
        }

        if (shootHeld && fireCooldown <= 0f) {
            fireShot();
        }

        float oldBottom = playerY + playerH;
        playerVy = Math.min(1060f, playerVy + GRAVITY * dt);
        playerY += playerVy * dt;
        playerX += playerVx * dt;

        grounded = false;
        for (Platform platform : platforms) {
            if (rectCollides(playerX, playerY, playerW, playerH, platform.x, platform.y, platform.w, platform.h)) {
                float playerBottom = playerY + playerH;
                float platformTop = platform.y;
                if (oldBottom <= platformTop + 8f && playerVy >= 0f && playerBottom >= platformTop && playerY < platformTop + platform.h) {
                    playerY = platform.y - playerH;
                    playerVy = 0f;
                    grounded = true;
                    doubleJumpUnlocked = true;
                }
            }
        }

        playerX = clamp(playerX, 20f, worldW - playerW - 20f);
        camX = clamp(playerX - 420f, 0f, worldW - VIEW_W);

        for (Checkpoint checkpoint : checkpoints) {
            if (!checkpoint.active && Math.abs(playerX - checkpoint.x) < 32f && Math.abs(playerY - checkpoint.y) < 65f) {
                checkpoint.active = true;
                checkpointX = checkpoint.x;
                checkpointY = checkpoint.y;
                show("CHECKPOINT", 1.1f);
                haptic(HapticFeedbackConstants.CONFIRM);
            }
        }

        for (Pickup pickup : pickups) {
            if (!pickup.taken && Math.abs(playerX - pickup.x) < 30f && Math.abs(playerY - pickup.y) < 34f) {
                pickup.taken = true;
                score += 100;
                tokensThisRun += 1;
                tokensTotal += 1;
                prefs.edit().putInt("tokens", tokensTotal).apply();
                show("TOKEN +1", 0.8f);
            }
        }

        for (Enemy enemy : enemies) {
            if (!enemy.alive) {
                continue;
            }
            enemy.x += enemy.vx * dt;
            if (enemy.x <= enemy.minX || enemy.x >= enemy.maxX) {
                enemy.vx *= -1f;
            }

            if (rectCollides(playerX, playerY, playerW, playerH, enemy.x, enemy.y, enemy.w, enemy.h)) {
                hurtPlayer();
            }
        }

        for (Shot shot : shots) {
            shot.x += shot.vx * dt;
            shot.life -= dt;
            for (Enemy enemy : enemies) {
                if (enemy.alive && rectCollides(shot.x - 4f, shot.y - 4f, 8f, 8f, enemy.x, enemy.y, enemy.w, enemy.h)) {
                    enemy.alive = false;
                    kills++;
                    score += 150;
                    shot.life = 0f;
                    show("TARGET DOWN", 0.4f);
                    break;
                }
            }
        }

        for (int i = shots.size() - 1; i >= 0; i--) {
            if (shots.get(i).life <= 0f) {
                shots.remove(i);
            }
        }

        if (invuln > 0f) {
            invuln -= dt;
        }

        if (playerY > VIEW_H + 260f) {
            hurtPlayer();
        }

        if (playerX >= gateX && collectedAllTokens()) {
            screen = CLEAR;
            unlocked = Math.max(unlocked, Math.min(11, mission + 1));
            prefs.edit().putInt("unlocked", unlocked).apply();
            show("CITY CLEARED", 1.2f);
        }
    }

    private boolean rectCollides(float ax, float ay, float aw, float ah, float bx, float by, float bw, float bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    private void hurtPlayer() {
        if (invuln > 0f || screen != PLAY) {
            return;
        }

        lives--;
        combo = 0;
        comboTime = 0f;
        shake = 0.7f;
        playerVx = 0f;
        playerVy = 0f;
        shots.clear();
        releaseAllButtons();

        if (lives <= 0) {
            screen = FAIL;
            haptic(HapticFeedbackConstants.REJECT);
            show("RUN FAILED", 1.2f);
            return;
        }

        playerX = checkpointX;
        playerY = checkpointY;
        invuln = 1.4f;
        show("CHECKPOINT", 0.8f);
    }

    private void fireShot() {
        float shotX = playerX + (facing > 0 ? playerW : -8f);
        float shotY = playerY + playerH * 0.5f;
        shots.add(new Shot(shotX, shotY, facing * 620f));
        fireCooldown = 0.18f;
    }

    private boolean collectedAllTokens() {
        for (Pickup pickup : pickups) {
            if (!pickup.taken) {
                return false;
            }
        }
        return true;
    }

    private float clamp(float v, float min, float max) {
        return Math.min(Math.max(v, min), max);
    }

    private float approach(float current, float target, float step) {
        if (current < target) {
            return Math.min(target, current + step);
        }
        return Math.max(target, current - step);
    }

    private void show(String text, float seconds) {
        notice = text;
        noticeTime = seconds;
    }

    private void haptic(int type) {
        try {
            performHapticFeedback(type);
        } catch (Exception ignored) {
            // Silent on devices without haptic support.
        }
    }

    private void render(Canvas c) {
        switch (screen) {
            case MENU:
                drawMenu(c);
                break;
            case PLAY:
                drawSky(c);
                drawWorld(c);
                drawHud(c);
                drawGameButtons(c);
                break;
            case PAUSE:
                drawSky(c);
                drawWorld(c);
                drawPauseOverlay(c);
                break;
            case GUIDE:
                drawGuide(c);
                break;
            case MAP:
                drawMap(c);
                break;
            case CLEAR:
                drawSky(c);
                drawWorld(c);
                drawClearOverlay(c);
                break;
            case FAIL:
                drawSky(c);
                drawWorld(c);
                drawFailOverlay(c);
                break;
            default:
                drawMenu(c);
                break;
        }
    }

    private void drawSky(Canvas c) {
        c.drawColor(Color.rgb(8, 14, 28));
        for (int i = 0; i < 8; i++) {
            float x = (i * 210f - (camX * 0.2f)) % (VIEW_W + 240f);
            paint.setColor(i % 2 == 0 ? 0x1A2D4A7A : 0x1C3C5D84);
            c.drawRect(x, 235f, x + 180f, VIEW_H, paint);
        }
    }

    private void drawWorld(Canvas c) {
        c.save();
        c.translate(-camX, 0f);

        int accent = missionPalette();
        for (Platform platform : platforms) {
            paint.setColor(0xFF1E2E45);
            c.drawRect(platform.x, platform.y, platform.x + platform.w, platform.y + platform.h, paint);
            paint.setColor(accent);
            c.drawRect(platform.x + 4f, platform.y + 4f, platform.x + platform.w - 4f, platform.y + 10f, paint);
        }

        for (Checkpoint checkpoint : checkpoints) {
            drawCheckpoint(c, checkpoint);
        }
        for (Pickup pickup : pickups) {
            if (!pickup.taken) {
                drawPickup(c, pickup);
            }
        }
        for (Enemy enemy : enemies) {
            if (enemy.alive) {
                drawEnemy(c, enemy);
            }
        }
        for (Shot shot : shots) {
            paint.setColor(0xFFFFD66E);
            c.drawCircle(shot.x, shot.y, 6f, paint);
        }

        drawGate(c, gateX, 494f, accent);
        drawPlayer(c, playerX, playerY);
        c.restore();
    }

    private void drawHud(Canvas c) {
        panel(c, 18f, 18f, 560f, 78f, 0xCC0D1828);
        text(c, String.format(Locale.US, "DISTRICT %02d   •   MISSION %02d", mission / 4 + 1, mission + 1),
                18f, Color.WHITE, 34f, 50f);
        text(c, String.format(Locale.US, "LIVES %d   •   TOKENS %d   •   SCORE %d   •   KILLS %d",
                        lives, tokensTotal, score, kills), 18f, 0xFFB7C6D9, 34f, 76f);
        if (noticeTime > 0f) {
            text(c, notice, 22f, 0xFFFFC76B, 610f, 52f);
        }
    }

    private void drawCheckpoint(Canvas c, Checkpoint checkpoint) {
        if (checkpoint.active) {
            paint.setColor(0xFF63E5CC);
        } else {
            paint.setColor(0xFFB8C7D9);
        }
        c.drawRect(checkpoint.x - 3f, checkpoint.y - 40f, checkpoint.x + 3f, checkpoint.y + 18f, paint);
        Path path = new Path();
        path.moveTo(checkpoint.x + 8f, checkpoint.y - 34f);
        path.lineTo(checkpoint.x + 8f, checkpoint.y + 22f);
        path.lineTo(checkpoint.x + 32f, checkpoint.y - 6f);
        path.close();
        c.drawPath(path, paint);
    }

    private void drawPickup(Canvas c, Pickup pickup) {
        float bob = (float) Math.sin(clock * 4f + pickup.x) * 4f;
        paint.setColor(0x66FFDB72);
        c.drawCircle(pickup.x, pickup.y + bob, 14f, paint);
        paint.setColor(0xFFFFD66E);
        Path path = new Path();
        path.moveTo(pickup.x, pickup.y - 10f + bob);
        path.lineTo(pickup.x + 8f, pickup.y + bob);
        path.lineTo(pickup.x, pickup.y + 10f + bob);
        path.lineTo(pickup.x - 8f, pickup.y + bob);
        path.close();
        c.drawPath(path, paint);
    }

    private void drawEnemy(Canvas c, Enemy enemy) {
        paint.setColor(Color.rgb(193, 82, 96));
        c.drawRoundRect(enemy.x, enemy.y, enemy.x + enemy.w, enemy.y + enemy.h, 10f, 10f, paint);
        paint.setColor(Color.rgb(228, 181, 154));
        c.drawCircle(enemy.x + enemy.w * 0.5f, enemy.y + 8f, 9f, paint);
    }

    private void drawGate(Canvas c, float x, float y, int accent) {
        paint.setColor(Color.argb(50, Color.red(accent), Color.green(accent), Color.blue(accent)));
        c.drawCircle(x, y, 44f + (float) Math.sin(clock * 3.2f) * 4f, paint);
        paint.setColor(accent);
        c.drawCircle(x, y, 18f, paint);
    }

    private void drawPlayer(Canvas c, float x, float y) {
        c.save();
        c.translate(x, y);
        if (facing < 0) {
            c.scale(-1f, 1f);
        }
        if (invuln > 0f && ((int) (clock * 18f) % 2) == 0) {
            c.restore();
            return;
        }

        paint.setColor(0xFFEEF2F8);
        c.drawRoundRect(0f, 0f, playerW, playerH, 8f, 8f, paint);
        paint.setColor(0xFF5878FF);
        c.drawRect(6f, 10f, playerW - 6f, 24f, paint);
        paint.setColor(0xFFFFC767);
        c.drawRect(7f, playerH - 12f, playerW - 7f, playerH - 2f, paint);
        c.restore();
    }

    private void drawGameButtons(Canvas c) {
        button(c, 42f, 610f, 128f, 72f, "◀", 0xDD2B3958);
        button(c, 182f, 610f, 128f, 72f, "▶", 0xDD2B3958);
        button(c, 1008f, 618f, 118f, 70f, "JUMP", 0xDD3E5C9A);
        button(c, 1138f, 618f, 118f, 70f, "DASH", 0xDD3E5C9A);
        button(c, 1008f, 540f, 248f, 60f, "FIRE", 0xDD3E5C9A);
    }

    private void drawMenu(Canvas c) {
        shade(c);
        text(c, "SKYLINE SPRINT", 62f, 0xFFFFC76B, 120f, 180f);
        text(c, "DIGITAL ROOFTOP RUNNER", 20f, 0xFFB2C3DA, 122f, 228f);
        button(c, 120f, 330f, 280f, 76f, "START", 0xFFFFC76B);
        button(c, 120f, 425f, 280f, 64f, "MISSIONS", 0xDD2B3958);
        button(c, 120f, 502f, 280f, 64f, "FIELD GUIDE", 0xDD2B3958);
    }

    private void drawMap(Canvas c) {
        shade(c);
        text(c, "MISSION BOARD", 48f, 0xFFFFC76B, 90f, 112f);
        for (int i = 0; i < 12; i++) {
            int col = i % 4;
            int row = i / 4;
            float bx = 92f + col * 270f;
            float by = 150f + row * 150f;
            boolean open = i <= unlocked;
            panel(c, bx, by, 210f, 110f, open ? 0xCC243B58 : 0xCC121B2C);
            text(c, String.format(Locale.US, "%02d", i + 1), 18f, Color.WHITE, bx + 18f, by + 32f);
            text(c, zoneName(i), 20f, open ? 0xFFF2C77A : 0xFF7E8BA6, bx + 18f, by + 64f);
            if (!open) {
                text(c, "LOCKED", 16f, 0xFF73859E, bx + 18f, by + 88f);
            }
        }
    }

    private String zoneName(int index) {
        String[] names = {
                "Old Town", "Cable Plaza", "Glass Ward", "Rain Run",
                "Basilica Pass", "Signal Arc", "Sky Market", "North Lift",
                "Sun Deck", "Cloudline", "Horizon Gate", "Final Jump"
        };
        return names[index];
    }

    private void drawGuide(Canvas c) {
        shade(c);
        text(c, "FIELD GUIDE", 48f, 0xFFFFC76B, 100f, 120f);
        String[] entries = {
                "MOVE   Left/right buttons to cross rooftops.",
                "JUMP   Tap to leap from platform to platform.",
                "DASH   Use after mission 4 to burst through danger.",
                "FIRE   Clear sentries before they reach you.",
                "GOAL   Grab all sparks and reach the glowing gate."
        };
        for (int i = 0; i < entries.length; i++) {
            text(c, entries[i], 18f, 0xFFE5EDF9, 100f, 180f + i * 42f);
        }
        button(c, 100f, 610f, 250f, 60f, "BACK", 0xFFFFC76B);
    }

    private void drawPauseOverlay(Canvas c) {
        shade(c);
        text(c, "PAUSED", 52f, 0xFFFFC76B, 100f, 220f);
        button(c, 100f, 285f, 310f, 62f, "RESUME", 0xFFFFC76B);
        button(c, 100f, 365f, 310f, 58f, "GUIDE", 0xDD2B3958);
        button(c, 100f, 438f, 310f, 58f, "MISSION BOARD", 0xDD2B3958);
        button(c, 100f, 510f, 310f, 58f, "MAIN MENU", 0xDD2B3958);
    }

    private void drawClearOverlay(Canvas c) {
        shade(c);
        text(c, "ZONE CLEARED", 45f, 0xFF63E5CC, 96f, 220f);
        text(c, String.format(Locale.US, "Spirits collected: %d", tokensThisRun), 20f, 0xFFBDEFE8, 100f, 272f);
        button(c, 96f, 370f, 300f, 62f, "NEXT ZONE", 0xFFFFC76B);
        button(c, 96f, 448f, 300f, 58f, "MISSION BOARD", 0xDD2B3958);
    }

    private void drawFailOverlay(Canvas c) {
        shade(c);
        text(c, "ROUTE LOST", 46f, 0xFFFF6B7E, 98f, 220f);
        text(c, "The skyline is still open. Try again.", 20f, 0xFFE4ECF9, 102f, 274f);
        button(c, 98f, 335f, 300f, 62f, "RETRY", 0xFFFFC76B);
        button(c, 98f, 418f, 300f, 58f, "MISSION BOARD", 0xDD2B3958);
        button(c, 98f, 491f, 300f, 58f, "MAIN MENU", 0xDD2B3958);
    }

    private void shade(Canvas c) {
        paint.setColor(0xCC07101F);
        c.drawRect(0f, 0f, VIEW_W, VIEW_H, paint);
    }

    private void button(Canvas c, float x, float y, float w, float h, String text, int color) {
        paint.setColor(0x55000000);
        c.drawRoundRect(x, y + 5f, x + w, y + h + 5f, 18f, 18f, paint);
        paint.setColor(color);
        c.drawRoundRect(x, y, x + w, y + h, 18f, 18f, paint);
        paint.setColor(0xFFEDF3FF);
        paint.setTextSize(26f);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        c.drawText(text, x + (w / 2f) - paint.measureText(text) / 2f, y + h / 2f + 10f, paint);
    }

    private void panel(Canvas c, float x, float y, float w, float h, int color) {
        paint.setColor(color);
        c.drawRoundRect(x, y, x + w, y + h, 16f, 16f, paint);
    }

    private void text(Canvas c, String text, float size, int color, float x, float y) {
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(android.graphics.TypeFace.create("sans-serif-medium", 0));
        c.drawText(text, x, y, paint);
    }

    private int missionPalette() {
        int district = mission / 4;
        if (district == 0) {
            return Color.rgb(255, 190, 103);
        }
        if (district == 1) {
            return Color.rgb(102, 235, 207);
        }
        return Color.rgb(178, 144, 255);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        int pointerId = event.getPointerId(index);
        float tx = event.getX(index);
        float ty = event.getY(index);

        if (screen == MENU) {
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                if (hit(tx, ty, 120f, 330f, 280f, 76f)) {
                    startMission(0);
                } else if (hit(tx, ty, 120f, 425f, 280f, 64f)) {
                    screen = MAP;
                } else if (hit(tx, ty, 120f, 502f, 280f, 64f)) {
                    guideBack = MENU;
                    screen = GUIDE;
                }
            }
            return true;
        }

        if (screen == MAP) {
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                for (int i = 0; i < 12; i++) {
                    int col = i % 4;
                    int row = i / 4;
                    float bx = 92f + col * 270f;
                    float by = 150f + row * 150f;
                    if (i <= unlocked && hit(tx, ty, bx, by, 210f, 110f)) {
                        startMission(i);
                        return true;
                    }
                }
                screen = MENU;
            }
            return true;
        }

        if (screen == GUIDE) {
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                if (hit(tx, ty, 100f, 610f, 250f, 60f)) {
                    screen = guideBack;
                }
            }
            return true;
        }

        if (screen == PAUSE) {
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                if (hit(tx, ty, 100f, 285f, 310f, 62f)) {
                    screen = PLAY;
                    lastFrame = SystemClock.uptimeMillis();
                } else if (hit(tx, ty, 100f, 365f, 310f, 58f)) {
                    guideBack = PAUSE;
                    screen = GUIDE;
                } else if (hit(tx, ty, 100f, 438f, 310f, 58f)) {
                    screen = MAP;
                } else if (hit(tx, ty, 100f, 510f, 310f, 58f)) {
                    screen = MENU;
                }
            }
            return true;
        }

        if (screen == CLEAR) {
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                if (hit(tx, ty, 96f, 370f, 300f, 62f)) {
                    startMission(Math.min(11, mission + 1));
                } else if (hit(tx, ty, 96f, 448f, 300f, 58f)) {
                    screen = MAP;
                }
            }
            return true;
        }

        if (screen == FAIL) {
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                if (hit(tx, ty, 98f, 335f, 300f, 62f)) {
                    startMission(mission);
                } else if (hit(tx, ty, 98f, 418f, 300f, 58f)) {
                    screen = MAP;
                } else if (hit(tx, ty, 98f, 491f, 300f, 58f)) {
                    screen = MENU;
                }
            }
            return true;
        }

        if (screen == PLAY) {
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                if (hit(tx, ty, 42f, 610f, 128f, 72f)) {
                    leftId = pointerId;
                    leftHeld = true;
                } else if (hit(tx, ty, 182f, 610f, 128f, 72f)) {
                    rightId = pointerId;
                    rightHeld = true;
                } else if (hit(tx, ty, 1008f, 618f, 118f, 70f)) {
                    jumpId = pointerId;
                    jumpHeld = true;
                    jumpQueued = true;
                } else if (hit(tx, ty, 1138f, 618f, 118f, 70f)) {
                    dashId = pointerId;
                    dashQueued = true;
                } else if (hit(tx, ty, 1008f, 540f, 248f, 60f)) {
                    shootId = pointerId;
                    shootHeld = true;
                }
            }

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                releasePointer(pointerId);
            }

            if (action == MotionEvent.ACTION_MOVE) {
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int pid = event.getPointerId(i);
                    float mx = event.getX(i);
                    float my = event.getY(i);
                    if (pid == leftId && !hit(mx, my, 42f, 610f, 128f, 72f)) {
                        leftHeld = false;
                    }
                    if (pid == rightId && !hit(mx, my, 182f, 610f, 128f, 72f)) {
                        rightHeld = false;
                    }
                    if (pid == jumpId && !hit(mx, my, 1008f, 618f, 118f, 70f)) {
                        jumpHeld = false;
                    }
                    if (pid == shootId && !hit(mx, my, 1008f, 540f, 248f, 60f)) {
                        shootHeld = false;
                    }
                }
            }

            invalidate();
            return true;
        }

        return super.onTouchEvent(event);
    }

    private void releasePointer(int pointerId) {
        if (pointerId == leftId) {
            leftId = -1;
            leftHeld = false;
        }
        if (pointerId == rightId) {
            rightId = -1;
            rightHeld = false;
        }
        if (pointerId == jumpId) {
            jumpId = -1;
            jumpHeld = false;
        }
        if (pointerId == dashId) {
            dashId = -1;
            dashQueued = true;
        }
        if (pointerId == shootId) {
            shootId = -1;
            shootHeld = false;
        }
    }

    private void releaseAllButtons() {
        leftId = -1;
        rightId = -1;
        jumpId = -1;
        dashId = -1;
        shootId = -1;
        leftHeld = false;
        rightHeld = false;
        jumpHeld = false;
        shootHeld = false;
    }

    private boolean hit(float x, float y, float bx, float by, float w, float h) {
        return x >= bx && x <= bx + w && y >= by && y <= by + h;
    }

    private void buildWorld() {
        platforms.clear();
        platforms.add(new Platform(0f, 620f, 420f, 100f));
        platforms.add(new Platform(470f, 620f, 360f, 100f));
        platforms.add(new Platform(930f, 560f, 220f, 20f));
        platforms.add(new Platform(1280f, 500f, 200f, 20f));
        platforms.add(new Platform(1640f, 560f, 220f, 20f));
        platforms.add(new Platform(2010f, 500f, 220f, 20f));
        platforms.add(new Platform(2360f, 560f, 220f, 20f));
        platforms.add(new Platform(2720f, 470f, 220f, 20f));
        platforms.add(new Platform(3110f, 540f, 240f, 20f));
        platforms.add(new Platform(3460f, 490f, 220f, 20f));
        platforms.add(new Platform(3700f, 620f, 540f, 100f));

        checkpoints.clear();
        checkpoints.add(new Checkpoint(240f, 560f));
        checkpoints.add(new Checkpoint(1490f, 460f));
        checkpoints.add(new Checkpoint(2860f, 430f));
        checkpoints.add(new Checkpoint(3600f, 560f));

        pickups.clear();
        pickups.add(new Pickup(210f, 575f));
        pickups.add(new Pickup(1120f, 520f));
        pickups.add(new Pickup(1760f, 520f));
        pickups.add(new Pickup(2530f, 520f));
        pickups.add(new Pickup(3240f, 500f));
        pickups.add(new Pickup(3920f, 580f));

        enemies.clear();
        enemies.add(new Enemy(540f, 588f, 490f, 820f, 90f));
        enemies.add(new Enemy(1360f, 468f, 1300f, 1460f, 130f));
        enemies.add(new Enemy(1900f, 528f, 1670f, 2050f, 110f));
        enemies.add(new Enemy(2600f, 528f, 2380f, 2780f, 120f));
        enemies.add(new Enemy(3300f, 508f, 3120f, 3520f, 125f));

        gateX = worldW - 160f;
    }
}
