package app.tidecard.test;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import app.tidecard.*;
import app.tidecard.R;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** UI-only checks. Never starts KeepService or invokes apply / restore. */
final class RegionUiChecks {
    private final Instrumentation test;private final Context context;private final TideApp app;private final StringBuilder log=new StringBuilder();
    private volatile Activity current;private volatile MainActivity home;private volatile NotificationSettingsActivity settings;private volatile RegionPickerActivity picker;
    private final List<Activity> created=new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Application.ActivityLifecycleCallbacks callbacks=new Application.ActivityLifecycleCallbacks(){
        public void onActivityCreated(Activity a,Bundle b){created.add(a);}public void onActivityStarted(Activity a){}
        public void onActivityResumed(Activity a){current=a;if(a instanceof MainActivity)home=(MainActivity)a;if(a instanceof NotificationSettingsActivity)settings=(NotificationSettingsActivity)a;if(a instanceof RegionPickerActivity)picker=(RegionPickerActivity)a;}
        public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
    };
    private RegionUiChecks(Instrumentation test){this.test=test;context=test.getTargetContext();app=(TideApp)context.getApplicationContext();}
    static void run(Instrumentation test){new RegionUiChecks(test).run();}
    private interface Work{void run()throws Exception;}private interface Condition{boolean ok()throws Exception;}
    private void main(Work work){AtomicReference<Throwable> error=new AtomicReference<>();test.runOnMainSync(()->{try{work.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new AssertionError(error.get());}
    private void require(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    private void waitFor(Condition condition,int timeout,String reason)throws Exception{long end=System.currentTimeMillis()+timeout;while(System.currentTimeMillis()<end){if(condition.ok()){test.waitForIdleSync();return;}Thread.sleep(100);}throw new AssertionError(reason);}
    private void note(String line){log.append(line).append('\n');Bundle status=new Bundle();status.putString("stream",line+"\n");test.sendStatus(0,status);}
    private void launch(Class<? extends Activity> activity){main(()->context.startActivity(new Intent(context,activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP)));}
    private void capture(Activity activity,String name){main(()->{View view=activity.getWindow().getDecorView();Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));try(FileOutputStream output=new FileOutputStream(new File(context.getExternalFilesDir(null),name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,output);}finally{bitmap.recycle();}});}
    private String selection(){return ((TextView)picker.findViewById(R.id.region_selection)).getText().toString();}
    private void search(String value){main(()->((EditText)picker.findViewById(R.id.region_search)).setText(value));test.waitForIdleSync();}
    private void tapDial(boolean drag){main(()->{View view=picker.findViewById(R.id.region_dial);float cx=view.getWidth()/2f,cy=view.getHeight()/2f,r=Math.min(view.getWidth(),view.getHeight())*.48f*.74f;long at=SystemClock.uptimeMillis();float x=drag?cx:cx+r,y=drag?cy-r:cy;MotionEvent down=MotionEvent.obtain(at,at,MotionEvent.ACTION_DOWN,x,y,0);view.dispatchTouchEvent(down);down.recycle();if(drag){MotionEvent move=MotionEvent.obtain(at,at+100,MotionEvent.ACTION_MOVE,cx+r,cy,0);view.dispatchTouchEvent(move);move.recycle();}MotionEvent up=MotionEvent.obtain(at,at+160,MotionEvent.ACTION_UP,cx+r,cy,0);view.dispatchTouchEvent(up);up.recycle();});}
    private void restore(Map<String,?> before){SharedPreferences.Editor edit=app.prefs().edit();for(String key:new String[]{"country","mask",AppAppearance.KEY}){edit.remove(key);Object value=before.get(key);if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);}require(edit.commit(),"preference restore failed");}
    private void run(){Map<String,?> before=new HashMap<>(app.prefs().getAll());String originalCountry=app.country();int originalMask=app.mask();boolean idle=!app.active()&&!app.busy,passed=false;app.registerActivityLifecycleCallbacks(callbacks);
        try{
            note("BASELINE active="+app.active()+" country="+originalCountry+" mask="+originalMask+"; no carrier writes in this test");
            launch(NotificationSettingsActivity.class);note("WAIT UI settings; an ADB foreground launch of UsbActivationActivity may be needed on HyperOS");
            long end=System.currentTimeMillis()+45000;while(settings==null&&System.currentTimeMillis()<end){Thread.sleep(1000);if(settings==null)launch(NotificationSettingsActivity.class);}require(settings!=null,"settings did not launch");
            for(String mode:new String[]{"light","dark","system"}){
                if(current!=settings){launch(NotificationSettingsActivity.class);waitFor(()->current instanceof NotificationSettingsActivity,6000,"settings resume");}
                NotificationSettingsActivity old=settings;int id="light".equals(mode)?R.id.appearance_light:"dark".equals(mode)?R.id.appearance_dark:R.id.appearance_system;main(()->old.findViewById(id).performClick());waitFor(()->settings!=old&&current==settings,7000,"theme recreation "+mode);
                require(mode.equals(AppAppearance.mode(context)),"theme not persisted");boolean expected=AppearanceMode.dark(mode,(android.content.res.Resources.getSystem().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
                require(((settings.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES)==expected,"wrong resource theme "+mode);capture(settings,"ui-settings-"+mode);
                if(idle){launch(MainActivity.class);waitFor(()->current instanceof MainActivity&&((home.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES)==expected,6000,"home theme resume");main(()->home.findViewById(R.id.region).performClick());}
                else{launch(RegionPickerActivity.class);}
                waitFor(()->current instanceof RegionPickerActivity,6000,"dial launch");require(Regions.ALL.length==249,"catalog count");capture(picker,"ui-dial-full-"+mode);
                search("Netherlands");require(selection().contains("NL"),"English exact-name search");require(originalCountry.equals(app.country()),"draft search mutated saved target");
                search("NO-SUCH-REGION");require(!picker.findViewById(R.id.region_confirm).isEnabled(),"empty result confirm enabled");
                search("日本");require(selection().contains("JP"),"Chinese search");search("HK");require(selection().contains("HK"),"ISO search");search("");
                String first=selection();main(()->picker.findViewById(R.id.region_next).performClick());require(!first.equals(selection()),"next button failed");main(()->picker.findViewById(R.id.region_previous).performClick());require(first.equals(selection()),"previous button failed");
                RegionDialView dial=(RegionDialView)picker.findViewById(R.id.region_dial);main(()->dial.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,null));require(!first.equals(selection()),"TalkBack scroll action failed");main(()->dial.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,null));
                first=selection();tapDial(false);require(!first.equals(selection()),"tap chip failed");first=selection();tapDial(true);require(!first.equals(selection()),"rotary gesture failed");require(originalCountry.equals(app.country()),"rotation changed persisted target");
                search("Netherlands");Thread.sleep(300);capture(picker,"ui-dial-"+mode);
                if(idle){main(()->picker.findViewById(R.id.region_confirm).performClick());waitFor(()->current instanceof MainActivity&&"nl".equals(app.country()),6000,"confirmed result not saved");require(!app.active(),"region confirmation unexpectedly started a carrier session");capture(home,"ui-home-"+mode);main(()->app.saveSelection(originalCountry,originalMask));}
                else main(()->picker.finish());
                note("PASS "+mode+": resources, search, empty guard, buttons, touch rotation, accessibility, draft isolation and confirmation");
            }
            require(Objects.equals(before.get("snapshot"),app.prefs().getString("snapshot",null)),"recovery snapshot changed");require(Objects.equals(before.get("restorePending"),app.prefs().getAll().get("restorePending")),"recovery flag changed");passed=true;
        }catch(Throwable e){note("FAIL "+e);}
        finally{try{restore(before);main(()->{for(Activity a:created)if(!a.isFinishing())a.finish();});app.unregisterActivityLifecycleCallbacks(callbacks);require(originalCountry.equals(app.country())&&originalMask==app.mask(),"selection restore");require(before.equals(app.prefs().getAll()),"unrelated preferences changed");note("PASS original preferences and carrier-session state retained");}catch(Throwable e){note("CLEANUP FAIL "+e);passed=false;}Bundle result=new Bundle();result.putString("stream",log.toString());test.finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
    }
}
