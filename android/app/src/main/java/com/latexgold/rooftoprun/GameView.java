package com.latexgold.rooftoprun;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

/** Self-contained, offline 2.5D platform shooter rendered with Android Canvas. */
public final class GameView extends View {
    private static final int MENU=0, PLAY=1, PAUSE=2, HELP=3, MAP=4, CLEAR=5, FAILED=6;
    private static final float W=1280f,H=720f, GRAVITY=1900f;
    private final Paint p=new Paint(3);
    private final SharedPreferences save;
    private final Random random=new Random(31);
    private final ArrayList<Platform> platforms=new ArrayList<>();
    private final ArrayList<Enemy> enemies=new ArrayList<>();
    private final ArrayList<Bullet> bullets=new ArrayList<>();
    private final HashSet<Integer> held=new HashSet<>();
    private int screen=MENU, level=0, unlocked=0, kills=0, totalKills=0, lives=3, missionCoins=0, totalCoins=0;
    private float px=100, py=500, vx=0, vy=0, cameraX=0, time=0, dashTime=0, fireCooldown=0, invuln=0, pulse=0;
    private boolean grounded=false, faceRight=true, jumpLatch=false, guideSeen=false;
    private long lastFrame=0;
    private String banner="";
    private float bannerTime=0;
    private int worldLength=4600;
    private int touchLeft=-1,touchRight=-1,touchJump=-1,touchDash=-1,touchShoot=-1,touchPause=-1;
    private final Paint textPaint=new Paint(3);

    private static final class Platform { float x,y,w,d; Platform(float a,float b,float c,float e){x=a;y=b;w=c;d=e;} }
    private static final class Enemy { float x,y,baseY,vx,hp=2; boolean alive=true; Enemy(float a,float b,float c){x=a;y=b;vx=c;} }
    private static final class Bullet { float x,y,vx,life=1.2f; Bullet(float a,float b,float c){x=a;y=b;vx=c;} }

    public GameView(Context c){super(c);setFocusable(true);save=c.getSharedPreferences("rooftop_save",Context.MODE_PRIVATE);unlocked=save.getInt("unlocked",0);totalCoins=save.getInt("coins",0);textPaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));textPaint.setTextAlign(Paint.Align.LEFT);}
    public void pauseGame(){if(screen==PLAY)screen=PAUSE;}
    public void resumeGame(){lastFrame=SystemClock.uptimeMillis();invalidate();}
    public void backAction(){if(screen==PLAY)screen=PAUSE;else if(screen==PAUSE||screen==HELP||screen==MAP)screen=MENU;else screen=MENU;invalidate();}

    @Override protected void onDraw(Canvas real){super.onDraw(real);float sx=getWidth()/W, sy=getHeight()/H;real.save();real.scale(sx,sy);long now=SystemClock.uptimeMillis();float dt=lastFrame==0?0.016f:Math.min(0.04f,(now-lastFrame)/1000f);lastFrame=now;time+=dt;pulse+=dt; if(screen==PLAY)update(dt);drawBackground(real);if(screen==MENU)drawMenu(real);else if(screen==MAP)drawMap(real);else if(screen==HELP)drawHelp(real);else{drawWorld(real);drawHud(real);if(screen==PAUSE)drawPause(real);if(screen==CLEAR)drawClear(real);if(screen==FAILED)drawFailed(real);}real.restore();postInvalidateDelayed(16);}

    private void update(float dt){if(bannerTime>0)bannerTime-=dt;if(invuln>0)invuln-=dt;if(fireCooldown>0)fireCooldown-=dt;if(dashTime>0)dashTime-=dt;
        float dir=(held.contains(touchLeft)?-1:0)+(held.contains(touchRight)?1:0);if(dir==0){if(touchLeft==-2)dir-=1;if(touchRight==-2)dir+=1;}
        vx=move(vx,dir*330,2100*dt);if(dir!=0)faceRight=dir>0;
        boolean jump=held.contains(touchJump)||touchJump==-2;if(jump&&!jumpLatch&&grounded){vy=-690;grounded=false;jumpLatch=true;}if(!jump)jumpLatch=false;
        if((held.contains(touchDash)||touchDash==-2)&&dashTime<=0){dashTime=.2f;vx=faceRight?700:-700;vy=-80;held.remove(touchDash);touchDash=-1;}
        if((held.contains(touchShoot)||touchShoot==-2)&&fireCooldown<=0){shoot();fireCooldown=.19f;}
        if(dashTime<=0)vy=Math.min(1050,vy+GRAVITY*dt);
        float oldBottom=py+45;px+=vx*dt;py+=vy*dt;grounded=false;
        for(Platform pl:platforms){if(px+20>pl.x&&px-20<pl.x+pl.w&&oldBottom<=pl.y+2&&py+45>=pl.y&&vy>=0){py=pl.y-45;vy=0;grounded=true;}}
        px=Math.max(28,Math.min(worldLength-60,px));if(py>820){hurt();}
        cameraX=Math.max(0,Math.min(worldLength-W,px-390));
        for(Enemy e:enemies){if(!e.alive)continue;e.x+=e.vx*dt;if(e.x<e.baseY-110||e.x>e.baseY+110)e.vx=-e.vx;if(Math.abs(px-e.x)<34&&Math.abs(py-e.y)<62&&invuln<=0)hurt();}
        for(int i=bullets.size()-1;i>=0;i--){Bullet b=bullets.get(i);b.x+=b.vx*dt;b.life-=dt;boolean hit=false;for(Enemy e:enemies){if(e.alive&&Math.abs(b.x-e.x)<25&&Math.abs(b.y-e.y)<48){e.hp-=1;hit=true;if(e.hp<=0){e.alive=false;kills++;showBanner("TARGET CLEARED",.9f);}break;}}if(hit||b.life<=0)bullets.remove(i);}
        if(px>worldLength-170){int need=requiredKills();if(kills>=need){screen=CLEAR;unlocked=Math.max(unlocked,Math.min(11,level+1));save.edit().putInt("unlocked",unlocked).putInt("coins",totalCoins).apply();}else if(bannerTime<=0)showBanner("CLEAR THE MARKED TARGETS  %d/%d".replace("%d/%d",kills+"/"+need),1.0f);}
    }
    private float move(float a,float b,float n){return a<b?Math.min(a+n,b):Math.max(a-n,b);}
    private void shoot(){Enemy target=null;float best=Float.MAX_VALUE;for(Enemy e:enemies)if(e.alive){float dx=e.x-px;if((faceRight&&dx>0)||(!faceRight&&dx<0)){float d=Math.abs(dx)+Math.abs(e.y-(py-20))*1.6f;if(d<best&&d<720){best=d;target=e;}}}float aimY=target==null?py-23:target.y-20;bullets.add(new Bullet(px+(faceRight?22:-22),aimY,(faceRight?1:-1)*880));}
    private int requiredKills(){return Math.min(3,1+level/4);}
    private void hurt(){if(invuln>0)return;lives--;invuln=1.2f;vx=faceRight?-260:260;vy=-420;if(lives<=0){screen=FAILED;}else showBanner("HIT!  %d LIVES LEFT".replace("%d",String.valueOf(lives)),1.1f);}
    private void showBanner(String s,float d){banner=s;bannerTime=d;}

    private void buildLevel(int n){level=Math.max(0,Math.min(11,n));screen=PLAY;kills=0;lives=3;missionCoins=0;px=90;py=460;vx=vy=cameraX=0;bullets.clear();platforms.clear();enemies.clear();int district=level/4;worldLength=3800+level*120;random.setSeed(9001+level*173);float x=0,y=585;platforms.add(new Platform(-100,585,540,100));while(x<worldLength-320){float gap=65+random.nextInt(105);float width=180+random.nextInt(135);x+=gap;y=Math.max(390,Math.min(600,y+random.nextInt(131)-65));platforms.add(new Platform(x,y,width,52));if(random.nextFloat()<.62f&&x>480){enemies.add(new Enemy(x+width*.5f,y-46,x+width*.5f, (random.nextBoolean()?1:-1)*(45+level*2)));}x+=width;}platforms.add(new Platform(worldLength-360,560,500,100));for(int i=0;i<3;i++){float cx=550+i*(worldLength-1100)/3f;platforms.add(new Platform(cx,420-i%2*65,100,28));}showBanner("MISSION %02d  ·  %s".replace("%02d",String.format(java.util.Locale.US,"%02d",level+1)).replace("%s",missionTitle()),2f);}
    private String missionTitle(){String[] n={"First Contact","Across the Awnings","Fire Escape","Neon Crossing","Cable Run","Rain Check","The Long Drop","Billboard Route","Garden Gate","Cloudline","Final Delivery","Sunrise Extraction"};return n[level];}
    private String hint(){if(level==0)return "Move right. Jump the gaps. Hold SHOOT to clear the sentry.";if(level<4)return "Use short jumps for tight roofs. Clear sentries to open the exit.";if(level<8)return "DASH crosses wide gaps. Keep moving while you fire.";return "Chain jump + dash, clear marked targets, then reach the beacon.";}

    private void drawBackground(Canvas c){c.drawColor(Color.rgb(10,17,31));for(int layer=0;layer<3;layer++){float speed=.12f+layer*.14f;float offset=(cameraX*speed)%230;for(int i=-1;i<8;i++){float x=i*230-offset;float h=160+((i*71+layer*43+800)%190);p.setColor(layer==0?Color.rgb(20,34,56):layer==1?Color.rgb(24,42,66):Color.rgb(30,48,72));c.drawRect(x,500-h,x+170,610,p);p.setColor(Color.argb(90,255,195,110));for(int r=0;r<4;r++)for(int col=0;col<4;col++)c.drawRect(x+18+col*34,520-h+r*28,x+29+col*34,530-h+r*28,p);}}p.setColor(Color.argb(45,110,202,235));c.drawRect(0,585,W,H,p);}
    private void drawWorld(Canvas c){c.save();c.translate(-cameraX,0);int district=level/4;int accent=district==0?Color.rgb(255,189,99):district==1?Color.rgb(99,229,204):Color.rgb(173,145,255);for(Platform pl:platforms){p.setColor(Color.rgb(37,57,83));c.drawRoundRect(pl.x,pl.y,pl.x+pl.w,pl.y+pl.d,7,7,p);p.setColor(accent);c.drawRoundRect(pl.x,pl.y,pl.x+pl.w,pl.y+8,6,6,p);p.setColor(Color.argb(35,255,255,255));c.drawRect(pl.x+12,pl.y+12,pl.x+pl.w-12,pl.y+15,p);}drawBeacon(c,worldLength-115,490,accent);for(Enemy e:enemies)if(e.alive)drawEnemy(c,e);for(Bullet b:bullets){p.setColor(Color.rgb(255,226,137));c.drawRoundRect(b.x-13,b.y-4,b.x+13,b.y+4,4,4,p);}drawHuman(c,px,py);c.restore();}
    private void drawBeacon(Canvas c,float x,float y,int color){p.setColor(Color.argb(45,color,Color.red(color),Color.blue(color)));c.drawCircle(x,y,54+(float)Math.sin(pulse*3)*4,p);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(7);c.drawCircle(x,y,26,p);p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);c.drawCircle(x,y,7,p);}
    private void drawEnemy(Canvas c,Enemy e){float x=e.x,y=e.y;p.setColor(Color.argb(50,0,0,0));c.drawOval(x-25,y+35,x+25,y+44,p);p.setColor(Color.rgb(199,87,102));c.drawRoundRect(x-17,y-23,x+17,y+28,10,10,p);p.setColor(Color.rgb(237,173,143));c.drawCircle(x,y-34,14,p);p.setColor(Color.rgb(35,27,43));c.drawRect(x-11,y-39,x+11,y-34,p);p.setColor(Color.rgb(255,221,133));c.drawCircle(x+5,y-35,2,p);p.setColor(Color.rgb(80,36,53));c.drawRect(x-13,y+27,x-5,y+39,p);c.drawRect(x+5,y+27,x+13,y+39,p);p.setColor(Color.rgb(255,100,120));c.drawRoundRect(x-22,y-58,x+22,y-51,3,3,p);p.setColor(Color.rgb(90,230,190));c.drawRect(x-20,y-57,x-20+42*e.hp/2,y-52,p);}
    private void drawHuman(Canvas c,float x,float y){c.save();c.translate(x,y);if(!faceRight)c.scale(-1,1);if(invuln>0&&((int)(pulse*16)%2==0)){c.restore();return;}float run=(float)Math.sin(time*13)*12;int skin=Color.rgb(227,177,145),shirt=Color.rgb(46,101,149),pants=Color.rgb(34,43,61),cape=Color.rgb(246,155,78);p.setColor(Color.argb(70,0,0,0));c.drawOval(-21,40,23,48,p);p.setColor(cape);Path cp=new Path();cp.moveTo(-7,-28);cp.quadTo(-41,-7,-31,20);cp.lineTo(-4,5);cp.close();c.drawPath(cp,p);p.setColor(pants);c.drawRoundRect(-14,12,-3,38+run,5,5,p);c.drawRoundRect(3,12,14,38-run,5,5,p);p.setColor(Color.rgb(20,26,38));c.drawRoundRect(-17,34+run,-1,41+run,4,4,p);c.drawRoundRect(2,34-run,18,41-run,4,4,p);p.setColor(shirt);c.drawRoundRect(-17,-28,17,17,9,9,p);p.setColor(skin);c.drawCircle(0,-39,14,p);p.setColor(Color.rgb(32,34,45));c.drawArc(-15,-54,14,-37,180,180,true,p);p.setColor(skin);c.drawRoundRect(11,-24,24,-12,5,5,p);p.setColor(Color.rgb(30,45,64));c.drawRoundRect(12,-18,36,-11,4,4,p);p.setColor(Color.rgb(250,216,143));c.drawCircle(36,-14,3,p);p.setColor(skin);c.drawRoundRect(-24,-22,-15,5,4,4,p);c.restore();}

    private void drawHud(Canvas c){round(c,18,16,398,77,0xCC101B30);txt(c,"DISTRICT 0%d   /   MISSION %02d".replace("0%d","0"+(level/4+1)).replace("%02d",String.format(java.util.Locale.US,"%02d",level+1)),20,Color.WHITE,34,48);txt(c,"TARGETS  %d/%d     LIVES  %d".replace("%d/%d",kills+"/"+requiredKills()).replace("%d",String.valueOf(lives)),17,0xFFB9C9DD,34,72);round(c,1094,16,125,60,0xCC101B30);txt(c,"Ⅱ  PAUSE",18,Color.WHITE,1112,54);if(bannerTime>0){round(c,310,100,660,54,0xDD17263D);txt(c,banner,19,0xFFFFD37B,640-banner.length()*5.1f,134);}if(screen==PLAY)drawControls(c);}
    private void drawControls(Canvas c){button(c,76,610,78,58,"◀",0xDD253C5A);button(c,166,610,78,58,"▶",0xDD253C5A);button(c,1010,605,93,62,"JUMP",0xDD344D70);button(c,1110,535,93,62,"DASH",0xDD344D70);button(c,1110,615,116,68,"SHOOT",0xFFE4A553);}
    private void drawMenu(Canvas c){overlay(c);txt(c,"ROOFTOP RUN",58,0xFFFFBF68,128,185);txt(c,"CITY ABOVE THE STREETS",20,0xFF99B1CC,132,229);txt(c,"A 2.5D PLATFORM SHOOTER",17,0xFFB8C7D9,132,263);button(c,110,330,290,68,"START MISSION",0xFFFFBD63);button(c,110,417,290,58,"MISSION SELECT",0xDD253C5A);button(c,110,493,290,58,"HOW TO PLAY",0xDD253C5A);drawHuman(c,790,512);drawCityDecor(c);}
    private void drawMap(Canvas c){overlay(c);txt(c,"MISSION BOARD",54,0xFFFFBF68,110,110);for(int i=0;i<12;i++){int col=i%4,row=i/4;float x=95+col*280,y=165+row*130;boolean open=i<=unlocked;p.setColor(open?0xDD243A57:0xAA182235);c.drawRoundRect(x,y,x+240,y+100,16,16,p);p.setColor(open?0xFFFFBD63:0xFF64748B);txt(c,open?String.format(java.util.Locale.US,"%02d",i+1):"LOCKED",17,p.getColor(),x+20,y+34);txt(c,missionName(i),16,Color.WHITE,x+20,y+66);}button(c,42,615,150,58,"BACK",0xDD253C5A);}
    private void drawHelp(Canvas c){overlay(c);txt(c,"FIELD GUIDE",48,0xFFFFBF68,110,125);String[] lines={"MOVE: use the two left buttons to cross rooftops.","JUMP: tap JUMP before the edge; hold briefly for height.","DASH: tap DASH to clear a wide gap or dodge.","SHOOT: hold SHOOT; aim assist picks the nearest threat ahead.","MISSION: clear the marked sentries, then reach the glowing beacon.","TIP: the beacon stays closed until the target count is met."};for(int i=0;i<lines.length;i++)txt(c,lines[i],20,0xFFE0E8F1,120,205+i*58);button(c,42,615,180,58,"GOT IT",0xFFFFBD63);}
    private void drawPause(Canvas c){overlay(c);txt(c,"PAUSED",56,0xFFFFBF68,120,220);button(c,110,300,260,65,"RESUME",0xFFFFBD63);button(c,110,380,260,58,"FIELD GUIDE",0xDD253C5A);button(c,110,452,260,58,"MISSION BOARD",0xDD253C5A);button(c,110,524,260,58,"MAIN MENU",0xDD253C5A);}
    private void drawClear(Canvas c){overlay(c);txt(c,"MISSION COMPLETE",47,0xFF63E5CC,110,220);txt(c,"Targets cleared: %d / %d".replace("%d / %d",kills+" / "+requiredKills()),22,Color.WHITE,112,274);button(c,110,340,290,66,level<11?"NEXT MISSION":"REPLAY FINAL MISSION",0xFFFFBD63);button(c,110,420,290,58,"MISSION BOARD",0xDD253C5A);button(c,110,493,290,58,"MAIN MENU",0xDD253C5A);}
    private void drawFailed(Canvas c){overlay(c);txt(c,"ROUTE INTERRUPTED",44,0xFFFF8389,110,230);txt(c,"Catch your breath. The rooftops will wait.",20,0xFFE0E8F1,112,282);button(c,110,345,280,64,"RETRY MISSION",0xFFFFBD63);button(c,110,425,280,58,"MISSION BOARD",0xDD253C5A);button(c,110,497,280,58,"MAIN MENU",0xDD253C5A);}
    private void drawCityDecor(Canvas c){for(int i=0;i<5;i++){float x=600+i*120,h=120+(i*67)%200;p.setColor(i%2==0?0xFF1A2B45:0xFF223753);c.drawRect(x,560-h,x+90,560,p);p.setColor(0x66FFCA79);for(int r=0;r<3;r++)for(int co=0;co<2;co++)c.drawRect(x+16+co*35,580-h+r*32,x+24+co*35,593-h+r*32,p);}}
    private void overlay(Canvas c){p.setColor(0xCC07101F);c.drawRect(0,0,W,H,p);}
    private void button(Canvas c,float x,float y,float w,float h,String text,int color){p.setColor(0x55000000);c.drawRoundRect(x,y+4,x+w,y+h+4,16,16,p);p.setColor(color);c.drawRoundRect(x,y,x+w,y+h,16,16,p);p.setColor(color==0xFFFFBD63?0xFF172135:Color.WHITE);p.setTextSize(text.length()>12?18:21);p.setTypeface(android.graphics.Typeface.create("sans-serif-medium",1));float tw=p.measureText(text);c.drawText(text,x+(w-tw)/2,y+h/2+7,p);}
    private void round(Canvas c,float x,float y,float w,float h,int color){p.setColor(color);c.drawRoundRect(x,y,x+w,y+h,14,14,p);}
    private void txt(Canvas c,String s,float size,int color,float x,float y){p.setColor(color);p.setTextSize(size);p.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));c.drawText(s,x,y,p);}
    private String missionName(int i){String[] n={"First Contact","Awning Alley","Fire Escape","Neon Crossing","Cable Run","Rain Check","Long Drop","Billboard Run","Sky Garden","Cloudline","Last Delivery","Sunrise"};return n[i];}

    @Override public boolean onTouchEvent(MotionEvent e){int action=e.getActionMasked();int index=e.getActionIndex();int id=e.getPointerId(index);float x=e.getX(index)*W/getWidth(),y=e.getY(index)*H/getHeight();if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){if(screen==MENU){if(hit(x,y,110,320,400,85)){buildLevel(Math.min(unlocked,11));}else if(hit(x,y,110,410,400,75))screen=MAP;else if(hit(x,y,110,485,400,75))screen=HELP;}
            else if(screen==MAP){if(hit(x,y,30,600,200,90))screen=MENU;else{int col=(int)((x-75)/280),row=(int)((y-145)/130),n=row*4+col;if(col>=0&&col<4&&row>=0&&row<3&&n<=unlocked)buildLevel(n);}}
            else if(screen==HELP){if(hit(x,y,20,590,250,110))screen=MENU;}
            else if(screen==PLAY){if(hit(x,y,1080,0,200,105)){screen=PAUSE;}else if(y>575&&x<260){if(x<150)held.add(id);else held.add(id);if(x<150)touchLeft=id;else touchRight=id;}else if(y>500&&x>1080&&y<605){touchDash=id;held.add(id);}else if(y>585&&x>980){touchShoot=id;held.add(id);}else if(y>560&&x>930&&x<1115){touchJump=id;held.add(id);}}
            else if(screen==PAUSE){if(hit(x,y,100,285,300,80))screen=PLAY;else if(hit(x,y,100,365,300,75))screen=HELP;else if(hit(x,y,100,440,300,75))screen=MAP;else if(hit(x,y,100,510,300,80))screen=MENU;}
            else if(screen==CLEAR){if(hit(x,y,100,325,320,80)){if(level<11)buildLevel(level+1);else buildLevel(11);}else if(hit(x,y,100,405,320,75))screen=MAP;else if(hit(x,y,100,480,320,80))screen=MENU;}
            else if(screen==FAILED){if(hit(x,y,100,330,320,80))buildLevel(level);else if(hit(x,y,100,410,320,75))screen=MAP;else if(hit(x,y,100,485,320,80))screen=MENU;}}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP||action==MotionEvent.ACTION_CANCEL){held.remove(id);if(id==touchLeft)touchLeft=-1;if(id==touchRight)touchRight=-1;if(id==touchJump)touchJump=-1;if(id==touchDash)touchDash=-1;if(id==touchShoot)touchShoot=-1;}
        if(action==MotionEvent.ACTION_MOVE&&screen==PLAY){for(int i=0;i<e.getPointerCount();i++){int pid=e.getPointerId(i);float mx=e.getX(i)*W/getWidth(),my=e.getY(i)*H/getHeight();if(pid==touchLeft&&mx>250)touchLeft=-1;if(pid==touchRight&&mx<75)touchRight=-1;}}
        invalidate();return true;}
    private boolean hit(float x,float y,float bx,float by,float bw,float bh){return x>=bx&&x<=bx+bw&&y>=by&&y<=by+bh;}
}
