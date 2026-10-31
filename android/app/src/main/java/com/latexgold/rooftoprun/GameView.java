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
    private int leftId=-1,rightId=-1,jumpId=-1,dashId=-1,shootId=-1,facing=1;
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

    public GameView(Context context){super(context);setFocusable(true);prefs=context.getSharedPreferences("rooftop_progress",Context.MODE_PRIVATE);unlocked=prefs.getInt("unlocked",0);tokensTotal=prefs.getInt("tokens",0);makeWorld();}
    public void pauseGame(){if(screen==PLAY)screen=PAUSE;}
    public void resumeGame(){lastFrame=SystemClock.uptimeMillis();invalidate();}
    public void backAction(){screen=screen==PLAY?PAUSE:MENU;invalidate();}

    @Override protected void onDraw(Canvas c){super.onDraw(c);float sx=getWidth()/W,sy=getHeight()/H;c.save();c.scale(sx,sy);long now=SystemClock.uptimeMillis();float dt=lastFrame==0?.016f:Math.min(0.033f,(now-lastFrame)/1000f);lastFrame=now;clock+=dt;if(screen==PLAY)update(dt);if(screen==MENU||screen==PAUSE||screen==GUIDE||screen==MAP||screen==CLEAR||screen==FAIL){drawScene(c);}else drawScene(c);c.restore();if(screen==PLAY)invalidate();}

    private void start(int n){mission=Math.max(0,Math.min(11,n));screen=PLAY;kills=0;lives=3;tokensThisRun=0;combo=0;comboTime=0;x=100;y=543;checkpointX=x;checkpointY=y;vx=vy=cam=0;grounded=false;fireWait=0;dashWait=0;invuln=0;show("MISSION START",1f);}
    private String name(int i){String[] a={"First Contact","Awning Alley","Fire Escape","Neon Crossing","Cable Run","Rain Check","The Long Drop","Billboard Run","Sky Garden","Cloudline","Last Delivery","Highline"};return a[i];}
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
        for(Token t:tokens)if(!t.taken&&Math.abs(x-t.x)<30&&Math.abs(y-t.y)<48){t.taken=true;tokensTotal++;tokensThisRun++;prefs.edit().putInt("tokens",tokensTotal).apply();show("ROOFTOP TOKEN +1",.8f);}
        for(Enemy e:enemies)if(e.alive){e.x+=e.v*dt;if(e.x<e.home-92||e.x>e.home+92)e.v=-e.v;if(invuln<=0&&Math.abs(x-e.x)<34&&Math.abs(y-e.y)<52)damage();}
        if(invuln>0)invuln-=dt;
        for(int i=shots.size()-1;i>=0;i--){Shot s=shots.get(i);s.x+=s.v*dt;s.life-=dt;boolean hit=false;for(Enemy e:enemies)if(e.alive&&Math.abs(s.x-e.x)<27&&Math.abs(s.y-(e.y-20))<43){e.hp--;hit=true;combo++;comboTime=1.25f;show("TARGET HIT",.25f);if(e.hp<=0){e.alive=false;kills++;show("TARGET DOWN",.42f);if(kills>=targets()){screen=CLEAR;unlocked=Math.max(unlocked,Math.min(11,mission+1));prefs.edit().putInt("unlocked",unlocked).apply();}}break;}if(hit||s.life<=0)shots.remove(i);}
        if(y>820){damage();return;}if(x>worldW-175){if(kills>=targets()){screen=CLEAR;unlocked=Math.max(unlocked,Math.min(11,mission+1));prefs.edit().putInt("unlocked",unlocked).putInt("tokens",tokensTotal).apply();show("MISSION CLEAR",1.2f);} else {show("KEEP CLEARING",.8f);x=worldW-175;}}}
    private float approach(float a,float b,float n){return a<b?Math.min(b,a+n):Math.max(b,a-n);}
    private void fire(){Enemy best=null;float score=Float.MAX_VALUE;for(Enemy e:enemies)if(e.alive){float dx=(e.x-x)*facing;if(dx>0){float q=dx+Math.abs(e.y-y)*1.35f;if(q<score&&q<760){score=q;best=e;}}}if(best!=null){float angle=(best.y-y)/(best.x-x);shots.add(new Shot(x+facing*20,y-12,facing*620));fireWait=.18f;}}
    private void damage(){if(invuln>0||screen!=PLAY)return;lives--;combo=0;comboTime=0;shake=.5f;vx=vy=0;shots.clear();releaseAll();dashWait=.2f;if(lives<=0){screen=FAIL;haptic(HapticFeedbackConstants.REJECT);show("RUN FAILED",1.2f);if(checkpointX>20){x=checkpointX;y=checkpointY;screen=PLAY;}} else {x=checkpointX;y=checkpointY;invuln=1.4f;show("CHECKPOINT",.8f);}}
    private void show(String s,float seconds){notice=s;noticeTime=seconds;}
    private void haptic(int type){try{performHapticFeedback(type);}catch(Exception ignored){}}

    private void drawSky(Canvas c){c.drawColor(Color.rgb(9,15,29));for(int layer=0;layer<3;layer++){float off=(cam*(.10f+layer*.13f))%240;for(int i=-1;i<8;i++){float bx=i*240-off,h=150+Math.floorMod(i+layer,3)*40f;p.setColor(layer==0?0x3350A8E8:layer==1?0x223B7CFF:0x1A4B6F9A);c.drawRect(bx,560-h,bx+215,560,p);}}}
    private void drawWorld(Canvas c){c.save();float jx=shake>0?(float)Math.sin(clock*76)*shake*8:0,jy=shake>0?(float)Math.cos(clock*61)*shake*5:0;c.translate(-cam+jx,jy);int color=accent();for(Platform f:roofs){p.setColor(0xFF233B57);c.drawRect(f.x,f.y,f.x+f.w,f.y+12,p);p.setColor(color);c.drawRect(f.x+2,f.y+2,f.x+f.w-2,f.y+8,p);}for(Flag f:flags){drawFlag(c,f);}for(Token t:tokens){drawToken(c,t);}for(Enemy e:enemies){drawEnemy(c,e);}drawPlayer(c,x,y);drawExit(c,worldW-110,530,color);c.restore();drawHud(c);}
    private void drawFlag(Canvas c,Flag f){p.setColor(f.active?0xFF63E5CC:0xFFA9B9CB);c.drawRoundRect(f.x-3,f.y-34,f.x+3,f.y+37,3,3,p);p.setColor(f.active?0xFF63E5CC:0xFFFFBD63);Path q=new Path();q.moveTo(f.x+8,f.y-30);q.lineTo(f.x+8,f.y+30);q.lineTo(f.x+30,f.y+12);q.close();c.drawPath(q,p);}
    private void drawToken(Canvas c,Token t){float bob=(float)Math.sin(clock*4+t.x)*4;p.setColor(0x66FFDB72);c.drawCircle(t.x,t.y+bob,17,p);p.setColor(0xFFFFD66E);Path q=new Path();q.moveTo(t.x,t.y-12+bob);q.lineTo(t.x+10,t.y+bob);q.lineTo(t.x,t.y+12+bob);q.lineTo(t.x-10,t.y+bob);q.close();c.drawPath(q,p);}
    private void drawEnemy(Canvas c,Enemy e){float a=e.x,b=e.y;p.setColor(Color.rgb(189,83,105));c.drawRoundRect(a-18,b-20,a+18,b+28,10,10,p);p.setColor(Color.rgb(232,174,144));c.drawCircle(a,b-32,12,p);p.setColor(Color.rgb(46,42,58));c.drawRect(a-16,b+12,a+16,b+18,p);}
    private void drawPlayer(Canvas c,float a,float b){c.save();c.translate(a,b);if(facing<0)c.scale(-1,1);if(invuln>0&&((int)(clock*18)%2==0)){c.restore();return;}float stride=grounded?(float)Math.sin(clock*11)*2:0;Path q=new Path();p.setColor(0xFFEAEEF7);c.drawRoundRect(-18,-42,18,18,10,10,p);p.setColor(0xFF3D5AFB);c.drawRoundRect(-12,-20,12,8,8,8,p);p.setColor(0xFFFFBD63);c.drawRect(-10,-8,10,10,p);p.setColor(0xFF2C3347);c.drawRect(-14,12, -6, 32, p); c.drawRect(6,12,14,32,p);p.setColor(0xFF6A82FF);c.drawRect(-2, -42,-10, -60,p);c.drawRect(2,-42,10,-60,p);c.restore();}
    private void drawExit(Canvas c,float a,float b,int color){p.setColor(Color.argb(50,Color.red(color),Color.green(color),Color.blue(color)));c.drawCircle(a,b,45+(float)Math.sin(clock*3)*4,p);p.setColor(color);c.drawCircle(a,b,18,p);}
    private void drawHud(Canvas c){panel(c,18,15,475,82,0xCC101B30);text(c,String.format(Locale.US,"DISTRICT %02d   ·   MISSION %02d",mission/4+1,mission+1),18,Color.WHITE,34,45);text(c,String.format(Locale.US,"LIVES %d   ·   TOKENS %d   ·   KILLS %d/%d",lives,tokensTotal,kills,targets()),18,0xFFB9CBE4,34,72);if(noticeTime>0)text(c,notice,24,0xFFFFBD63,650,52);}
    private void drawControls(Canvas c){button(c,68,610,82,68,"◀",0xDD253C5A);button(c,160,610,82,68,"▶",0xDD253C5A);button(c,972,602,104,72,mission>=2?"HOP":"JUMP",0xDD344D70);button(c,1086,602,104,72,"DASH",0xDD344D70);button(c,1198,602,104,72,"FIRE",0xDD344D70);}
    private void drawScene(Canvas c){drawSky(c);drawWorld(c);if(screen==MENU||screen==PAUSE||screen==GUIDE||screen==MAP||screen==CLEAR||screen==FAIL){drawControls(c);} }
    private void drawMenu(Canvas c){shade(c);text(c,"ROOFTOP RUN",57,0xFFFFBF68,110,178);text(c,"CITY ABOVE THE STREETS",20,0xFF99B1CC,115,221);text(c,"A 2.5D PLATFORM SHOOTER",17,0xFFB8C7D9,115,258);button(c,100,332,260,74,"START",0xFFFFBD63);button(c,100,423,260,64,"MISSIONS",0xDD253C5A);button(c,100,500,260,64,"FIELD GUIDE",0xDD253C5A);}
    private void drawMap(Canvas c){shade(c);text(c,"MISSION BOARD",48,0xFFFFBF68,82,104);for(int i=0;i<12;i++){int col=i%4,row=i/4;float bx=82+col*286,by=142+row*140;boolean open=i<=unlocked;panel(c,bx,by,220,104,open?0xCC263C58:0xCC111B2A);text(c,String.format(Locale.US,"%02d",i+1),18,Color.WHITE,bx+18,by+32);text(c,name(i),20,open?0xFFF5C96C:0xFF6F7E94,bx+18,by+62);if(!open)text(c,"LOCKED",16,0xFF72839E,bx+18,by+86);}}
    private void drawGuide(Canvas c){shade(c);text(c,"FIELD GUIDE",48,0xFFFFBF68,90,118);String[] a={"MOVE   Use the two left buttons to cross rooftops.","JUMP   Tap before a ledge. Hold briefly for a higher hop.","DASH   Unlocks after mission 4 and helps dodge danger.","SHOOT   Hold fire to tag the nearest target ahead.","SAVE   Checkpoints mark safe rooftops and keep progress."};for(int i=0;i<a.length;i++)text(c,a[i],18,0xFFE4EDF7,96,170+i*42);button(c,96,600,260,60,"BACK",0xFFFFBD63);}
    private void drawPause(Canvas c){shade(c);text(c,"PAUSED",52,0xFFFFBF68,100,218);button(c,95,283,300,65,"RESUME",0xFFFFBD63);button(c,95,362,300,58,"FIELD GUIDE",0xDD253C5A);button(c,95,434,300,58,"MISSION BOARD",0xDD253C5A);button(c,95,507,300,58,"MAIN MENU",0xDD253C5A);}
    private void drawClear(Canvas c){shade(c);text(c,"MISSION COMPLETE",45,0xFF63E5CC,94,215);text(c,String.format(Locale.US,"Targets cleared: %d / %d",kills,targets()),21,Color.WHITE,100,268);text(c,String.format(Locale.US,"Tokens this run: %d",tokensThisRun),20,0xFFBFEAE0,100,306);button(c,95,370,300,60,"NEXT MISSION",0xFFFFBD63);button(c,95,444,300,58,"MISSION BOARD",0xDD253C5A);}
    private void drawFail(Canvas c){shade(c);text(c,"ROUTE INTERRUPTED",42,0xFFFF8389,95,218);text(c,"Checkpoint reached. Ready for another run?",20,0xFFE0E8F1,100,270);button(c,95,330,310,65,"RETRY",0xFFFFBD63);button(c,95,410,310,58,"MISSION BOARD",0xDD253C5A);button(c,95,482,310,58,"MAIN MENU",0xDD253C5A);}
    private void drawCity(Canvas c){for(int i=0;i<5;i++){float bx=590+i*124,h=120+(i*61)%200;p.setColor(i%2==0?0xFF1A2B45:0xFF223753);c.drawRect(bx,560-h,bx+92,560,p);p.setColor(0x66FFCA79);for(int y=0;y<5;y++)c.drawRect(bx+10,560-h+25+y*20,bx+82,560-h+32+y*20,p);}}
    private void shade(Canvas c){p.setColor(0xCC07101F);c.drawRect(0,0,W,H,p);}
    private void button(Canvas c,float x,float y,float w,float h,String s,int color){p.setColor(0x55000000);c.drawRoundRect(x,y+4,x+w,y+h+4,16,16,p);p.setColor(color);c.drawRoundRect(x,y,x+w,y+h,18,18,p);p.setColor(0xFFECF3FF);p.setTextSize(28);p.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));c.drawText(s,x+(w/2)-p.measureText(s)/2,y+h/2+10,p);}
    private void panel(Canvas c,float x,float y,float w,float h,int color){p.setColor(color);c.drawRoundRect(x,y,x+w,y+h,15,15,p);}
    private void text(Canvas c,String s,float size,int color,float x,float y){p.setColor(color);p.setTextSize(size);p.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));c.drawText(s,x,y,p);}
    private void guide(){guideBack=screen;screen=GUIDE;}

    @Override public boolean onTouchEvent(MotionEvent e){int a=e.getActionMasked();if(a==MotionEvent.ACTION_CANCEL){releaseAll();invalidate();return true;}int i=e.getActionIndex(),id=e.getPointerId(i);float x=e.getX(i),y=e.getY(i);if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_POINTER_DOWN){if(hit(x,y,68,610,82,68)){leftId=id;left=true;}if(hit(x,y,160,610,82,68)){rightId=id;right=true;}if(hit(x,y,972,602,104,72)){jumpId=id;jumpHeld=true;jumpQueued=true;}if(hit(x,y,1086,602,104,72)){dashId=id;dashQueued=true;}if(hit(x,y,1198,602,104,72)){shootId=id;shootHeld=true;}if(screen==MENU&&hit(x,y,100,332,260,74)){start(0);}if(screen==MENU&&hit(x,y,100,423,260,64)){screen=MAP;}if(screen==MENU&&hit(x,y,100,500,260,64)){guide();}if(screen==MAP){for(int m=0;m<12;m++){int col=m%4,row=m/4;float bx=82+col*286,by=142+row*140;if(hit(x,y,bx,by,220,104)&&m<=unlocked){start(m);break;}}}if(screen==GUIDE&&hit(x,y,96,600,260,60)){screen=guideBack;}if(screen==PAUSE){if(hit(x,y,95,283,300,65)){resumeGame();}if(hit(x,y,95,362,300,58)){guide();}if(hit(x,y,95,434,300,58)){screen=MAP;}if(hit(x,y,95,507,300,58)){screen=MENU;}}if(screen==CLEAR){if(hit(x,y,95,370,300,60)){start(Math.min(11,mission+1));}if(hit(x,y,95,444,300,58)){screen=MAP;} }if(screen==FAIL){if(hit(x,y,95,330,310,65)){start(mission);}if(hit(x,y,95,410,310,58)){screen=MAP;}if(hit(x,y,95,482,310,58)){screen=MENU;}}return true;}if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_POINTER_UP){release(id);return true;}if(a==MotionEvent.ACTION_MOVE){for(int j=0;j<e.getPointerCount();j++){int pid=e.getPointerId(j);float mx=e.getX(j),my=e.getY(j);if(pid==leftId){left=hit(mx,my,68,610,82,68);}if(pid==rightId){right=hit(mx,my,160,610,82,68);}if(pid==jumpId){jumpHeld=hit(mx,my,972,602,104,72);}if(pid==shootId){shootHeld=hit(mx,my,1198,602,104,72);}}}invalidate();return true;}
    private void release(int id){if(id==leftId){leftId=-1;left=false;}if(id==rightId){rightId=-1;right=false;}if(id==jumpId){jumpId=-1;jumpHeld=false;}if(id==dashId){dashId=-1;dashQueued=true;}if(id==shootId){shootId=-1;shootHeld=false;}}
    private void releaseAll(){leftId=rightId=jumpId=dashId=shootId=-1;left=right=jumpHeld=shootHeld=false;}
    private boolean hit(float a,float b,float x,float y,float w,float h){return a>=x&&a<=x+w&&b>=y&&b<=y+h;}

    private void makeWorld(){
        roofs.clear();
        roofs.add(new Platform(90f,560f,220f));
        roofs.add(new Platform(360f,492f,210f));
        roofs.add(new Platform(610f,520f,260f));
        roofs.add(new Platform(930f,470f,260f));
        roofs.add(new Platform(1230f,540f,230f));
        roofs.add(new Platform(1480f,460f,210f));
        roofs.add(new Platform(1710f,388f,220f));
        roofs.add(new Platform(1950f,470f,240f));
        roofs.add(new Platform(2230f,430f,190f));
        roofs.add(new Platform(2460f,500f,250f));
        roofs.add(new Platform(2750f,420f,208f));
        roofs.add(new Platform(3000f,520f,320f));
        roofs.add(new Platform(3420f,490f,230f));
        flags.clear();
        flags.add(new Flag(280f,512f));
        flags.add(new Flag(920f,432f));
        flags.add(new Flag(1880f,432f));
        flags.add(new Flag(2820f,382f));
        flags.add(new Flag(3480f,450f));
        tokens.clear();
        tokens.add(new Token(220f,514f));
        tokens.add(new Token(690f,478f));
        tokens.add(new Token(1120f,432f));
        tokens.add(new Token(1605f,340f));
        tokens.add(new Token(2280f,392f));
        tokens.add(new Token(3010f,472f));
        enemies.clear();
        enemies.add(new Enemy(480f,510f,440f,90f));
        enemies.add(new Enemy(1290f,500f,1290f,120f));
        enemies.add(new Enemy(1765f,350f,1765f,85f));
        enemies.add(new Enemy(2560f,470f,2560f,110f));
        enemies.add(new Enemy(3310f,450f,3310f,95f));
    }
}
