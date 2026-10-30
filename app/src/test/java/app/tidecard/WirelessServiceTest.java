package app.tidecard;
import android.app.*;
import android.content.*;
import android.os.*;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner.class) @Config(sdk={30,34},application=TideApp.class)
public class WirelessServiceTest {
    TideApp app;ActivationService service;ServiceController<ActivationService> controller;CountDownLatch release;
    @Before public void setup(){app=(TideApp)RuntimeEnvironment.getApplication();controller=Robolectric.buildService(ActivationService.class).create();service=controller.get();service.onStartCommand(new Intent(app,ActivationService.class).setAction(ActivationService.SCAN),0,1);shadowOf(Looper.getMainLooper()).idle();}
    @After public void cleanup(){if(controller!=null)controller.destroy();if(release!=null)release.countDown();app.worker.shutdownNow();shadowOf(Looper.getMainLooper()).idle();}
    Object field(String name)throws Exception{Field f=ActivationService.class.getDeclaredField(name);f.setAccessible(true);return f.get(service);}
    String session()throws Exception{return (String)field("session");}
    void blockWorker()throws Exception{release=new CountDownLatch(1);CountDownLatch entered=new CountDownLatch(1);app.worker.execute(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(entered.await(3,TimeUnit.SECONDS));}
    void disposeBeforeWorkerCanUseNetwork()throws Exception{controller.destroy();controller=null;release.countDown();app.worker.submit(()->{}).get(3,TimeUnit.SECONDS);}
    @Test public void waitingNotificationHasFallbackAndHintAfterTenSeconds(){assertEquals(0,ActivationService.pairingPort);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10));assertTrue(ActivationService.status.contains("手动输入"));assertFalse(ActivationService.working);}
    @Test public void staleAndWrongPhaseActionsDoNothing()throws Exception{
        String before=ActivationService.status;ActivationService.receiveManual("old-session","12345 987654",false);ActivationService.receiveManual(session(),"38213",true);ActivationService.retry("old-session");assertEquals(before,ActivationService.status);assertEquals(0,ActivationService.pairingPort);assertFalse(ActivationService.working);
    }
    @Test public void malformedManualInputDoesNotLeakCodeOrStartNetwork()throws Exception{
        Map<String,?> prefs=new HashMap<>(app.prefs().getAll());ActivationService.receiveManual(session(),"127.0.0.1:12345 987654",false);assertFalse(ActivationService.working);assertFalse(ActivationService.status.contains("987654"));assertEquals(prefs,app.prefs().getAll());
    }
    @Test public void noDiscoveryCanSubmitManualPairThroughReceiver()throws Exception{
        blockWorker();Map<String,?> prefs=new HashMap<>(app.prefs().getAll());String session=session();
        Intent intent=new Intent(app,PairingReceiver.class).setAction(PairingReceiver.MANUAL).putExtra("session",session);Bundle input=new Bundle();input.putCharSequence("manualPair","39461 001234");RemoteInput.addResultsToIntent(new RemoteInput[]{new RemoteInput.Builder("manualPair").setLabel("test").build()},intent,input);
        new PairingReceiver().onReceive(app,intent);assertTrue(ActivationService.working);assertEquals(39461,ActivationService.pairingPort);assertTrue((boolean)field("manualPairPort"));assertFalse(ActivationService.status.contains("001234"));assertEquals(prefs,app.prefs().getAll());
        disposeBeforeWorkerCanUseNetwork();assertEquals(prefs,app.prefs().getAll());
    }
    @Test public void afterPairingMissingConnectionCanBeFilledWithoutDiscovery()throws Exception{
        blockWorker();var method=ActivationService.class.getDeclaredMethod("paired");method.setAccessible(true);method.invoke(service);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2100));assertFalse((boolean)field("pairingSession"));assertEquals(0,ActivationService.connectionPort);
        ActivationService.receiveManual(session(),"38213",true);assertEquals(38213,ActivationService.connectionPort);assertTrue(ActivationService.working);assertTrue((boolean)field("manualConnectionPort"));disposeBeforeWorkerCanUseNetwork();
    }
    @Test public void openingManualInputWithNoDiscoveredPortIsAllowed()throws Exception{ActivationService.receive(session(),null,true);assertTrue((boolean)field("inputMode"));assertTrue(ActivationService.status.contains("手动配对"));}
    @Test public void manualLiveEntryStillExplainsBothFieldsWhenAutoPortExists()throws Exception{ActivationService.pairingPort=12345;ActivationService.openManual(session());assertTrue(ActivationService.status.contains("手动配对"));assertTrue((boolean)field("inputMode"));}
    @Test public void retryAndFreshScanResetAutomaticState()throws Exception{
        ActivationService.pairingPort=11111;ActivationService.connectionPort=22222;String session=session();Object old=field("discovery");ActivationService.retry(session);assertNotSame(old,field("discovery"));assertEquals(0,ActivationService.pairingPort);assertEquals(0,ActivationService.connectionPort);
        service.onStartCommand(new Intent(app,ActivationService.class).setAction(ActivationService.SCAN),0,2);assertNotEquals(session,session());
    }
}
