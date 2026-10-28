package app.tidecard;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.view.accessibility.*;

/** Original Canvas dial. Draft selection only; no service or carrier operations. */
public final class RegionDialView extends View {
    private final RegionWheelModel model;private final Runnable changed;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final TextPaint text=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animation;private float offset,lastAngle,drag;private boolean dragging;private final float density;
    public RegionDialView(Context c,RegionWheelModel model,Runnable changed){super(c);this.model=model;this.changed=changed;density=getResources().getDisplayMetrics().density;setFocusable(true);setClickable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);updateDescription();}
    private float dp(float v){return v*density;}private float sp(float v){return v*getResources().getDisplayMetrics().scaledDensity;}
    @Override protected void onMeasure(int ws,int hs){int width=resolveSize((int)dp(340),ws);int height=MeasureSpec.getMode(hs)==MeasureSpec.UNSPECIFIED?width:Math.min(width,MeasureSpec.getSize(hs));setMeasuredDimension(width,height);}
    private float radius(){return Math.min(getWidth(),getHeight())*.48f;}private int visible(){return Math.min(8,model.size());}private float gap(){return visible()==0?45:360f/visible();}private int first(){return -(visible()-1)/2;}
    private void label(Canvas c,String value,float x,float y,float size,int color){text.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));text.setTextAlign(Paint.Align.CENTER);text.setTextSize(size);text.setColor(color);c.drawText(value,x,y-(text.ascent()+text.descent())/2,text);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f,r=radius();
        paint.setStyle(Paint.Style.FILL);paint.setShader(new RadialGradient(cx,cy,r,new int[]{0xFF263D30,0xFF101C16},null,Shader.TileMode.CLAMP));c.drawCircle(cx,cy,r,paint);paint.setShader(null);
        paint.setStrokeWidth(dp(1.2f));for(int i=0;i<60;i++){double a=Math.toRadians(i*6-90);float outer=r*.95f,inner=r*(i%5==0?.90f:.93f);paint.setColor(i%5==0?0xFF6F8C77:0xFF3B5442);c.drawLine(cx+(float)Math.cos(a)*inner,cy+(float)Math.sin(a)*inner,cx+(float)Math.cos(a)*outer,cy+(float)Math.sin(a)*outer,paint);}
        paint.setColor(0xFF1A2D22);c.drawCircle(cx,cy,r*.49f,paint);paint.setColor(0xFF38513E);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));c.drawCircle(cx,cy,r*.49f,paint);paint.setStyle(Paint.Style.FILL);
        String[] selected=model.selected();if(selected==null){label(c,I18n.tr("没有匹配地区"),cx,cy,Math.min(sp(18),r*.14f),0xFFE8F1E2);return;}
        label(c,I18n.tr("目的地"),cx,cy-r*.28f,Math.min(sp(10),r*.085f),0xFF9CB59C);label(c,selected[4],cx,cy-r*.025f,Math.min(sp(46),r*.34f),0xFFD6EF9B);
        text.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));text.setTextAlign(Paint.Align.LEFT);text.setTextSize(Math.min(sp(15),r*.115f));text.setColor(0xFFE7EEE0);int width=(int)(r*.82f);
        StaticLayout name=StaticLayout.Builder.obtain(I18n.region(selected),0,I18n.region(selected).length(),text,width).setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).build();c.save();c.translate(cx-width/2f,cy+r*.17f);name.draw(c);c.restore();
        for(int j=0;j<visible();j++){int relative=first()+j;double a=Math.toRadians(-90+relative*gap()+offset);float x=cx+(float)Math.cos(a)*r*.74f,y=cy+(float)Math.sin(a)*r*.74f;String[] row=model.neighbor(relative);
            paint.setColor(relative==0?0xFFD6EF9B:0xFF2D4434);c.drawCircle(x,y,r*.125f,paint);label(c,row[4],x,y,Math.min(sp(13),r*.105f),relative==0?0xFF20351E:0xFFCDDFC8);
        }
        paint.setColor(0xFFD6EF9B);c.drawCircle(cx,cy-r*.875f,dp(2.4f),paint);
    }
    public void refresh(){if(animation!=null)animation.cancel();offset=0;updateDescription();invalidate();}
    public void step(int direction){if(model.size()<2)return;model.step(direction);offset=direction*gap();selectionChanged();snap();}
    private void selectionChanged(){updateDescription();changed.run();invalidate();performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);}
    private void updateDescription(){String[] row=model.selected();setContentDescription(row==null?I18n.tr("没有匹配地区"):I18n.tr("地区圆盘，")+I18n.region(row)+"，"+row[4]+I18n.tr("，第 ")+(model.index()+1)+I18n.tr(" 项，共 ")+model.size()+I18n.tr(" 项。可前后滚动或使用下方按钮。"));}
    private void snap(){if(animation!=null)animation.cancel();animation=ValueAnimator.ofFloat(offset,0);animation.setDuration(220);animation.setInterpolator(new android.view.animation.DecelerateInterpolator());animation.addUpdateListener(a->{offset=(float)a.getAnimatedValue();invalidate();});animation.start();}
    private float angle(MotionEvent e){return (float)Math.toDegrees(Math.atan2(e.getY()-getHeight()/2f,e.getX()-getWidth()/2f));}
    @Override public boolean onTouchEvent(MotionEvent e){if(model.size()==0)return false;switch(e.getActionMasked()){
        case MotionEvent.ACTION_DOWN:double distance=Math.hypot(e.getX()-getWidth()/2f,e.getY()-getHeight()/2f);if(distance<radius()*.52f||distance>radius())return false;if(animation!=null)animation.cancel();offset=0;drag=0;dragging=false;lastAngle=angle(e);getParent().requestDisallowInterceptTouchEvent(true);return true;
        case MotionEvent.ACTION_MOVE:float now=angle(e),delta=now-lastAngle;while(delta>180)delta-=360;while(delta< -180)delta+=360;lastAngle=now;drag+=Math.abs(delta);dragging=drag>6;offset+=delta;int steps=(int)(offset/gap());if(steps!=0){model.step(-steps);offset-=steps*gap();selectionChanged();}invalidate();return true;
        case MotionEvent.ACTION_UP:if(!dragging){float r=radius(),cx=getWidth()/2f,cy=getHeight()/2f;for(int j=0;j<visible();j++){int relative=first()+j;double a=Math.toRadians(-90+relative*gap()+offset);if(Math.hypot(e.getX()-cx-Math.cos(a)*r*.74f,e.getY()-cy-Math.sin(a)*r*.74f)<=Math.max(dp(26),r*.15f)){if(relative!=0){model.step(relative);offset=relative*gap();selectionChanged();}break;}}performClick();}getParent().requestDisallowInterceptTouchEvent(false);snap();return true;
        case MotionEvent.ACTION_CANCEL:getParent().requestDisallowInterceptTouchEvent(false);snap();return true;
    }return super.onTouchEvent(e);}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onKeyDown(int key,android.view.KeyEvent event){if(key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_DOWN){step(1);return true;}if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_UP){step(-1);return true;}return super.onKeyDown(key,event);}
    @Override public boolean onGenericMotionEvent(MotionEvent event){if(event.getAction()==MotionEvent.ACTION_SCROLL&&event.getAxisValue(MotionEvent.AXIS_VSCROLL)!=0){step(event.getAxisValue(MotionEvent.AXIS_VSCROLL)>0?-1:1);return true;}return super.onGenericMotionEvent(event);}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.SeekBar");info.setScrollable(model.size()>1);if(model.size()>1){info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,0,model.size()-1,model.index()));}}
    @Override public boolean performAccessibilityAction(int action,Bundle args){if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD){step(1);return true;}if(action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){step(-1);return true;}return super.performAccessibilityAction(action,args);}
    @Override protected void onDetachedFromWindow(){if(animation!=null)animation.cancel();super.onDetachedFromWindow();}
}
