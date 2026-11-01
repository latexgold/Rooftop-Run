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

/** Offline 2.5D rooftop platform shooter drawn on a Canvas. */
public final class GameView extends View {
    private static final float W = 1280f;
    private static final float H = 720f;
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
    private final ArrayList<Platform> roofs = new ArrayList<>();
    private final ArrayList<Enemy> enemies = new ArrayList<>();
    private final ArrayList<Shot> shots = new ArrayList<>();
    private final ArrayList<Token> tokens = new ArrayList<>();
    private final ArrayList<Flag> flags = new ArrayList<>();

    private int screen = MENU;
    private int guideBack = MENU;
    private int mission = 0;
    private int unlocked = 0;
    private int kills = 0;
    private int lives = 3;
    private int tokensTotal = 0;
    private int tokensThisRun = 0;
    private int combo = 0;
    private int leftId = -1;
    private int rightId = -1;
    private int jumpId = -1;
    private int dashId = -1;
    private int shootId = -1;
    private int facing = 1;

    private float x = 100f;
    private float y = 543f;
    private float vx = 0f;
    private float vy = 0f;
    private float cam = 0f;
    private float clock = 0f;
    private float invuln = 0f;
    private float fireWait = 0f;
    private float dashWait = 0f;
    private float comboTime = 0f;
    private float noticeTime = 0f;
    private float shake = 0f;

    private boolean left = false;
    private boolean right = false;
    private boolean jumpHeld = false;
    private boolean shootHeld = false;
    private boolean grounded = false;
    private boolean doubleJump = true;
    private boolean jumpQueued = false;
    private boolean dashQueued = false;

    private int worldW = 4000;
    private float checkpointX = 100f;
    private float checkpointY = 543f;
    private long lastFrame = 0L;
    private String notice = "READY";

    private static final class Platform {
        final float x;
        final float y;
        final float w;

        Platform(float x, float y, float w) {
            this.x = x;
            this.y = y;
            this.w = w;
        }
    }

    private static final class Enemy {
        float x;
        float y;
        final float home;
        float v;
        float hp = 2f;
        boolean alive = true;

        Enemy(float x, float y, float home, float v) {
            this.x = x;
            this.y = y;
            this.home = home;
            this.v = v;
        }
    }

    private static final class Shot {
        float x;
        float y;
        float v;
        float life = 1.1f;

        Shot(float x, float y, float v) {
            this.x = x;
            this.y = y;
            this.v = v;
        }
    }

    private static final class Token {
        final float x;
        final float y;
        boolean taken = false;

        Token(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class Flag {
        final float x;
        final float y;
        boolean active = false;

        Flag(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    public GameView(Context context) {
        super(context);
        setFocusable(true);
        prefs = context.getSharedPreferences("rooftop_progress", Context.MODE_PRIVATE);
        unlocked = prefs.getInt("unlocked", 0);
        tokensTotal = prefs.getInt("tokens", 0);
        makeWorld();
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

        float sx = getWidth() / W;
        float sy = getHeight() / H;
        c.save();
        c.scale(sx, sy);

        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0L ? 0.016f : Math.min(0.033f, (now - lastFrame) / 1000f);
        lastFrame = now;
        clock += dt;

        if (screen == PLAY) {
            update(dt);
        } else if (noticeTime > 0f) {
            noticeTime = Math.max(0f, noticeTime - dt);
        }

        drawScene(c);
        c.restore();

        if (screen == PLAY) {
            invalidate();
        }
    }

    private void startMission(int index) {
        mission = Math.max(0, Math.min(11, index));
        screen = PLAY;
        kills = 0;
        lives = 3;
        tokensThisRun = 0;
        combo = 0;
        comboTime = 0f;
        x = 100f;
        y = 543f;
        checkpointX = x;
        checkpointY = y;
        vx = 0f;
        vy = 0f;
        cam = 0f;
        grounded = false;
        fireWait = 0f;
        dashWait = 0f;
        invuln = 0f;
        show("MISSION START", 1.0f);
        shots.clear();
        makeWorld();
    }

    private void update(float dt) {
        if (noticeTime > 0f) {
            noticeTime -= dt;
        }
        if (fireWait > 0f) {
            fireWait -= dt;
        }
        if (dashWait > 0f) {
            dashWait -= dt;
        }
        if (comboTime > 0f) {
            comboTime -= dt;
        } else {
            combo = 0;
        }
        shake = Math.max(0f, shake - dt * 2.2f);

        float move = (right ? 1f : 0f) - (left ? 1f : 0f);
        if (move != 0f) {
            facing = move > 0 ? 1 : -1;
        }
        vx = approach(vx, move * 335f, 2200f * dt);

        if (jumpQueued) {
            if (grounded) {
                vy = -690f;
                grounded = false;
                doubleJump = true;
            } else if (mission >= 2 && doubleJump) {
                vy = -610f;
                doubleJump = false;
                show("AIR HOP", 0.6f);
            }
            jumpQueued = false;
        }

        if (dashQueued && dashWait <= 0f && mission >= 4) {
            vx = facing * 760f;
            vy = -85f;
            dashWait = 0.68f;
            invuln = Math.max(invuln, 0.24f);
            shake = 0.16f;
            dashQueued = false;
        } else {
            dashQueued = false;
        }

        if (shootHeld && fireWait <= 0f) {
            fire();
        }

        vy = Math.min(1050f, vy + GRAVITY * dt);
        float oldFoot = y + 42f;
        x += vx * dt;
        y += vy * dt;
        grounded = false;

        for (Platform platform : roofs) {
            if (x + 18f > platform.x && x - 18f < platform.x + platform.w && oldFoot <= platform.y + 3f && y + 42f >= platform.y && vy >= 0f) {
                y = platform.y - 42f;
                vy = 0f;
                grounded = true;
                doubleJump = true;
            }
        }

        x = Math.max(20f, Math.min(worldW - 60f, x));
        cam = Math.max(0f, Math.min(worldW - W, x - 390f));

        for (Flag flag : flags) {
            if (!flag.active && Math.abs(x - flag.x) < 35f && Math.abs(y - flag.y) < 56f) {
                flag.active = true;
                checkpointX = flag.x;
                checkpointY = flag.y;
                show("CHECKPOINT", 1.2f);
                haptic(HapticFeedbackConstants.CONFIRM);
            }
        }

        for (Token token : tokens) {
            if (!token.taken && Math.abs(x - token.x) < 30f && Math.abs(y - token.y) < 48f) {
                token.taken = true;
                tokensTotal++;
                tokensThisRun++;
                prefs.edit().putInt("tokens", tokensTotal).apply();
                show("TOKEN +1", 0.8f);
            }
        }

        for (Enemy enemy : enemies) {
            if (enemy.alive) {
                enemy.x += enemy.v * dt;
                if (enemy.x < enemy.home - 92f || enemy.x > enemy.home + 92f) {
                    enemy.v = -enemy.v;
                }
                if (invuln <= 0f && Math.abs(x - enemy.x) < 34f && Math.abs(y - enemy.y) < 52f) {
                    damage();
                }
            }
        }

        if (invuln > 0f) {
            invuln -= dt;
        }

        for (int i = shots.size() - 1; i >= 0; i--) {
            Shot shot = shots.get(i);
            shot.x += shot.v * dt;
            shot.life -= dt;
            boolean hit = false;

            for (Enemy enemy : enemies) {
                if (enemy.alive && Math.abs(shot.x - enemy.x) < 27f && Math.abs(shot.y - (enemy.y - 20f)) < 43f) {
                    enemy.hp -= 1f;
                    hit = true;
                    combo++;
                    comboTime = 1.25f;
                    if (enemy.hp <= 0f) {
                        enemy.alive = false;
                        kills++;
                        show("TARGET DOWN", 0.45f);
                    }
                    break;
                }
            }

            if (hit || shot.life <= 0f) {
                shots.remove(i);
            }
        }

        if (y > 820f) {
            damage();
            return;
        }

        if (x > worldW - 175f) {
            if (kills >= targets()) {
                screen = CLEAR;
                unlocked = Math.max(unlocked, Math.min(11, mission + 1));
                prefs.edit().putInt("unlocked", unlocked).putInt("tokens", tokensTotal).apply();
                show("MISSION CLEAR", 1.2f);
            } else {
                show("KEEP CLEARING", 0.8f);
                x = worldW - 175f;
            }
        }
    }

    private float approach(float current, float target, float step) {
        if (current < target) {
            return Math.min(target, current + step);
        }
        return Math.max(target, current - step);
    }

    private int targets() {
        return Math.min(3, 1 + mission / 4);
    }

    private int accent() {
        if (mission / 4 == 0) {
            return Color.rgb(255, 189, 99);
        }
        if (mission / 4 == 1) {
            return Color.rgb(99, 229, 204);
        }
        return Color.rgb(173, 145, 255);
    }

    private void fire() {
        Enemy best = null;
        float score = Float.MAX_VALUE;

        for (Enemy enemy : enemies) {
            if (enemy.alive) {
                float dx = (enemy.x - x) * facing;
                if (dx > 0f) {
                    float q = dx + Math.abs(enemy.y - y) * 1.35f;
                    if (q < score && q < 760f) {
                        score = q;
                        best = enemy;
                    }
                }
            }
        }

        if (best != null) {
            shots.add(new Shot(x + facing * 20f, y - 12f, facing * 620f));
            fireWait = 0.18f;
        }
    }

    private void damage() {
        if (invuln > 0f || screen != PLAY) {
            return;
        }

        lives--;
        combo = 0;
        comboTime = 0f;
        shake = 0.5f;
        vx = 0f;
        vy = 0f;
        shots.clear();
        releaseAll();
        dashWait = 0.2f;

        if (lives <= 0) {
            screen = FAIL;
            haptic(HapticFeedbackConstants.REJECT);
            show("ROUTE INTERRUPTED", 1.2f);
        } else {
            x = checkpointX;
            y = checkpointY;
            invuln = 1.4f;
            show("CHECKPOINT", 0.8f);
        }
    }

    private void show(String text, float seconds) {
        notice = text;
        noticeTime = seconds;
    }

    private void haptic(int type) {
        try {
            performHapticFeedback(type);
        } catch (Exception ignored) {
            // Some devices deny haptic access; fail silently.
        }
    }

    private void drawScene(Canvas c) {
        switch (screen) {
            case MENU:
                drawMenu(c);
                break;
            case PLAY:
                drawSky(c);
                drawWorld(c);
                drawHud(c);
                drawTouchButtons(c);
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
        c.drawColor(Color.rgb(9, 15, 29));
        for (int layer = 0; layer < 3; layer++) {
            float off = (cam * (0.10f + layer * 0.13f)) % 240f;
            for (int i = -1; i < 8; i++) {
                float bx = i * 240f - off;
                float h = 150f + (Math.floorMod(i + layer, 3) * 40f);
                int tint = layer == 0 ? 0x3350A8E8 : (layer == 1 ? 0x223B7CFF : 0x1A4B6F9A);
                paint.setColor(tint);
                c.drawRect(bx, 560f - h, bx + 215f, 560f, paint);
            }
        }
    }

    private void drawWorld(Canvas c) {
        c.save();
        float jx = shake > 0f ? (float) Math.sin(clock * 76f) * shake * 8f : 0f;
        float jy = shake > 0f ? (float) Math.cos(clock * 61f) * shake * 5f : 0f;
        c.translate(-cam + jx, jy);

        int color = accent();
        for (Platform platform : roofs) {
            paint.setColor(0xFF233B57);
            c.drawRect(platform.x, platform.y, platform.x + platform.w, platform.y + 12f, paint);
            paint.setColor(color);
            c.drawRect(platform.x + 2f, platform.y + 2f, platform.x + platform.w - 2f, platform.y + 8f, paint);
        }

        for (Flag flag : flags) {
            drawFlag(c, flag);
        }
        for (Token token : tokens) {
            drawToken(c, token);
        }
        for (Enemy enemy : enemies) {
            if (enemy.alive) {
                drawEnemy(c, enemy);
            }
        }

        drawPlayer(c, x, y);
        drawExit(c, worldW - 110f, 530f, color);
        c.restore();

        drawHud(c);
    }

    private void drawHud(Canvas c) {
        panel(c, 18f, 15f, 475f, 82f, 0xCC101B30);
        text(c, String.format(Locale.US, "DISTRICT %02d   ·   MISSION %02d", mission / 4 + 1, mission + 1),
                18f, Color.WHITE, 34f, 45f);
        text(c, String.format(Locale.US, "LIVES %d   ·   TOKENS %d   ·   KILLS %d/%d",
                        lives, tokensTotal, kills, targets()), 18f, 0xFFB9CBE4, 34f, 72f);
        if (noticeTime > 0f) {
            text(c, notice, 24f, 0xFFFFBD63, 650f, 52f);
        }
    }

    private void drawFlag(Canvas c, Flag flag) {
        paint.setColor(flag.active ? 0xFF63E5CC : 0xFFA9B9CB);
        c.drawRoundRect(flag.x - 3f, flag.y - 34f, flag.x + 3f, flag.y + 37f, 3f, 3f, paint);
        paint.setColor(flag.active ? 0xFF63E5CC : 0xFFFFBD63);
        Path path = new Path();
        path.moveTo(flag.x + 8f, flag.y - 30f);
        path.lineTo(flag.x + 8f, flag.y + 30f);
        path.lineTo(flag.x + 30f, flag.y + 12f);
        path.close();
        c.drawPath(path, paint);
    }

    private void drawToken(Canvas c, Token token) {
        if (token.taken) {
            return;
        }
        float bob = (float) Math.sin(clock * 4f + token.x) * 4f;
        paint.setColor(0x66FFDB72);
        c.drawCircle(token.x, token.y + bob, 17f, paint);
        paint.setColor(0xFFFFD66E);
        Path path = new Path();
        path.moveTo(token.x, token.y - 12f + bob);
        path.lineTo(token.x + 10f, token.y + bob);
        path.lineTo(token.x, token.y + 12f + bob);
        path.lineTo(token.x - 10f, token.y + bob);
        path.close();
        c.drawPath(path, paint);
    }

    private void drawEnemy(Canvas c, Enemy enemy) {
        float a = enemy.x;
        float b = enemy.y;
        paint.setColor(Color.rgb(189, 83, 105));
        c.drawRoundRect(a - 18f, b - 20f, a + 18f, b + 28f, 10f, 10f, paint);
        paint.setColor(Color.rgb(232, 174, 144));
        c.drawCircle(a, b - 32f, 12f, paint);
        paint.setColor(Color.rgb(46, 42, 58));
        c.drawRect(a - 16f, b + 12f, a + 16f, b + 18f, paint);
    }

    private void drawPlayer(Canvas c, float a, float b) {
        c.save();
        c.translate(a, b);
        if (facing < 0) {
            c.scale(-1f, 1f);
        }
        if (invuln > 0f && ((int) (clock * 18f) % 2) == 0) {
            c.restore();
            return;
        }

        paint.setColor(0xFFEAEEF7);
        c.drawRoundRect(-18f, -42f, 18f, 18f, 10f, 10f, paint);
        paint.setColor(0xFF3D5AFB);
        c.drawRoundRect(-12f, -20f, 12f, 8f, 8f, 8f, paint);
        paint.setColor(0xFFFFBD63);
        c.drawRect(-10f, -8f, 10f, 10f, paint);
        paint.setColor(0xFF2C3347);
        c.drawRect(-14f, 12f, -6f, 32f, paint);
        c.drawRect(6f, 12f, 14f, 32f, paint);
        paint.setColor(0xFF6A82FF);
        c.drawRect(-2f, -42f, -10f, -60f, paint);
        c.drawRect(2f, -42f, 10f, -60f, paint);
        c.restore();
    }

    private void drawExit(Canvas c, float a, float b, int color) {
        paint.setColor(Color.argb(50, Color.red(color), Color.green(color), Color.blue(color)));
        c.drawCircle(a, b, 45f + (float) Math.sin(clock * 3f) * 4f, paint);
        paint.setColor(color);
        c.drawCircle(a, b, 18f, paint);
    }

    private void drawTouchButtons(Canvas c) {
        button(c, 68f, 610f, 82f, 68f, "◀", 0xDD253C5A);
        button(c, 160f, 610f, 82f, 68f, "▶", 0xDD253C5A);
        button(c, 972f, 602f, 104f, 72f, mission >= 2 ? "HOP" : "JUMP", 0xDD344D70);
        button(c, 1086f, 602f, 104f, 72f, "DASH", 0xDD344D70);
        button(c, 1198f, 602f, 104f, 72f, "FIRE", 0xDD344D70);
    }

    private void drawMenu(Canvas c) {
        shade(c);
        text(c, "ROOFTOP RUN", 57f, 0xFFFFBF68, 110f, 178f);
        text(c, "CITY ABOVE THE STREETS", 20f, 0xFF99B1CC, 115f, 221f);
        text(c, "A 2.5D PLATFORM SHOOTER", 17f, 0xFFB8C7D9, 115f, 258f);
        button(c, 100f, 332f, 260f, 74f, "START", 0xFFFFBD63);
        button(c, 100f, 423f, 260f, 64f, "MISSIONS", 0xDD253C5A);
        button(c, 100f, 500f, 260f, 64f, "FIELD GUIDE", 0xDD253C5A);
    }

    private void drawMap(Canvas c) {
        shade(c);
        text(c, "MISSION BOARD", 48f, 0xFFFFBF68, 82f, 104f);
        for (int i = 0; i < 12; i++) {
            int col = i % 4;
            int row = i / 4;
            float bx = 82f + col * 286f;
            float by = 142f + row * 140f;
            boolean open = i <= unlocked;
            panel(c, bx, by, 220f, 104f, open ? 0xCC263C58 : 0xCC111B2A);
            text(c, String.format(Locale.US, "%02d", i + 1), 18f, Color.WHITE, bx + 18f, by + 32f);
            text(c, name(i), 20f, open ? 0xFFF5C96C : 0xFF6F7E94, bx + 18f, by + 62f);
            if (!open) {
                text(c, "LOCKED", 16f, 0xFF72839E, bx + 18f, by + 86f);
            }
        }
    }

    private String name(int index) {
        String[] names = {
                "First Contact", "Awning Alley", "Fire Escape", "Neon Crossing",
                "Cable Run", "Rain Check", "The Long Drop", "Billboard Run",
                "Sky Garden", "Cloudline", "Last Delivery", "Highline"
        };
        return names[index];
    }

    private void drawGuide(Canvas c) {
        shade(c);
        text(c, "FIELD GUIDE", 48f, 0xFFFFBF68, 90f, 118f);
        String[] entries = {
                "MOVE   Use the two left buttons to cross rooftops.",
                "JUMP   Tap before a ledge. Hold briefly for a larger hop.",
                "DASH   Unlocks after mission 4 and helps dodge danger.",
                "SHOOT   Fire at the nearest target ahead.",
                "SAVE   Checkpoints store progress and keep runs alive."
        };
        for (int i = 0; i < entries.length; i++) {
            text(c, entries[i], 18f, 0xFFE4EDF7, 96f, 170f + i * 42f);
        }
        button(c, 96f, 600f, 260f, 60f, "BACK", 0xFFFFBD63);
    }

    private void drawPauseOverlay(Canvas c) {
        shade(c);
        text(c, "PAUSED", 52f, 0xFFFFBF68, 100f, 218f);
        button(c, 95f, 283f, 300f, 65f, "RESUME", 0xFFFFBD63);
        button(c, 95f, 362f, 300f, 58f, "FIELD GUIDE", 0xDD253C5A);
        button(c, 95f, 434f, 300f, 58f, "MISSION BOARD", 0xDD253C5A);
        button(c, 95f, 507f, 300f, 58f, "MAIN MENU", 0xDD253C5A);
    }

    private void drawClearOverlay(Canvas c) {
        shade(c);
        text(c, "MISSION COMPLETE", 45f, 0xFF63E5CC, 94f, 215f);
        text(c, String.format(Locale.US, "Targets cleared: %d / %d", kills, targets()), 21f, Color.WHITE, 100f, 268f);
        text(c, String.format(Locale.US, "Tokens this run: %d", tokensThisRun), 20f, 0xFFBFEAE0, 100f, 306f);
        button(c, 95f, 370f, 300f, 60f, "NEXT MISSION", 0xFFFFBD63);
        button(c, 95f, 444f, 300f, 58f, "MISSION BOARD", 0xDD253C5A);
    }

    private void drawFailOverlay(Canvas c) {
        shade(c);
        text(c, "ROUTE INTERRUPTED", 42f, 0xFFFF8389, 95f, 218f);
        text(c, "Checkpoint reached. Ready for another run?", 20f, 0xFFE0E8F1, 100f, 270f);
        button(c, 95f, 330f, 310f, 65f, "RETRY", 0xFFFFBD63);
        button(c, 95f, 410f, 310f, 58f, "MISSION BOARD", 0xDD253C5A);
        button(c, 95f, 482f, 310f, 58f, "MAIN MENU", 0xDD253C5A);
    }

    private void shade(Canvas c) {
        paint.setColor(0xCC07101F);
        c.drawRect(0f, 0f, W, H, paint);
    }

    private void button(Canvas c, float x, float y, float w, float h, String text, int color) {
        paint.setColor(0x55000000);
        c.drawRoundRect(x, y + 4f, x + w, y + h + 4f, 16f, 16f, paint);
        paint.setColor(color);
        c.drawRoundRect(x, y, x + w, y + h, 18f, 18f, paint);
        paint.setColor(0xFFECF3FF);
        paint.setTextSize(28f);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        c.drawText(text, x + (w / 2f) - paint.measureText(text) / 2f, y + h / 2f + 10f, paint);
    }

    private void panel(Canvas c, float x, float y, float w, float h, int color) {
        paint.setColor(color);
        c.drawRoundRect(x, y, x + w, y + h, 15f, 15f, paint);
    }

    private void text(Canvas c, String text, float size, int color, float x, float y) {
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        c.drawText(text, x, y, paint);
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
                if (hit(tx, ty, 100f, 332f, 260f, 74f)) {
                    startMission(0);
                } else if (hit(tx, ty, 100f, 423f, 260f, 64f)) {
                    screen = MAP;
                } else if (hit(tx, ty, 100f, 500f, 260f, 64f)) {
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
                    float bx = 82f + col * 286f;
                    float by = 142f + row * 140f;
                    if (hit(tx, ty, bx, by, 220f, 104f) && i <= unlocked) {
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
                if (hit(tx, ty, 96f, 600f, 260f, 60f)) {
                    screen = guideBack;
                }
            }
            return true;
        }

        if (screen == PAUSE) {
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                if (hit(tx, ty, 95f, 283f, 300f, 65f)) {
                    screen = PLAY;
                    lastFrame = SystemClock.uptimeMillis();
                } else if (hit(tx, ty, 95f, 362f, 300f, 58f)) {
                    guideBack = PAUSE;
                    screen = GUIDE;
                } else if (hit(tx, ty, 95f, 434f, 300f, 58f)) {
                    screen = MAP;
                } else if (hit(tx, ty, 95f, 507f, 300f, 58f)) {
                    screen = MENU;
                }
            }
            return true;
        }

        if (screen == CLEAR) {
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                if (hit(tx, ty, 95f, 370f, 300f, 60f)) {
                    startMission(Math.min(11, mission + 1));
                } else if (hit(tx, ty, 95f, 444f, 300f, 58f)) {
                    screen = MAP;
                }
            }
            return true;
        }

        if (screen == FAIL) {
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                if (hit(tx, ty, 95f, 330f, 310f, 65f)) {
                    startMission(mission);
                } else if (hit(tx, ty, 95f, 410f, 310f, 58f)) {
                    screen = MAP;
                } else if (hit(tx, ty, 95f, 482f, 310f, 58f)) {
                    screen = MENU;
                }
            }
            return true;
        }

        if (screen == PLAY) {
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                if (hit(tx, ty, 68f, 610f, 82f, 68f)) {
                    leftId = pointerId;
                    left = true;
                } else if (hit(tx, ty, 160f, 610f, 82f, 68f)) {
                    rightId = pointerId;
                    right = true;
                } else if (hit(tx, ty, 972f, 602f, 104f, 72f)) {
                    jumpId = pointerId;
                    jumpHeld = true;
                    jumpQueued = true;
                } else if (hit(tx, ty, 1086f, 602f, 104f, 72f)) {
                    dashId = pointerId;
                    dashQueued = true;
                } else if (hit(tx, ty, 1198f, 602f, 104f, 72f)) {
                    shootId = pointerId;
                    shootHeld = true;
                }
            }

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                release(pointerId);
            }

            if (action == MotionEvent.ACTION_MOVE) {
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int pid = event.getPointerId(i);
                    float mx = event.getX(i);
                    float my = event.getY(i);

                    if (pid == leftId && !hit(mx, my, 68f, 610f, 82f, 68f)) {
                        left = false;
                    }
                    if (pid == rightId && !hit(mx, my, 160f, 610f, 82f, 68f)) {
                        right = false;
                    }
                    if (pid == jumpId && !hit(mx, my, 972f, 602f, 104f, 72f)) {
                        jumpHeld = false;
                    }
                    if (pid == shootId && !hit(mx, my, 1198f, 602f, 104f, 72f)) {
                        shootHeld = false;
                    }
                }
            }

            invalidate();
            return true;
        }

        return super.onTouchEvent(event);
    }

    private void release(int pointerId) {
        if (pointerId == leftId) {
            leftId = -1;
            left = false;
        }
        if (pointerId == rightId) {
            rightId = -1;
            right = false;
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

    private void releaseAll() {
        leftId = -1;
        rightId = -1;
        jumpId = -1;
        dashId = -1;
        shootId = -1;
        left = false;
        right = false;
        jumpHeld = false;
        shootHeld = false;
    }

    private boolean hit(float x, float y, float bx, float by, float w, float h) {
        return x >= bx && x <= bx + w && y >= by && y <= by + h;
    }

    private void makeWorld() {
        roofs.clear();
        roofs.add(new Platform(90f, 560f, 220f));
        roofs.add(new Platform(360f, 492f, 210f));
        roofs.add(new Platform(610f, 520f, 260f));
        roofs.add(new Platform(930f, 470f, 260f));
        roofs.add(new Platform(1230f, 540f, 230f));
        roofs.add(new Platform(1480f, 460f, 210f));
        roofs.add(new Platform(1710f, 388f, 220f));
        roofs.add(new Platform(1950f, 470f, 240f));
        roofs.add(new Platform(2230f, 430f, 190f));
        roofs.add(new Platform(2460f, 500f, 250f));
        roofs.add(new Platform(2750f, 420f, 208f));
        roofs.add(new Platform(3000f, 520f, 320f));
        roofs.add(new Platform(3420f, 490f, 230f));

        flags.clear();
        flags.add(new Flag(280f, 512f));
        flags.add(new Flag(920f, 432f));
        flags.add(new Flag(1880f, 432f));
        flags.add(new Flag(2820f, 382f));
        flags.add(new Flag(3480f, 450f));

        tokens.clear();
        tokens.add(new Token(220f, 514f));
        tokens.add(new Token(690f, 478f));
        tokens.add(new Token(1120f, 432f));
        tokens.add(new Token(1605f, 340f));
        tokens.add(new Token(2280f, 392f));
        tokens.add(new Token(3010f, 472f));

        enemies.clear();
        enemies.add(new Enemy(480f, 510f, 440f, 90f));
        enemies.add(new Enemy(1290f, 500f, 1290f, 120f));
        enemies.add(new Enemy(1765f, 350f, 1765f, 85f));
        enemies.add(new Enemy(2560f, 470f, 2560f, 110f));
        enemies.add(new Enemy(3310f, 450f, 3310f, 95f));
    }
}
