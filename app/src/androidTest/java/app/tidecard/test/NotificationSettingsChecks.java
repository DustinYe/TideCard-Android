package app.tidecard.test;

import android.app.*;
import android.content.*;
import android.os.*;
import android.service.notification.StatusBarNotification;
import android.widget.Switch;
import app.tidecard.*;
import app.tidecard.R;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Exercises real settings switches and this app's channel without changing SIM selections. */
final class NotificationSettingsChecks {
    private static final String LIVE="pairingLiveUpdates",IMPORTANCE="sessionNotificationImportance",CHANNEL="region_session";
    private final Instrumentation test;private final Context context;private final TideApp app;private final NotificationManager notifications;
    private final StringBuilder log=new StringBuilder();
    private volatile MainActivity mainActivity;private volatile NotificationSettingsActivity activity;
    private final Application.ActivityLifecycleCallbacks activities=new Application.ActivityLifecycleCallbacks(){
        public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}
        public void onActivityResumed(Activity a){if(a instanceof MainActivity)mainActivity=(MainActivity)a;if(a instanceof NotificationSettingsActivity)activity=(NotificationSettingsActivity)a;}
        public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
    };
    private NotificationSettingsChecks(Instrumentation t){test=t;context=t.getTargetContext();app=(TideApp)context.getApplicationContext();notifications=context.getSystemService(NotificationManager.class);}
    static void run(Instrumentation test){new NotificationSettingsChecks(test).run();}
    private interface Checked{void run()throws Exception;}
    private interface Condition{boolean ok()throws Exception;}
    private void main(Checked work){test.runOnMainSync(()->{try{work.run();}catch(Exception e){throw new RuntimeException(e);}});}
    private void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private void waitFor(Condition condition,long ms,String message)throws Exception{long end=System.currentTimeMillis()+ms;while(System.currentTimeMillis()<end){if(condition.ok())return;Thread.sleep(100);}throw new AssertionError(message);}
    private void note(String line){log.append(line).append('\n');Bundle b=new Bundle();b.putString("stream",line+"\n");test.sendStatus(0,b);}
    private NotificationChannel channel(){return notifications.getNotificationChannel(CHANNEL);}
    private int importance(){NotificationChannel c=channel();return c==null?2:c.getImportance();}
    private boolean checked(int id){return ((Switch)activity.findViewById(id)).isChecked();}
    private void select(int id,boolean enabled)throws Exception{if(checked(id)!=enabled)main(()->activity.findViewById(id).performClick());}
    private Notification notification(int id){for(StatusBarNotification n:notifications.getActiveNotifications())if(n.getId()==id)return n.getNotification();return null;}
    private boolean foreground(){for(ActivityManager.RunningServiceInfo service:context.getSystemService(ActivityManager.class).getRunningServices(50))if(service.service.getClassName().equals(KeepService.class.getName()))return service.foreground;return false;}
    private Object service()throws Exception{Field f=ActivationService.class.getDeclaredField("current");f.setAccessible(true);return f.get(null);}
    private void call(Object target,String name)throws Exception{Method m=target.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(target);}
    private String metadata(NotificationChannel c){return String.valueOf(c.getSound())+"|"+c.getAudioAttributes()+"|"+Arrays.toString(c.getVibrationPattern())+"|"+c.shouldVibrate()+"|"+c.shouldShowLights()+"|"+c.getLightColor()+"|"+c.canShowBadge()+"|"+c.canBypassDnd()+"|"+c.getLockscreenVisibility()+"|"+c.getGroup();}
    private void recreate()throws Exception{NotificationSettingsActivity old=activity;main(old::recreate);waitFor(()->activity!=old,5000,"settings recreation failed");test.waitForIdleSync();}
    private void screenshot()throws Exception{android.graphics.Bitmap bitmap=test.getUiAutomation().takeScreenshot();if(bitmap!=null){try(java.io.FileOutputStream output=new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null),"notification-settings.png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}bitmap.recycle();}}
    private void run(){
        Map<String,?> before=new HashMap<>(app.prefs().getAll());int original=-1;String meta=null;boolean passed=false;
        app.registerActivityLifecycleCallbacks(activities);
        try{
            main(()->context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            note("WAIT main activity; HyperOS may require an ADB foreground launch for instrumentation");
            waitFor(()->mainActivity!=null,45000,"main activity missing");
            waitFor(app::authorized,45000,"backend not ready");
            main(()->mainActivity.findViewById(R.id.notification_settings).performClick());
            waitFor(()->activity!=null,8000,"notification settings entry failed");
            original=importance();meta=metadata(channel());
            select(R.id.session_notice_switch,true);waitFor(()->importance()>0&&checked(R.id.session_notice_switch),7000,"show notification switch failed");
            if(app.active()){waitFor(()->notification(21)!=null,5000,"ongoing notification did not reappear");require(!notification(21).extras.getBoolean("android.requestPromotedOngoing"),"ongoing notification requested island");require(foreground(),"monitor service stopped");}
            select(R.id.session_notice_switch,false);waitFor(()->importance()==0&&!checked(R.id.session_notice_switch),7000,"hide notification switch failed");
            if(app.active()){waitFor(()->notification(21)==null,5000,"blocked notification remained active");require(foreground(),"hiding notification stopped monitor");}
            require(meta.equals(metadata(channel())),"unrelated channel metadata changed");
            note("PASS in-app ongoing notification on/off, foreground monitoring retained, channel metadata preserved");
            recreate();require(!checked(R.id.session_notice_switch),"hidden channel setting did not persist");
            select(R.id.pairing_live_switch,false);recreate();require(!checked(R.id.pairing_live_switch),"island choice did not persist");
            main(()->context.startForegroundService(new Intent(context,ActivationService.class).setAction(ActivationService.SCAN)));
            waitFor(()->service()!=null,5000,"pairing service missing");Object service=service();
            main(()->{ActivationService.pairingPort=12345;call(service,"publish");});
            waitFor(()->notification(22)!=null&&!notification(22).extras.getBoolean("android.requestPromotedOngoing"),3000,"island off still promoted");
            require(notification(22).actions[0].getRemoteInputs()!=null,"ordinary pairing notification lacks direct input");
            select(R.id.pairing_live_switch,true);
            if(Build.VERSION.SDK_INT>=36&&notifications.canPostPromotedNotifications())waitFor(()->notification(22).extras.getBoolean("android.requestPromotedOngoing"),3000,"live preference not applied to running session");
            select(R.id.pairing_live_switch,false);waitFor(()->!notification(22).extras.getBoolean("android.requestPromotedOngoing"),3000,"disabling island not applied");
            main(()->call(service,"paired"));Thread.sleep(350);require(!notification(22).extras.getBoolean("android.requestPromotedOngoing"),"injected success ignored island-off choice");
            Thread.sleep(2100);require(notification(22)==null,"injected success failed to clean up");
            note("PASS island opt-out persists, ordinary notification has direct input, running session and success honor preference (injected UI)");
            boolean rejected=false;try{app.call("session_notification",new JSONObject().put("enabled",true).put("importance",99));}catch(Exception expected){rejected=true;}
            require(rejected&&importance()==0,"invalid notification level accepted");
            for(String key:new String[]{"snapshot","country","mask","restorePending","paired","bridgeToken"})require(Objects.equals(before.get(key),app.prefs().getAll().get(key)),"card session preference changed: "+key);
            note("PASS invalid notification level rejected and active SIM session/restore snapshot retained");passed=true;
        }catch(Throwable error){note("FAIL "+error);}
        finally{
            main(()->context.stopService(new Intent(context,ActivationService.class)));
            try{if(original>=0&&app.authorized())app.call("session_notification",new JSONObject().put("enabled",original>0).put("importance",original>0?original:2));
                main(()->{SharedPreferences.Editor edit=app.prefs().edit();if(before.containsKey(LIVE))edit.putBoolean(LIVE,(Boolean)before.get(LIVE));else edit.remove(LIVE);if(before.containsKey(IMPORTANCE))edit.putInt(IMPORTANCE,(Integer)before.get(IMPORTANCE));else edit.remove(IMPORTANCE);edit.commit();app.changed();});
                Thread.sleep(300);if(activity!=null)screenshot();
            }catch(Exception error){note("CLEANUP FAILED "+error);passed=false;}
            app.unregisterActivityLifecycleCallbacks(activities);Bundle result=new Bundle();result.putString("stream",log.toString());test.finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }
    }
}
