package app.tidecard;
import android.app.Application;
import android.net.nsd.*;
import android.os.*;
import java.net.*;
import java.time.Duration;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner.class) @Config(sdk={30,34},application=Application.class)
public class WirelessDiscoveryTest {
    static class Api implements LocalDiscovery.Api {
        Map<String,NsdManager.DiscoveryListener> listeners=new HashMap<>();List<NsdManager.ResolveListener> requests=new ArrayList<>();int cancels,stops;
        public void discover(String type,NsdManager.DiscoveryListener listener){listeners.put(type,listener);}
        public void stop(NsdManager.DiscoveryListener listener){stops++;}
        public void resolve(NsdServiceInfo info,NsdManager.ResolveListener listener){requests.add(listener);}
        public void cancel(NsdManager.ResolveListener listener){cancels++;}
    }
    Api api;LocalDiscovery discovery;List<Integer> found;int errors;
    @Before public void setup(){api=new Api();found=new ArrayList<>();discovery=new LocalDiscovery(api,new Handler(Looper.getMainLooper()),new LocalDiscovery.Callback(){public void found(boolean p,int port){found.add(port);}public void lost(boolean p,int port){}public void error(String text){errors++;}});discovery.start();}
    @After public void cleanup(){discovery.close();}
    NsdServiceInfo service(String name,int port,String host)throws Exception{NsdServiceInfo s=new NsdServiceInfo();s.setServiceName(name);s.setServiceType("_adb-tls-pairing._tcp.");s.setPort(port);s.setHost(InetAddress.getByName(host));return s;}
    void announce(NsdServiceInfo s){api.listeners.get(s.getServiceType()).onServiceFound(s);shadowOf(Looper.getMainLooper()).idle();}
    @Test public void resolvesSequentiallyAndDeduplicates()throws Exception{
        var a=service("a",12345,"127.0.0.1");var b=service("b",23456,"127.0.0.1");announce(a);announce(a);announce(b);assertEquals(1,api.requests.size());
        api.requests.get(0).onServiceResolved(a);shadowOf(Looper.getMainLooper()).idle();assertEquals(List.of(12345),found);assertEquals(2,api.requests.size());
        api.requests.get(1).onServiceResolved(b);shadowOf(Looper.getMainLooper()).idle();assertEquals(List.of(12345,23456),found);
    }
    @Test public void stuckResolutionTimesOutAndLateCallbackIsIgnored()throws Exception{
        var a=service("a",12345,"127.0.0.1");var b=service("b",23456,"127.0.0.1");announce(a);announce(b);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(8));assertEquals(1,api.cancels);assertEquals(2,api.requests.size());
        api.requests.get(0).onServiceResolved(a);api.requests.get(1).onServiceResolved(b);shadowOf(Looper.getMainLooper()).idle();assertEquals(List.of(23456),found);
    }
    @Test public void failureRetriesOnlyTwice()throws Exception{
        var a=service("a",12345,"127.0.0.1");announce(a);api.requests.get(0).onResolveFailed(a,0);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));assertEquals(2,api.requests.size());
        api.requests.get(1).onResolveFailed(a,0);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20));assertEquals(2,api.requests.size());assertEquals(1,errors);
    }
    @Test public void acceptsAnyLocalAddressInMultiAddressService()throws Exception{
        if(Build.VERSION.SDK_INT<34)return;var a=service("multi",34567,"192.0.2.1");a.setHostAddresses(List.of(InetAddress.getByName("192.0.2.1"),InetAddress.getByName("127.0.0.1")));announce(a);api.requests.get(0).onServiceResolved(a);shadowOf(Looper.getMainLooper()).idle();assertEquals(List.of(34567),found);
    }
    @Test public void lostClosedAndForeignServicesAreNotAccepted()throws Exception{
        var a=service("a",12345,"127.0.0.1");announce(a);api.listeners.get(a.getServiceType()).onServiceLost(a);api.requests.get(0).onServiceResolved(a);shadowOf(Looper.getMainLooper()).idle();assertTrue(found.isEmpty());
        var remote=service("remote",12345,"192.0.2.1");announce(remote);api.requests.get(1).onServiceResolved(remote);shadowOf(Looper.getMainLooper()).idle();assertTrue(found.isEmpty());
        var c=service("c",34567,"127.0.0.1");announce(c);discovery.close();api.requests.get(2).onServiceResolved(c);shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30));assertTrue(found.isEmpty());assertEquals(2,api.stops);
    }
}
