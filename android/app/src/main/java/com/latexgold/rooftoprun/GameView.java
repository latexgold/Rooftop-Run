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
import java.util.Random;

/** Offline 2.5D rooftop platform shooter, drawn with Android Canvas. */
public final class GameView extends View {
    private static final float W=1280f,H=720f,GRAVITY=1850f;
    private static final int MENU=0,PLAY=1,PAUSE=2,GUIDE=3,MAP=4,CLEAR=5,FAIL=6;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SharedPreferences prefs;
    private final ArrayList<Platform> roofs=new ArrayList<>();
    private final ArrayList<Enemy> enemies=new ArrayList<>();
    private final ArrayList<Shot> shots=new ArrayList<>();
    private final ArrayList<Token> tokens=new ArrayList<>();
    private final ArrayList<Flag> flags=new ArrayList<>();
    private int screen=MENU,guideBack=MENU,mission,unlocked,kills,lives=3,tokensTotal,tokensThisRun,combo;
    private int leftPid=-1,rightPid=-1,jumpPid=-1,dashPid=-1,shootPid=-1,facing=1;
    private float x=100,y=543,vx,vy,cam,clock,invuln,fireWait,dashWait,comboTime,noticeTime,shake;
    private boolean left,right,jumpHeld,shootHeld,grounded,doubleJump=true,jumpQueued,dashQueued;
    private int worldW=4000;
    private float checkpointX=100,checkpointY=543;
    private long lastFrame;
    private String notice="";

    private static final class Platform { final float x,y,w; Platform(float a,float b,float c){x=a;y=b;w=c;} }
    private static final class Enemy { float x,y,home,v,hp=2; boolean alive=true; Enemy(float a,float b,float c,float d){x=a;y=b;home=c;v=d;} }
    private static final class Shot { float x,y,v,life=1.15f; Shot(float a,float b,float c){x=a;y=b;v=c;} }
    private static final class Token { final float x,y; boolean taken; Token(float a,float b){x=a;y=b;} }
    private static final class Flag { final float x,y; boolean active; Flag(float a,float b){x=a;y=b;} }

    public GameView(Context context){super(context);setFocusable(true);prefs=context.getSharedPreferences("rooftop_progress",Context.MODE_PRIVATE);unlocked=prefs.getInt("unlocked",0);tokensTotal=prefs.getInt("tokens",0);}
    public void pauseGame(){if(screen==PLAY)screen=PAUSE;}
    public void resumeGame(){lastFrame=SystemClock.uptimeMillis();invalidate();}
    public void backAction(){screen=screen==PLAY?PAUSE:MENU;invalidate();}

    @Override protected void onDraw(Canvas c){super.onDraw(c);float sx=getWidth()/W,sy=getHeight()/H;c.save();c.scale(sx,sy);long now=SystemClock.uptimeMillis();float dt=lastFrame==0?.016f:Math.min(.035f,(now-lastFrame)/1000f);lastFrame=now;clock+=dt;if(screen==PLAY)update(dt);drawSky(c);if(screen==MENU)drawMenu(c);else if(screen==MAP)drawMap(c);else if(screen==GUIDE)drawGuide(c);else{drawWorld(c);drawHud(c);if(screen==PAUSE)drawPause(c);if(screen==CLEAR)drawClear(c);if(screen==FAIL)drawFail(c);}c.restore();postInvalidateDelayed(16);}

    private void start(int n){mission=Math.max(0,Math.min(11,n));screen=PLAY;kills=0;lives=3;tokensThisRun=0;combo=0;comboTime=0;x=100;y=543;checkpointX=x;checkpointY=y;vx=vy=cam=0;grounded=false;doubleJump=true;shots.clear();roofs.clear();enemies.clear();tokens.clear();flags.clear();Random r=new Random(8107+mission*313);worldW=3900+mission*145;float at=0,top=585;roofs.add(new Platform(-100,top,560));int idx=0;while(at<worldW-400){at+=75+r.nextInt(95);top=Math.max(380,Math.min(585,top+r.nextInt(111)-55));float width=185+r.nextInt(110);roofs.add(new Platform(at,top,width));if(at>520&&r.nextFloat()<.75f)enemies.add(new Enemy(at+width*.55f,top-45,at+width*.55f,(r.nextBoolean()?1:-1)*(65+mission*3)));if(idx%3==1)tokens.add(new Token(at+width*.5f,top-70));if(idx>0&&idx%5==0)flags.add(new Flag(at+width*.5f,top-43));at+=width;idx++;}roofs.add(new Platform(worldW-420,560,520));show(String.format(Locale.US,"MISSION %02d  ·  %s",mission+1,name(mission)),2.2f);}
    private String name(int i){String[] a={"First Contact","Awning Alley","Fire Escape","Neon Crossing","Cable Run","Rain Check","The Long Drop","Billboard Run","Sky Garden","Cloudline","Last Delivery","Sunrise Extraction"};return a[i];}
    private int targets(){return Math.min(3,1+mission/4);}
    private int accent(){return mission/4==0?Color.rgb(255,189,99):mission/4==1?Color.rgb(99,229,204):Color.rgb(173,145,255);}

    private void update(float dt){if(noticeTime>0)noticeTime-=dt;if(fireWait>0)fireWait-=dt;if(dashWait>0)dashWait-=dt;if(comboTime>0)comboTime-=dt;else combo=0;shake=Math.max(0,shake-dt*2.2f);
        float d=(right?1:0)-(left?1:0);if(d!=0)facing=d>0?1:-1;vx=approach(vx,d*335,2200*dt);
        if(jumpQueued){if(grounded){vy=-690;grounded=false;doubleJump=true;}else if(mission>=2&&doubleJump){vy=-610;doubleJump=false;show("AIR HOP",.6f);}}jumpQueued=false;
        if(dashQueued&&dashWait<=0&&mission>=4){vx=facing*760;vy=-85;dashWait=.68f;invuln=Math.max(invuln,.24f);shake=.16f;}dashQueued=false;
        if(shootHeld&&fireWait<=0)fire();
        vy=Math.min(1050,vy+GRAVITY*dt);float oldFoot=y+42;x+=vx*dt;y+=vy*dt;grounded=false;
        for(Platform f:roofs)if(x+18>f.x&&x-18<f.x+f.w&&oldFoot<=f.y+3&&y+42>=f.y&&vy>=0){y=f.y-42;vy=0;grounded=true;doubleJump=true;}
        x=Math.max(20,Math.min(worldW-60,x));cam=Math.max(0,Math.min(worldW-W,x-390));
        for(Flag f:flags)if(!f.active&&Math.abs(x-f.x)<35&&Math.abs(y-f.y)<56){f.active=true;checkpointX=f.x;checkpointY=f.y;show("CHECKPOINT SAVED",1.2f);haptic(HapticFeedbackConstants.CONFIRM);}
        for(Token t:tokens)if(!t.taken&&Math.abs(x-t.x)<30&&Math.abs(y-t.y)<48){t.taken=true;tokensTotal++;tokensThisRun++;prefs.edit().putInt("tokens",tokensTotal).apply();show("ROOFTOP TOKEN  +1",.7f);}
        for(Enemy e:enemies)if(e.alive){e.x+=e.v*dt;if(e.x<e.home-92||e.x>e.home+92)e.v=-e.v;if(invuln<=0&&Math.abs(x-e.x)<34&&Math.abs(y-e.y)<52)damage();}
        if(invuln>0)invuln-=dt;
        for(int i=shots.size()-1;i>=0;i--){Shot s=shots.get(i);s.x+=s.v*dt;s.life-=dt;boolean hit=false;for(Enemy e:enemies)if(e.alive&&Math.abs(s.x-e.x)<27&&Math.abs(s.y-(e.y-20))<43){e.hp--;hit=true;if(e.hp<=0){e.alive=false;kills++;combo=comboTime>0?combo+1:1;comboTime=2.2f;show(combo>1?"SENTRY CLEARED  ·  x"+combo:"SENTRY CLEARED",.85f);haptic(HapticFeedbackConstants.KEYBOARD_TAP);}break;}if(hit||s.life<=0)shots.remove(i);}
        if(y>820){damage();return;}if(x>worldW-175){if(kills>=targets()){screen=CLEAR;unlocked=Math.max(unlocked,Math.min(11,mission+1));prefs.edit().putInt("unlocked",unlocked).putInt("tokens",tokensTotal).apply();}else if(noticeTime<=0)show("BEACON LOCKED  ·  TARGETS "+kills+" / "+targets(),1.1f);}
    }
    private float approach(float a,float b,float n){return a<b?Math.min(b,a+n):Math.max(b,a-n);}
    private void fire(){Enemy best=null;float score=Float.MAX_VALUE;for(Enemy e:enemies)if(e.alive){float dx=(e.x-x)*facing;if(dx>0){float q=dx+Math.abs(e.y-y)*1.35f;if(q<score&&q<760){score=q;best=e;}}}shots.add(new Shot(x+facing*25,best==null?y-24:best.y-20,facing*930));fireWait=.19f;}
    private void damage(){if(invuln>0||screen!=PLAY)return;lives--;combo=0;comboTime=0;shake=.5f;vx=vy=0;shots.clear();releaseAll();dashWait=.2f;if(lives<=0){screen=FAIL;haptic(HapticFeedbackConstants.LONG_PRESS);return;}x=checkpointX;y=checkpointY;cam=Math.max(0,Math.min(worldW-W,x-390));grounded=false;doubleJump=true;invuln=1.7f;show("RESPAWNED AT CHECKPOINT  ·  "+lives+" LIVES",1.5f);haptic(HapticFeedbackConstants.LONG_PRESS);}
    private void show(String s,float seconds){notice=s;noticeTime=seconds;}
    private void haptic(int type){try{performHapticFeedback(type);}catch(Exception ignored){}}

    private void drawSky(Canvas c){c.drawColor(Color.rgb(9,15,29));for(int layer=0;layer<3;layer++){float off=(cam*(.10f+layer*.13f))%240;for(int i=-1;i<8;i++){float bx=i*240-off,h=150+Math.floorMod(i*67+layer*49+700,220);p.setColor(layer==0?Color.rgb(18,30,51):layer==1?Color.rgb(23,39,62):Color.rgb(29,47,71));c.drawRect(bx,520-h,bx+178,620,p);p.setColor(0x66FFD28A);for(int r=0;r<4;r++)for(int col=0;col<4;col++)c.drawRoundRect(bx+17+col*37,530-h+r*31,bx+27+col*37,544-h+r*31,2,2,p);}}p.setColor(0x332C9EBA);c.drawRect(0,585,W,H,p);}
    private void drawWorld(Canvas c){c.save();float jx=shake>0?(float)Math.sin(clock*76)*shake*8:0,jy=shake>0?(float)Math.cos(clock*61)*shake*5:0;c.translate(-cam+jx,jy);int color=accent();for(Platform f:roofs){p.setColor(Color.rgb(30,48,73));c.drawRoundRect(f.x,f.y,f.x+f.w,f.y+54,9,9,p);p.setColor(color);c.drawRoundRect(f.x,f.y,f.x+f.w,f.y+8,6,6,p);p.setColor(0x1FFFFFFF);for(float z=f.x+20;z<f.x+f.w-10;z+=44)c.drawRect(z,f.y+20,z+22,f.y+22,p);}for(Flag f:flags)drawFlag(c,f);for(Token t:tokens)if(!t.taken)drawToken(c,t);for(Enemy e:enemies)if(e.alive)drawEnemy(c,e);for(Shot s:shots){p.setColor(0x55FFD45A);c.drawCircle(s.x,s.y,10,p);p.setColor(Color.rgb(255,232,151));c.drawRoundRect(s.x-14,s.y-4,s.x+14,s.y+4,4,4,p);}drawPlayer(c,x,y);drawExit(c,worldW-120,510,color);c.restore();if(shake>0.35f){p.setColor(Color.argb(24,255,84,83));c.drawRect(0,0,W,H,p);}}
    private void drawFlag(Canvas c,Flag f){p.setColor(f.active?0xFF63E5CC:0xFFA9B9CB);c.drawRoundRect(f.x-3,f.y-34,f.x+3,f.y+37,3,3,p);p.setColor(f.active?0xFF63E5CC:0xFFFFBD63);Path q=new Path();q.moveTo(f.x+3,f.y-33);q.lineTo(f.x+34,f.y-24);q.lineTo(f.x+3,f.y-14);q.close();c.drawPath(q,p);}
    private void drawToken(Canvas c,Token t){float bob=(float)Math.sin(clock*4+t.x)*4;p.setColor(0x66FFDB72);c.drawCircle(t.x,t.y+bob,17,p);p.setColor(0xFFFFD66E);Path q=new Path();q.moveTo(t.x,t.y-13+bob);q.lineTo(t.x+10,t.y+bob);q.lineTo(t.x,t.y+13+bob);q.lineTo(t.x-10,t.y+bob);q.close();c.drawPath(q,p);}
    private void drawEnemy(Canvas c,Enemy e){float a=e.x,b=e.y;p.setColor(Color.rgb(189,83,105));c.drawRoundRect(a-18,b-20,a+18,b+28,10,10,p);p.setColor(Color.rgb(232,174,144));c.drawCircle(a,b-32,14,p);p.setColor(Color.rgb(36,29,44));c.drawRect(a-12,b-39,a+13,b-34,p);p.setColor(Color.rgb(255,210,120));c.drawCircle(a+5,b-32,2,p);p.setColor(Color.rgb(255,108,124));c.drawRoundRect(a-24,b-60,a+24,b-52,4,4,p);p.setColor(Color.rgb(104,232,191));c.drawRoundRect(a-22,b-59,a-22+44*e.hp/2,b-53,3,3,p);}
    private void drawPlayer(Canvas c,float a,float b){c.save();c.translate(a,b);if(facing<0)c.scale(-1,1);if(invuln>0&&((int)(clock*18)%2==0)){c.restore();return;}float stride=grounded?(float)Math.sin(clock*14)*10:0;p.setColor(0xB3E99B52);Path cape=new Path();cape.moveTo(-7,-27);cape.quadTo(-40,-7,-30,18);cape.lineTo(-5,6);cape.close();c.drawPath(cape,p);p.setColor(Color.rgb(31,42,60));c.drawRoundRect(-14,12,-3,37+stride,5,5,p);c.drawRoundRect(3,12,14,37-stride,5,5,p);p.setColor(Color.rgb(18,24,36));c.drawRoundRect(-18,34+stride,-1,42+stride,4,4,p);c.drawRoundRect(2,34-stride,19,42-stride,4,4,p);p.setColor(Color.rgb(48,104,150));c.drawRoundRect(-17,-28,17,17,9,9,p);p.setColor(Color.rgb(227,177,145));c.drawCircle(0,-39,14,p);p.setColor(Color.rgb(35,34,43));c.drawArc(-15,-54,14,-37,180,180,true,p);p.setColor(Color.rgb(227,177,145));c.drawRoundRect(10,-24,24,-13,5,5,p);p.setColor(Color.rgb(31,45,64));c.drawRoundRect(12,-19,38,-11,4,4,p);p.setColor(Color.rgb(255,218,144));c.drawCircle(39,-15,3,p);p.setColor(Color.rgb(227,177,145));c.drawRoundRect(-24,-22,-15,5,4,4,p);c.restore();}
    private void drawExit(Canvas c,float a,float b,int color){p.setColor(Color.argb(50,Color.red(color),Color.green(color),Color.blue(color)));c.drawCircle(a,b,45+(float)Math.sin(clock*3)*4,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(7);p.setColor(color);c.drawCircle(a,b,26,p);p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);c.drawCircle(a,b,6,p);}
    private void drawHud(Canvas c){panel(c,18,15,475,82,0xCC101B30);text(c,String.format(Locale.US,"DISTRICT 0%d   ·   MISSION %02d",mission/4+1,mission+1),18,Color.WHITE,34,45);text(c,String.format(Locale.US,"TARGETS %d/%d   ·   LIVES %d   ·   TOKENS %d",kills,targets(),lives,tokensThisRun),15,0xFFB9C9DD,34,75);button(c,1090,16,142,58,"Ⅱ  PAUSE",0xCC253B58);if(noticeTime>0){panel(c,280,106,720,54,0xDD17263D);text(c,notice,18,0xFFFFD37B,305,140);}if(state==PLAY)drawControls(c);}
    private void drawControls(Canvas c){button(c,68,610,82,68,"◀",0xDD253C5A);button(c,160,610,82,68,"▶",0xDD253C5A);button(c,972,602,104,72,mission>=2?"HOP":"JUMP",0xDD344D70);button(c,1086,520,104,70,"DASH",0xDD344D70);button(c,1098,608,128,72,"SHOOT",0xFFE4A553);}
    private void drawMenu(Canvas c){shade(c);text(c,"ROOFTOP RUN",57,0xFFFFBF68,110,178);text(c,"CITY ABOVE THE STREETS",20,0xFF99B1CC,115,221);text(c,"A 2.5D PLATFORM SHOOTER",17,0xFFB8C7D9,115,252);button(c,100,307,315,68,"START MISSION",0xFFFFBD63);button(c,100,391,315,60,"MISSION BOARD",0xDD253C5A);button(c,100,465,315,60,"FIELD GUIDE",0xDD253C5A);drawCity(c);drawPlayer(c,820,512);}
    private void drawMap(Canvas c){shade(c);text(c,"MISSION BOARD",48,0xFFFFBF68,82,104);for(int i=0;i<12;i++){int col=i%4,row=i/4;float bx=82+col*286,by=142+row*140;boolean open=i<=unlocked;panel(c,bx,by,250,108,open?0xDD243A57:0xAA182235);text(c,open?String.format(Locale.US,"MISSION %02d",i+1):"LOCKED",16,open?0xFFFFBD63:0xFF718096,bx+17,by+37);text(c,name(i),17,Color.WHITE,bx+17,by+72);}button(c,34,614,155,62,"BACK",0xDD253C5A);}
    private void drawGuide(Canvas c){shade(c);text(c,"FIELD GUIDE",48,0xFFFFBF68,90,118);String[] a={"MOVE   Use the two left buttons to cross rooftops.","JUMP   Tap before a ledge. Hold briefly for height.","AIR HOP  Unlocks early. Tap jump again while airborne.","DASH   Tap to clear a gap or dodge a sentry.","SHOOT  Hold SHOOT. Aim assist selects the nearest threat ahead.","CHECKPOINTS  Teal flags save your respawn spot.","MISSION  Clear marked sentries, then reach the glowing exit."};for(int i=0;i<a.length;i++)text(c,a[i],18,0xFFE0E8F1,100,184+i*57);button(c,32,614,190,62,"GOT IT",0xFFFFBD63);}
    private void drawPause(Canvas c){shade(c);text(c,"PAUSED",52,0xFFFFBF68,100,218);button(c,95,283,300,65,"RESUME",0xFFFFBD63);button(c,95,362,300,58,"FIELD GUIDE",0xDD253C5A);button(c,95,434,300,58,"MISSION BOARD",0xDD253C5A);button(c,95,506,300,62,"MAIN MENU",0xDD253C5A);}
    private void drawClear(Canvas c){shade(c);text(c,"MISSION COMPLETE",45,0xFF63E5CC,94,215);text(c,String.format(Locale.US,"Targets cleared: %d / %d",kills,targets()),21,Color.WHITE,100,268);text(c,"Tokens found: "+tokensThisRun,18,0xFFFFD66E,100,299);button(c,95,326,310,67,mission<11?"NEXT MISSION":"REPLAY FINAL",0xFFFFBD63);button(c,95,407,310,58,"MISSION BOARD",0xDD253C5A);button(c,95,479,310,60,"MAIN MENU",0xDD253C5A);}
    private void drawFail(Canvas c){shade(c);text(c,"ROUTE INTERRUPTED",42,0xFFFF8389,95,218);text(c,"Checkpoint reached. Ready for another run?",20,0xFFE0E8F1,100,270);button(c,95,330,310,65,"RETRY MISSION",0xFFFFBD63);button(c,95,409,310,58,"MISSION BOARD",0xDD253C5A);button(c,95,481,310,60,"MAIN MENU",0xDD253C5A);}
    private void drawCity(Canvas c){for(int i=0;i<5;i++){float bx=590+i*124,h=120+(i*61)%200;p.setColor(i%2==0?0xFF1A2B45:0xFF223753);c.drawRect(bx,560-h,bx+92,560,p);p.setColor(0x66FFCA79);for(int r=0;r<3;r++)for(int j=0;j<2;j++)c.drawRect(bx+16+j*38,574-h+r*32,bx+26+j*38,586-h+r*32,p);}}
    private void shade(Canvas c){p.setColor(0xCC07101F);c.drawRect(0,0,W,H,p);}
    private void button(Canvas c,float x,float y,float w,float h,String s,int color){p.setColor(0x55000000);c.drawRoundRect(x,y+4,x+w,y+h+4,16,16,p);p.setColor(color);c.drawRoundRect(x,y,x+w,y+h,16,16,p);p.setColor(color==0xFFFFBD63?0xFF172135:Color.WHITE);p.setTextSize(s.length()>14?17:20);p.setTypeface(android.graphics.Typeface.create("sans-serif-medium",1));c.drawText(s,x+(w-p.measureText(s))/2,y+h/2+7,p);}
    private void panel(Canvas c,float x,float y,float w,float h,int color){p.setColor(color);c.drawRoundRect(x,y,x+w,y+h,15,15,p);}
    private void text(Canvas c,String s,float size,int color,float x,float y){p.setColor(color);p.setTextSize(size);p.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));c.drawText(s,x,y,p);}
    private void guide(){guideBack=screen;screen=GUIDE;}

    @Override public boolean onTouchEvent(MotionEvent e){int a=e.getActionMasked();if(a==MotionEvent.ACTION_CANCEL){releaseAll();invalidate();return true;}int i=e.getActionIndex(),id=e.getPointerId(i);float tx=e.getX(i)*W/getWidth(),ty=e.getY(i)*H/getHeight();if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_POINTER_DOWN){if(screen==MENU){if(hit(tx,ty,90,296,340,82))start(Math.min(unlocked,11));else if(hit(tx,ty,90,380,340,80))screen=MAP;else if(hit(tx,ty,90,454,340,80))guide();}else if(screen==MAP){if(hit(tx,ty,20,590,210,120))screen=MENU;else{int col=(int)((tx-70)/286),row=(int)((ty-128)/140),n=row*4+col;if(col>=0&&col<4&&row>=0&&row<3&&n<=unlocked)start(n);}}else if(screen==GUIDE){if(hit(tx,ty,20,590,220,120))screen=guideBack;}else if(screen==PLAY){if(hit(tx,ty,1080,0,200,100))screen=PAUSE;else if(ty>590&&tx<260){if(tx<153){leftId=id;left=true;}else{rightId=id;right=true;}}else if(tx>1080&&ty>=510&&ty<600)dashId=id;else if(tx>1090&&ty>=600){shootId=id;shootHeld=true;}else if(tx>960&&tx<=1080&&ty>585){jumpId=id;jumpHeld=true;jumpQueued=true;}}else if(screen==PAUSE){if(hit(tx,ty,80,274,340,82))screen=PLAY;else if(hit(tx,ty,80,352,340,75))guide();else if(hit(tx,ty,80,425,340,75))screen=MAP;else if(hit(tx,ty,80,498,340,80))screen=MENU;}else if(screen==CLEAR){if(hit(tx,ty,80,317,340,82)){if(mission<11)start(mission+1);else start(11);}else if(hit(tx,ty,80,395,340,78))screen=MAP;else if(hit(tx,ty,80,468,340,80))screen=MENU;}else if(screen==FAIL){if(hit(tx,ty,80,322,340,80))start(mission);else if(hit(tx,ty,80,399,340,80))screen=MAP;else if(hit(tx,ty,80,470,340,82))screen=MENU;}}
        if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_POINTER_UP)release(id);if(a==MotionEvent.ACTION_MOVE){for(int j=0;j<e.getPointerCount();j++){int pid=e.getPointerId(j);float mx=e.getX(j)*W/getWidth();if(pid==leftId&&mx>260)release(pid);if(pid==rightId&&mx<65)release(pid);}}invalidate();return true;}
    private void release(int id){if(id==leftId){leftId=-1;left=false;}if(id==rightId){rightId=-1;right=false;}if(id==jumpId){jumpId=-1;jumpHeld=false;}if(id==dashId){dashId=-1;dashQueued=true;}if(id==shootId){shootId=-1;shootHeld=false;}}
    private void releaseAll(){leftId=rightId=jumpId=dashId=shootId=-1;left=right=jumpHeld=shootHeld=false;}
    private boolean hit(float a,float b,float x,float y,float w,float h){return a>=x&&a<=x+w&&b>=y&&b<=y+h;}
}
