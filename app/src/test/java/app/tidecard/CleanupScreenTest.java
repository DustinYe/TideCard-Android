package app.tidecard;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner.class) @Config(sdk={30,34},application=TideApp.class)
public class CleanupScreenTest {
    TideApp app;ActivityController<CleanupActivity> activity;List<String> calls;boolean failClear,oldBackend,partialClear;
    @Before public void setup(){
        app=(TideApp)RuntimeEnvironment.getApplication();calls=Collections.synchronizedList(new ArrayList<>());
        app.bridge=new ICarrierBridge.Stub(){
            public String execute(String cmd,String payload){calls.add(cmd);try{
                if(oldBackend)return new JSONObject().put("ok",false).put("error","未知操作").toString();
                if(cmd.equals("cleanup_preview"))return new JSONObject().put("ok",true).put("canClear",true).put("mask",new JSONObject(payload).getInt("mask")).put("token","reviewed-plan")
                    .put("cards",new JSONArray().put(new JSONObject().put("slot",0).put("present",true).put("needsClear",true).put("country","717").put("originalCountry","cn").put("persistent",true))
                        .put(new JSONObject().put("slot",1).put("present",false))).toString();
                if(cmd.equals("cleanup_clear"))return new JSONObject().put("ok",!failClear).put("error","readback not verified").put("code",partialClear?"cleanup_incomplete":"").put("completedMask",partialClear?1:0).put("cards",new JSONArray()).toString();
                return new JSONObject().put("ok",true).put("cards",new JSONArray()).toString();
            }catch(Exception e){throw new RuntimeException(e);}}
            public void destroy(){}
        };
        app.prefs().edit().putString("country","jp").putString("sessionFailure","old failure").putBoolean("custom-setting",true).commit();
    }
    void flush()throws Exception{app.worker.submit(()->{}).get(5,TimeUnit.SECONDS);shadowOf(Looper.getMainLooper()).idle();}
    void open(){activity=Robolectric.buildActivity(CleanupActivity.class).setup();}
    void preview()throws Exception{app.cleanup.check();flush();}
    @After public void cleanup(){if(activity!=null)activity.pause().stop().destroy();app.worker.shutdownNow();shadowOf(Looper.getMainLooper()).idle();}
    @Test public void checkIsReadOnlyAndConfirmCancelDoesNotClear()throws Exception{
        open();assertFalse(((Button)activity.get().findViewById(R.id.cleanup_clear)).isEnabled());preview();
        assertEquals(Collections.singletonList("cleanup_preview"),calls);assertTrue(((TextView)activity.get().findViewById(R.id.cleanup_report)).getText().toString().contains("717"));
        activity.get().findViewById(R.id.cleanup_clear).performClick();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();flush();assertEquals(Collections.singletonList("cleanup_preview"),calls);assertEquals("old failure",app.prefs().getString("sessionFailure",""));
    }
    @Test public void confirmedCleanupUpdatesCardsAndClearsOnlyFailureState()throws Exception{
        open();preview();activity.get().findViewById(R.id.cleanup_clear).performClick();ShadowAlertDialog.getLatestAlertDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick();flush();
        assertEquals(Arrays.asList("cleanup_preview","cleanup_clear"),calls);assertFalse(app.prefs().contains("sessionFailure"));assertEquals("jp",app.country());assertTrue(app.prefs().getBoolean("custom-setting",false));assertNotNull(app.cards);assertNull(app.cleanup.preview);assertFalse(app.busy);
    }
    @Test public void activeSnapshotAndPendingRecoveryPreventBothCheckAndCleanup()throws Exception{
        preview();app.prefs().edit().putString("snapshot","original backup").commit();app.cleanup.clear("reviewed-plan");app.cleanup.check();flush();
        assertEquals(Collections.singletonList("cleanup_preview"),calls);assertEquals("original backup",app.prefs().getString("snapshot",""));
        app.prefs().edit().remove("snapshot").putBoolean("restorePending",true).commit();app.cleanup.check();flush();assertEquals(1,calls.size());assertTrue(app.prefs().getBoolean("restorePending",false));
    }
    @Test public void staleDialogAndChangedSelectionCannotSubmitPreviousPlan()throws Exception{
        preview();app.cleanup.select(1);app.cleanup.clear("reviewed-plan");flush();assertNull(app.cleanup.preview);assertEquals(Collections.singletonList("cleanup_preview"),calls);
        preview();app.cleanup.clear("wrong-token");flush();assertEquals(2,calls.size());
    }
    @Test public void missingBackendAndBusyStateDoNotCallBackend()throws Exception{
        app.busy=true;app.cleanup.check();assertTrue(calls.isEmpty());app.busy=false;app.bridge=null;app.cleanup.check();flush();assertTrue(calls.isEmpty());assertTrue(app.cleanup.status.contains("激活"));
    }
    @Test public void failureIsVisibleAndNeverDropsExistingPreferences()throws Exception{
        failClear=true;preview();Map<String,?> before=new HashMap<>(app.prefs().getAll());app.cleanup.clear("reviewed-plan");flush();
        assertEquals(before,app.prefs().getAll());assertEquals("readback not verified",app.cleanup.status);assertFalse(app.busy);assertFalse(app.cleanup.canClear());
    }
    @Test public void outdatedBackendExplainsHowToUpgradeRunningService()throws Exception{
        oldBackend=true;preview();assertTrue(app.cleanup.status.contains("重新激活"));assertFalse(app.cleanup.canClear());assertFalse(app.busy);
    }
    @Test public void partialCompletionNamesCompletedSlotAndKeepsFailureState()throws Exception{
        failClear=true;partialClear=true;preview();app.cleanup.clear("reviewed-plan");flush();assertTrue(app.cleanup.status.contains("卡 1"));assertTrue(app.prefs().contains("sessionFailure"));assertFalse(app.cleanup.canClear());
    }
    @Test public void activityRecreationKeepsReviewedPlanInBothThemesAndAllLanguages()throws Exception{
        for(String lang:new String[]{"zh-Hans","zh-Hant","en"})for(String theme:new String[]{"light","dark"}){
            AppLanguage.set(app,lang);AppAppearance.set(app,theme);open();preview();assertTrue(app.cleanup.canClear());
            activity.recreate();assertTrue(((Button)activity.get().findViewById(R.id.cleanup_clear)).isEnabled());assertTrue(((TextView)activity.get().findViewById(R.id.cleanup_report)).getText().toString().contains("717"));
            activity.pause().stop().destroy();activity=null;
        }
        assertFalse(calls.contains("cleanup_clear"));
    }
}
