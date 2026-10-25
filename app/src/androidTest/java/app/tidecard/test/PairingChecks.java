package app.tidecard.test;

import android.app.*;
import android.content.*;
import android.os.*;
import android.service.notification.StatusBarNotification;
import app.tidecard.*;
import java.lang.reflect.*;
import java.util.*;

/** Notification/lifecycle checks only. No SIM calls and no real pairing success is claimed. */
final class PairingChecks {
    private final Instrumentation test;private final Context context;private final TideApp app;
    private final StringBuilder log=new StringBuilder();
    private PairingChecks(Instrumentation t){test=t;context=t.getTargetContext();app=(TideApp)context.getApplicationContext();}
    static void run(Instrumentation t){t.waitForIdleSync();new PairingChecks(t).run();}
    private void note(String s){log.append(s).append('\n');Bundle b=new Bundle();b.putString("stream",s+"\n");test.sendStatus(0,b);}
    private void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private Object service()throws Exception{Field f=ActivationService.class.getDeclaredField("current");f.setAccessible(true);return f.get(null);}
    private void call(Object target,String method)throws Exception{Method m=target.getClass().getDeclaredMethod(method);m.setAccessible(true);m.invoke(target);}
    private Notification notification(int id){for(StatusBarNotification n:context.getSystemService(NotificationManager.class).getActiveNotifications())if(n.getId()==id)return n.getNotification();return null;}
    private void start(){context.startForegroundService(new Intent(context,ActivationService.class).setAction(ActivationService.SCAN));}
    private void cancel(){context.stopService(new Intent(context,ActivationService.class));}
    private void receive(Object s,String session,String code,boolean open)throws Exception{Method m=s.getClass().getDeclaredMethod("receive",String.class,String.class,boolean.class);m.setAccessible(true);m.invoke(null,session,code,open);}
    private void main(Checked action){test.runOnMainSync(()->{try{action.run();}catch(Exception e){throw new RuntimeException(e);}});}
    private interface Checked{void run()throws Exception;}
    private void waitGone()throws Exception{for(int i=0;i<30&&service()!=null;i++)Thread.sleep(100);require(service()==null,"service did not stop");}
    private void capture(String name)throws Exception{android.graphics.Bitmap image=test.getUiAutomation().takeScreenshot();if(image!=null){try(java.io.FileOutputStream file=new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null),name+".png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,file);}image.recycle();}}
    private void run(){
        Map<String,?> before=new HashMap<>(app.prefs().getAll());boolean passed=false;
        try{
            main(this::start);Thread.sleep(700);Object s=service();require(s!=null,"service missing");
            Notification n=notification(22);require(n!=null,"pairing notification missing");
            require(n.extras.getBoolean("android.requestPromotedOngoing"),"pairing did not request promotion");
            if(Build.VERSION.SDK_INT>=36)require(n.hasPromotableCharacteristics(),"notification ineligible");
            note("PASS pairing notification eligible for native Live Update");
            main(()->{ActivationService.pairingPort=12345;call(s,"publish");});Thread.sleep(300);
            n=notification(22);require(n.contentIntent!=null,"island card input action missing");
            require(n.actions[0].getRemoteInputs()==null,"promoted input entry must be a standard action");
            String session=(String)field(s,"session");
            main(()->receive(s,"stale-session",null,true));require(!(boolean)field(s,"inputMode"),"stale session changed input state");
            main(()->receive(s,session,"12",false));require(!ActivationService.working,"invalid code started pairing");
            note("PASS stale actions and incomplete codes rejected");
            main(()->receive(s,session,null,true));Thread.sleep(300);n=notification(22);
            require(!n.extras.getBoolean("android.requestPromotedOngoing"),"input must use an ordinary notification");
            require(n.actions[0].getRemoteInputs()!=null,"SystemUI RemoteInput missing after demotion");
            note("PASS island action switches to an ordinary notification with protected SystemUI input");
            test.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);Thread.sleep(600);
            main(()->call(s,"paired"));Thread.sleep(100);int first=notification(22).getSmallIcon().getResId();capture("pairing-ui-frame-0");
            require(notification(22).extras.getBoolean("android.requestPromotedOngoing"),"success feedback missing promotion");
            Thread.sleep(270);capture("pairing-ui-frame-1");Thread.sleep(530);require(notification(22).getSmallIcon().getResId()!=first,"success icon frames did not advance");capture("pairing-ui-frame-3");
            Thread.sleep(1500);n=notification(22);require(n==null||!n.extras.getBoolean("android.requestPromotedOngoing"),"promotion continued after success");
            note("PASS injected success advances check frames and ends promotion (not a crypto test)");
            main(this::cancel);waitGone();Thread.sleep(300);require(notification(22)==null&&notification(23)==null,"cancel left notification");
            main(this::start);Thread.sleep(300);Object fresh=service();
            main(()->((Runnable)field(fresh,"timeout")).run());waitGone();require(notification(22)==null,"timeout leaked notification");
            note("PASS cancellation and timeout remove notification and input window");
            main(this::start);Thread.sleep(300);Object last=service();
            main(()->{Method m=last.getClass().getDeclaredMethod("pair",String.class,int.class);m.setAccessible(true);m.invoke(last,"000000",9);cancel();});
            waitGone();Thread.sleep(2000);require(notification(22)==null&&notification(23)==null,"late worker resurrected notification");
            note("PASS late failed pairing cannot recreate a cancelled notification");
            for(String key:new String[]{"snapshot","country","mask","restorePending","paired","bridgeToken"})require(Objects.equals(before.get(key),app.prefs().getAll().get(key)),"preference changed: "+key);
            note("PASS active card session, restore snapshot, selection, and pairing identity preserved");passed=true;
        }catch(Throwable e){note("FAIL "+e);}
        finally{main(this::cancel);Bundle result=new Bundle();result.putString("stream",log.toString());test.finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
    }
}
