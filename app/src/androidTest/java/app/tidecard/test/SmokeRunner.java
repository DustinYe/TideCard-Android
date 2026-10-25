package app.tidecard.test;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import app.tidecard.*;
import app.tidecard.R;
import org.json.*;
import java.io.*;

public class SmokeRunner extends Instrumentation {
    MainActivity activity;TideApp app;StringBuilder log=new StringBuilder();boolean uiOnly,setupOnly,wirelessOnly,notificationOnly;
    void note(String s){log.append(s).append('\n');Bundle b=new Bundle();b.putString("stream",s+"\n");sendStatus(0,b);}
    void require(boolean v,String s){if(!v)throw new AssertionError(s);}
    interface Condition{boolean ok();}
    void waitFor(Condition c,long timeout,String msg)throws Exception{long end=System.currentTimeMillis()+timeout;while(System.currentTimeMillis()<end){if(c.ok())return;Thread.sleep(300);}throw new AssertionError(msg+": "+app.message);}
    JSONObject inspect()throws Exception{return app.call("inspect",new JSONObject());}
    String country(JSONObject o,int slot)throws Exception{return o.getJSONArray("cards").getJSONObject(slot).optString("country");}
    void capture(String name)throws Exception{Bitmap b=getUiAutomation().takeScreenshot();if(b!=null){try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}}
    void toggle(){runOnMainSync(()->{note("CLICK active="+app.active()+" busy="+app.busy+" authorized="+app.authorized()+" bridge="+(app.bridge!=null));boolean clicked=activity.findViewById(R.id.toggle).performClick();note("CLICK delivered="+clicked);});}
    void selection(String iso,int mask){runOnMainSync(()->{app.saveSelection(iso,app.mask());activity.findViewById(mask==3?R.id.slot_both:mask==1?R.id.slot_one:R.id.slot_two).performClick();});}
    @Override public void onCreate(Bundle b){super.onCreate(b);uiOnly=b!=null&&"true".equals(b.getString("uiOnly"));setupOnly=b!=null&&"true".equals(b.getString("setup"));wirelessOnly=b!=null&&"true".equals(b.getString("wirelessOnly"));notificationOnly=b!=null&&"true".equals(b.getString("notificationOnly"));start();}
    @Override public void onStart(){
        if(notificationOnly){PairingChecks.run(this);return;}
        Bundle result=new Bundle();boolean passed=false,restoreCreatedSession=false;
        try{
            note("BOOT instrumentation started");
            app=(TideApp)getTargetContext().getApplicationContext();
            if(setupOnly){try(FileOutputStream f=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"activation-command.txt"))){f.write(app.startCommand().getBytes(java.nio.charset.StandardCharsets.UTF_8));}passed=true;note("Activation command prepared locally");return;}
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
                public void onActivityCreated(Activity a,Bundle b){} public void onActivityStarted(Activity a){} public void onActivityResumed(Activity a){if(a instanceof MainActivity)activity=(MainActivity)a;}
                public void onActivityPaused(Activity a){} public void onActivityStopped(Activity a){} public void onActivitySaveInstanceState(Activity a,Bundle b){} public void onActivityDestroyed(Activity a){}
            });
            runOnMainSync(()->getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            waitFor(()->activity!=null,15000,"启动界面");
            if(wirelessOnly){
                require(app.prefs().getBoolean("paired",false),"尚未完成无线配对");require(!app.authorized(),"测试前内置后台仍在运行，不能证明无线启动");
                byte[] first=new app.tidecard.adb.AdbIdentity(getTargetContext()).publicAdb();byte[] second=new app.tidecard.adb.AdbIdentity(getTargetContext()).publicAdb();require(java.util.Arrays.equals(first,second),"配对身份未持久保存");note("PASS encrypted pairing identity survives reload");
                runOnMainSync(()->activity.startForegroundService(new Intent(activity,ActivationService.class).setAction(ActivationService.CONNECT)));
                waitFor(()->app.authorized(),35000,"无线启动失败："+ActivationService.status);note("PASS wireless TLS activation without external Shizuku");note("WIRELESS "+inspect());
                runOnMainSync(()->activity.startActivity(new Intent(activity,ActivationActivity.class)));Thread.sleep(700);capture("06-wireless-activated");passed=true;return;
            }
            waitFor(()->app.bridge!=null,60000,"等待内置后台连接");require(!app.active(),"测试前已有运行会话");restoreCreatedSession=true;JSONObject baseline=inspect();
            note("BASELINE "+baseline);capture("01-stopped");
            if(uiOnly){
                runOnMainSync(()->activity.findViewById(R.id.region).performClick());Thread.sleep(500);
                java.lang.reflect.Field field=MainActivity.class.getDeclaredField("regionDialog");field.setAccessible(true);AlertDialog dialog=(AlertDialog)field.get(activity);
                require(dialog!=null&&dialog.isShowing(),"地区列表未打开");require(dialog.getListView().getCount()==10,"地区数量错误");capture("04-regions");
                runOnMainSync(()->dialog.getListView().performItemClick(dialog.getListView().getChildAt(1),1,dialog.getListView().getAdapter().getItemId(1)));
                require("us".equals(app.country()),"地区选择未保存");Thread.sleep(400);capture("05-us-selected");selection("jp",3);note("PASS region picker and selection");passed=true;return;
            }
            for(int mask:new int[]{1,2,3}){
                String iso=mask==2?"us":"jp";selection(iso,mask);toggle();waitFor(()->app.active()&&!app.busy&&app.message.contains("已核对"),20000,"启动失败");
                JSONObject current=inspect();for(int slot=0;slot<2;slot++){if(current.getJSONArray("cards").getJSONObject(slot).optInt("subId",-1)<0)continue;String expected=(mask&(1<<slot))!=0?iso:country(baseline,slot);require(expected.equals(country(current,slot)),"卡槽范围错误 slot="+slot);}
                note("PASS start mask="+mask+" country="+iso+" "+current);capture("02-active-"+mask);
                if(mask==3){
                    ICarrierBridge saved=app.bridge;
                    app.call("apply",new JSONObject().put("snapshot",new JSONObject(app.prefs().getString("snapshot","{}"))).put("country","us").put("name","T-Mobile"));
                    waitFor(()->{try{return "jp".equals(country(inspect(),0))&&"jp".equals(country(inspect(),1));}catch(Exception e){return false;}},40000,"持续保持未恢复目标国家码");note("PASS watchdog repaired drift");
                    runOnMainSync(()->app.bridge=null);toggle();waitFor(()->!app.busy&&app.prefs().getBoolean("restorePending",false),10000,"未保留恢复失败状态");require(app.active(),"恢复失败时错误清除了备份");note("PASS restore failure retained snapshot");runOnMainSync(()->app.bridge=saved);
                    android.service.notification.StatusBarNotification[] all=activity.getSystemService(NotificationManager.class).getActiveNotifications();require(all.length>0,"没有前台通知");all[0].getNotification().actions[0].actionIntent.send();
                }else toggle();
                waitFor(()->!app.active()&&!app.busy,18000,"停止恢复失败");JSONObject restored=inspect();for(int slot=0;slot<2;slot++)require(country(baseline,slot).equals(country(restored,slot)),"停止后未恢复卡 "+slot);note("PASS stop restored mask="+mask+" "+restored);
            }
            selection("jp",3);capture("03-restored");passed=true;note("ALL TESTS PASSED");
        }catch(Throwable e){note("ACTIVATION status="+ActivationService.status+" pairingPort="+ActivationService.pairingPort+" connectionPort="+ActivationService.connectionPort+" working="+ActivationService.working);note("FAIL "+e);result.putString("error",e.toString());}
        finally{
            if(restoreCreatedSession&&app!=null&&app.active())try{JSONObject snapshot=new JSONObject(app.prefs().getString("snapshot","{}"));app.call("restore",snapshot);app.prefs().edit().remove("snapshot").remove("restorePending").commit();getTargetContext().stopService(new Intent(getTargetContext(),KeepService.class));}catch(Exception e){note("CLEANUP FAILED "+e);passed=false;}
            result.putString("stream",log.toString());finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }
    }
}
