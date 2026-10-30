package app.tidecard;
import android.app.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import static org.junit.Assert.*;

@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner.class) @Config(sdk={30,34},application=Application.class)
public class WirelessNotificationTest {
    Application app;
    @Before public void setup(){app=RuntimeEnvironment.getApplication();I18n.init(app);PairingNotifications.createChannel(app);}
    Notification build(int port,boolean pairing,boolean working,boolean success){return PairingNotifications.build(app,"test-session","test status",port,pairing,true,working,success,0);}
    @Test public void missingDiscoveryStillHasManualInput(){var n=build(0,true,false,false);assertEquals(3,n.actions.length);assertEquals("manualPair",n.actions[0].getRemoteInputs()[0].getResultKey());assertTrue(org.robolectric.Shadows.shadowOf(n.actions[0].actionIntent).isBroadcast());assertFalse(org.robolectric.Shadows.shadowOf(n.actions[0].actionIntent).isActivity());assertFalse(n.extras.getBoolean(PairingNotifications.PROMOTE));}
    @Test public void discoveredPortKeepsCodeAndManualFallback(){var n=build(12345,true,false,false);assertEquals(3,n.actions.length);assertEquals("pairCode",n.actions[0].getRemoteInputs()[0].getResultKey());assertEquals("manualPair",n.actions[1].getRemoteInputs()[0].getResultKey());}
    @Test public void afterPairingCanEnterConnectionPort(){var n=build(0,false,false,false);assertEquals("connectPort",n.actions[0].getRemoteInputs()[0].getResultKey());assertFalse(n.extras.getBoolean(PairingNotifications.PROMOTE));}
    @Test public void manualLiveActionDoesNotCancelTheNormalOpenAction()throws Exception{
        PendingIntent normal=PairingNotifications.input(app,"test-session",false);
        var method=PairingNotifications.class.getDeclaredMethod("manual",android.content.Context.class,String.class,boolean.class,boolean.class);method.setAccessible(true);
        var manual=(Notification.Action)method.invoke(null,app,"test-session",false,true);assertNull(manual.getRemoteInputs());normal.send();manual.actionIntent.send();
    }
    @Test public void statusRefreshKeepsInputIntentAndSessionsRemainSeparate()throws Exception{
        var first=build(0,true,false,false);var second=build(0,true,false,false);assertEquals(first.actions[0].actionIntent,second.actions[0].actionIntent);first.actions[0].actionIntent.send();
        var other=PairingNotifications.build(app,"other-session","test",0,true,true,false,false,0);assertNotEquals(first.actions[0].actionIntent,other.actions[0].actionIntent);
    }
    @Test public void busyAndSuccessHaveNoEditableActions(){for(boolean success:new boolean[]{false,true}){var n=build(12345,true,!success,success);assertEquals(1,n.actions.length);assertNull(n.actions[0].getRemoteInputs());}}
}
