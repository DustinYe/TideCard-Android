package app.tidecard.test;

import android.app.*;
import android.content.*;
import android.os.*;
import app.tidecard.*;
import app.tidecard.R;
import org.json.*;

/** Regression for missing app backup with a live three-key override, including Stop restoration. */
final class ReinstallRecoveryChecks {
    private final Instrumentation test;private final Context context;private final TideApp app;
    private volatile MainActivity activity;private final StringBuilder log=new StringBuilder();
    private ReinstallRecoveryChecks(Instrumentation test){this.test=test;context=test.getTargetContext();app=(TideApp)context.getApplicationContext();}
    static void run(Instrumentation test){new ReinstallRecoveryChecks(test).run();}
    private interface Condition{boolean ok()throws Exception;}
    private void waitFor(Condition c,long ms,String message)throws Exception{long end=System.currentTimeMillis()+ms;while(System.currentTimeMillis()<end){if(c.ok())return;Thread.sleep(200);}throw new AssertionError(message+": "+app.message);}
    private void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private void note(String value){log.append(value).append('\n');Bundle b=new Bundle();b.putString("stream",value+"\n");test.sendStatus(0,b);}
    private void main(Runnable task){test.runOnMainSync(task);}
    private JSONObject inspect()throws Exception{return app.call("inspect",new JSONObject());}
    private void countries(String iso)throws Exception{JSONArray cards=inspect().getJSONArray("cards");for(int slot=0;slot<cards.length();slot++)if(cards.getJSONObject(slot).optInt("subId",-1)>=0)require(iso.equals(cards.getJSONObject(slot).getString("country")),"country mismatch at slot "+slot);}
    private void original(JSONObject snapshot,String iso)throws Exception{JSONArray cards=snapshot.getJSONArray("cards");require(cards.length()==2,"expected two SIMs for device regression");for(int slot=0;slot<cards.length();slot++){JSONObject card=cards.getJSONObject(slot);require(iso.equals(card.getString("country")),"overridden country captured as original");require(!card.getBoolean("nameFlag"),"overridden name flag captured as original");require(!"T-Mobile".equals(card.getString("name")),"overridden name captured as original");}}
    private void toggle(){main(()->activity.findViewById(R.id.toggle).performClick());}
    private void ready()throws Exception{waitFor(()->app.active()&&!app.busy&&app.message.contains("已核对"),25000,"start failed");}
    private void stopped()throws Exception{waitFor(()->!app.active()&&!app.busy,20000,"stop failed");}
    private void run(){boolean passed=false;String pairedBefore=String.valueOf(app.prefs().getBoolean("paired",false));String tokenBefore=app.prefs().getString("bridgeToken",null);
        Application.ActivityLifecycleCallbacks listener=new Application.ActivityLifecycleCallbacks(){
            public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){if(a instanceof MainActivity)activity=(MainActivity)a;}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
        };app.registerActivityLifecycleCallbacks(listener);
        try{
            main(()->context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            note("WAIT main activity; use ADB foreground launch if HyperOS blocks the test UI");
            waitFor(()->activity!=null,45000,"main activity missing");waitFor(app::authorized,45000,"backend not ready");
            require(!app.active(),"this regression requires the already reinstalled app with no backup");
            countries("us");JSONObject candidate=app.call("prepare",new JSONObject().put("mask",3));
            require(candidate.getBoolean("recoveredExisting"),"existing session not identified");original(candidate,"cn");countries("us");
            require(!app.active(),"read-only prepare unexpectedly saved app session");note("PASS actual reinstall residual accepted; original CN backup rebuilt without changing live US settings");
            main(()->app.saveSelection("us",3));toggle();ready();original(new JSONObject(app.prefs().getString("snapshot","{}")),"cn");countries("us");
            toggle();stopped();countries("cn");note("PASS real Start/Stop after reinstall restores both cards to CN");
            toggle();ready();countries("us");
            main(()->context.stopService(new Intent(context,KeepService.class)));Thread.sleep(300);require(!app.busy,"worker busy before simulated backup loss");
            main(()->{app.prefs().edit().remove("snapshot").remove("restorePending").commit();app.changed();});
            countries("us");toggle();ready();require(new JSONObject(app.prefs().getString("snapshot","{}")).optBoolean("recoveredExisting"),"repeated backup loss not recovered");
            original(new JSONObject(app.prefs().getString("snapshot","{}")),"cn");toggle();stopped();countries("cn");note("PASS repeated missing-backup session can be adopted and stopped again");
            toggle();ready();countries("us");original(new JSONObject(app.prefs().getString("snapshot","{}")),"cn");
            require(pairedBefore.equals(String.valueOf(app.prefs().getBoolean("paired",false)))&&java.util.Objects.equals(tokenBefore,app.prefs().getString("bridgeToken",null)),"pairing identity changed");
            note("PASS final dual-US session retained with a valid CN restore backup; pairing identity unchanged");passed=true;
        }catch(Throwable error){note("FAIL "+error);}
        finally{app.unregisterActivityLifecycleCallbacks(listener);Bundle result=new Bundle();result.putString("stream",log.toString());test.finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
    }
}
