package app.tidecard.test;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.Bundle;
import app.tidecard.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Explicit opt-in physical NL start/stop regression; it refuses a pre-existing session. */
final class CatalogSessionChecks {
    private final Instrumentation test;private final Context context;private final TideApp app;private final StringBuilder log=new StringBuilder();private volatile MainActivity activity;
    private CatalogSessionChecks(Instrumentation test){this.test=test;context=test.getTargetContext();app=(TideApp)context.getApplicationContext();}
    static void run(Instrumentation test){new CatalogSessionChecks(test).run();}
    private interface Work{void run()throws Exception;}private interface Condition{boolean ok()throws Exception;}
    private void main(Work work){AtomicReference<Throwable> error=new AtomicReference<>();test.runOnMainSync(()->{try{work.run();}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new AssertionError(error.get());}
    private void require(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    private void waitFor(Condition condition,int timeout,String reason)throws Exception{long end=System.currentTimeMillis()+timeout;while(System.currentTimeMillis()<end){if(condition.ok()){test.waitForIdleSync();return;}Thread.sleep(150);}throw new AssertionError(reason+": "+app.message);}
    private void note(String line){log.append(line).append('\n');Bundle b=new Bundle();b.putString("stream",line+"\n");test.sendStatus(0,b);}
    private JSONObject inspect()throws Exception{return app.call("inspect",new JSONObject());}
    private void countries(JSONObject expected,JSONObject actual,String override)throws Exception{JSONArray cards=expected.getJSONArray("cards"),now=actual.getJSONArray("cards");for(int i=0;i<cards.length();i++){JSONObject before=cards.getJSONObject(i),after=now.getJSONObject(i);if(before.optInt("subId",-1)<0)continue;require(before.getInt("subId")==after.getInt("subId"),"SIM identity changed");require((override==null?before.getString("country"):override).equals(after.getString("country")),"wrong country on slot "+i);}}
    private void restoreChoices(Map<String,?> before){SharedPreferences.Editor edit=app.prefs().edit();for(String key:new String[]{"country","mask",AppAppearance.KEY,"sessionFailure","restorePending"}){edit.remove(key);Object value=before.get(key);if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);}require(edit.commit(),"restore choices");}
    private void run(){Map<String,?> before=new HashMap<>(app.prefs().getAll());JSONObject baseline=null;boolean own=false,passed=false;
        Application.ActivityLifecycleCallbacks callbacks=new Application.ActivityLifecycleCallbacks(){public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){if(a instanceof MainActivity)activity=(MainActivity)a;}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}};
        app.registerActivityLifecycleCallbacks(callbacks);
        try{
            require(!app.active()&&!app.busy,"refusing to alter an existing session");note("WAIT catalog session UI and backend; ADB foreground MainActivity may be needed");
            main(()->context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            waitFor(()->activity!=null,45000,"main missing");waitFor(app::authorized,30000,"backend missing");baseline=inspect();
            main(()->app.saveSelection("nl",3));own=true;main(()->context.startForegroundService(new Intent(context,KeepService.class).setAction(KeepService.START)));
            waitFor(()->app.active()&&!app.busy&&app.message.contains("已核对"),25000,"NL start failed");countries(baseline,inspect(),"nl");note("PASS newly added NL applied to both present SIM slots and read back");
            String snapshot=app.prefs().getString("snapshot",null);MainActivity old=activity;
            main(()->{AppAppearance.set(context,"dark");old.recreate();});waitFor(()->activity!=old&&!app.busy,10000,"active theme recreation");require(snapshot.equals(app.prefs().getString("snapshot",null)),"theme change lost snapshot");countries(baseline,inspect(),"nl");
            main(()->{android.view.View view=activity.getWindow().getDecorView();Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"ui-active-nl-dark.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}});note("PASS active session and backup survive dark-theme recreation");
            main(()->context.startForegroundService(new Intent(context,KeepService.class).setAction(KeepService.STOP)));waitFor(()->!app.active()&&!app.busy,25000,"stop restore failed");countries(baseline,inspect(),null);note("PASS stop restored original country on both slots");passed=true;
        }catch(Throwable e){note("FAIL "+e);}
        finally{try{if(own&&app.active()){main(()->context.startForegroundService(new Intent(context,KeepService.class).setAction(KeepService.STOP)));waitFor(()->!app.active()&&!app.busy,25000,"cleanup restore failed");}if(own&&!app.active()){if(baseline!=null)countries(baseline,inspect(),null);restoreChoices(before);main(()->{context.stopService(new Intent(context,KeepService.class));if(activity!=null)activity.finish();});require(before.equals(app.prefs().getAll()),"unrelated preferences changed");note("PASS original selection, theme and inactive state restored");}}catch(Throwable e){passed=false;note("CLEANUP FAIL; backup retained: "+e);}app.unregisterActivityLifecycleCallbacks(callbacks);Bundle result=new Bundle();result.putString("stream",log.toString());test.finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
    }
}
