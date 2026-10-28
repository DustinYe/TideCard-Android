package app.tidecard;

import android.animation.*;
import android.content.Context;
import android.graphics.*;
import android.view.*;

public class PlayButton extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float phase,progress;
    private boolean active,busy,paused;
    private ValueAnimator loop,morph;
    private final AppPalette colors;
    public PlayButton(Context c){super(c);colors=new AppPalette(AppAppearance.dark(c));setClickable(true);setFocusable(true);setElevation(0);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
    public void state(boolean running,boolean waiting){
        state(running,waiting,false);
    }
    public void state(boolean running,boolean waiting,boolean recovery){
        busy=waiting;paused=recovery;
        if(active!=running){active=running;if(morph!=null)morph.cancel();morph=ValueAnimator.ofFloat(progress,active?1:0);morph.setDuration(280);morph.addUpdateListener(a->{progress=(float)a.getAnimatedValue();invalidate();});morph.start();}
        setContentDescription(waiting?I18n.tr("正在处理，请稍候"):running?recovery?I18n.tr("恢复待处理的原配置"):I18n.tr("停止改卡并恢复原配置"):I18n.tr("启动持续改卡"));invalidate();
        if(loop!=null){if(((active&&!paused)||busy)&&!loop.isRunning())loop.start();else if((!active||paused)&&!busy)loop.cancel();}
    }
    @Override public CharSequence getAccessibilityClassName(){return android.widget.Button.class.getName();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();loop=ValueAnimator.ofFloat(0,1);loop.setDuration(2300);loop.setRepeatCount(ValueAnimator.INFINITE);loop.addUpdateListener(a->{phase=(float)a.getAnimatedValue();if((active&&!paused)||busy)invalidate();});if((active&&!paused)||busy)loop.start();}
    @Override protected void onDetachedFromWindow(){if(loop!=null)loop.cancel();if(morph!=null)morph.cancel();super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float s=Math.min(getWidth(),getHeight()),cx=getWidth()/2f,cy=getHeight()/2f,r=s*.365f;int icon=active?colors.ink:colors.onAccent;
        p.setStyle(Paint.Style.FILL);p.setColor((colors.accent&0x00FFFFFF)|0x19000000);canvas.drawCircle(cx,cy+3,r+6,p);
        if((active&&!paused)||busy){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(s*.017f);p.setColor((colors.accent&0x00FFFFFF)|0x60000000);float rad=r+8+phase*5;RectF outer=new RectF(cx-rad,cy-rad,cx+rad,cy+rad);canvas.drawArc(outer,phase*360,250,false,p);}
        p.setStyle(Paint.Style.FILL);p.setColor(active?colors.soft:colors.accent);canvas.drawCircle(cx,cy,r,p);
        p.setColor(icon);p.setAlpha((int)(255*(1-progress)));Path play=new Path();play.moveTo(cx-r*.20f,cy-r*.38f);play.lineTo(cx+r*.40f,cy);play.lineTo(cx-r*.20f,cy+r*.38f);play.close();canvas.drawPath(play,p);
        p.setAlpha((int)(255*progress));canvas.drawRoundRect(cx-r*.28f,cy-r*.28f,cx+r*.28f,cy+r*.28f,r*.08f,r*.08f,p);p.setAlpha(255);
        if(busy){p.setStyle(Paint.Style.STROKE);p.setColor((icon&0x00FFFFFF)|0x99000000);p.setStrokeWidth(2);RectF box=new RectF(cx-r*.7f,cy-r*.7f,cx+r*.7f,cy+r*.7f);canvas.drawArc(box,phase*720,70,false,p);p.setStyle(Paint.Style.FILL);}
    }
}
