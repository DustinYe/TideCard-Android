/* SPDX-License-Identifier: Apache-2.0
 * TideCard adaptation, 2026: local-only Java implementation and application integration.
 * Protocol / pairing reference: RikkaApps/Shizuku v13.6.0 (RikkaApps contributors).
 * See NOTICE and THIRD_PARTY.md for source links and modification details.
 */
package app.tidecard;
import android.content.Context;
import android.net.nsd.*;
import android.net.NetworkRequest;
import android.net.NetworkCapabilities;
import android.os.*;
import java.net.*;
import java.util.*;

final class LocalDiscovery {
    interface Callback{void found(boolean pairing,int port);void lost(boolean pairing,int port);void error(String message);}
    interface Api{
        void discover(String type,NsdManager.DiscoveryListener listener);
        void stop(NsdManager.DiscoveryListener listener);
        void resolve(NsdServiceInfo info,NsdManager.ResolveListener listener);
        void cancel(NsdManager.ResolveListener listener);
    }
    private static final class Platform implements Api{
        private final NsdManager manager;private final java.util.concurrent.Executor executor;Platform(Context c){manager=c.getSystemService(NsdManager.class);executor=c.getMainExecutor();}
        public void discover(String type,NsdManager.DiscoveryListener l){
            // Select Wi-Fi on newer systems instead of depending on the default VPN network.
            if(Build.VERSION.SDK_INT>=33){
                try{manager.discoverServices(type,NsdManager.PROTOCOL_DNS_SD,new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(),executor,l);return;}
                catch(UnsupportedOperationException ignored){}
            }
            manager.discoverServices(type,NsdManager.PROTOCOL_DNS_SD,l);
        }
        public void stop(NsdManager.DiscoveryListener l){manager.stopServiceDiscovery(l);}
        public void resolve(NsdServiceInfo s,NsdManager.ResolveListener l){manager.resolveService(s,l);}
        public void cancel(NsdManager.ResolveListener l){if(Build.VERSION.SDK_INT>=34)manager.stopServiceResolution(l);}
    }
    private final Api api;private final Callback callback;private final Handler main;
    private final ArrayList<NsdManager.DiscoveryListener> listeners=new ArrayList<>();
    private final DiscoveryQueue<NsdServiceInfo> queue=new DiscoveryQueue<>();
    private final HashMap<String,Integer> ports=new HashMap<>(),attempts=new HashMap<>();
    private final HashSet<String> lost=new HashSet<>();
    private boolean closed;private NsdManager.ResolveListener active;private Runnable watchdog;
    private String key(NsdServiceInfo s){return s.getServiceType()+":"+s.getServiceName();}
    private void error(){if(!closed)callback.error("自动发现暂不可用，可在通知中手动输入系统当前显示的端口");}
    LocalDiscovery(Context c,Callback cb){this(new Platform(c),new Handler(Looper.getMainLooper()),cb);}
    LocalDiscovery(Api api,Handler main,Callback cb){this.api=api;this.main=main;callback=cb;}
    void start(){discover("_adb-tls-pairing._tcp.");discover("_adb-tls-connect._tcp.");}
    private void discover(String type){
        NsdManager.DiscoveryListener listener=new NsdManager.DiscoveryListener(){
            public void onDiscoveryStarted(String t){}public void onDiscoveryStopped(String t){}public void onStopDiscoveryFailed(String t,int e){}
            public void onStartDiscoveryFailed(String t,int e){main.post(()->error());}
            public void onServiceLost(NsdServiceInfo info){main.post(()->{if(closed)return;String k=key(info);lost.add(k);queue.lost(k);attempts.remove(k);Integer port=ports.remove(k);if(port!=null)callback.lost(info.getServiceType().contains("pairing"),port);});}
            public void onServiceFound(NsdServiceInfo info){main.post(()->{if(closed)return;String k=key(info);lost.remove(k);if(!ports.containsKey(k)&&!attempts.containsKey(k))attempts.put(k,0);queue.offer(k,info);next();});}
        };listeners.add(listener);try{api.discover(type,listener);}catch(Exception e){error();}
    }
    private void next(){
        if(closed)return;DiscoveryQueue.Ticket<NsdServiceInfo> ticket=queue.next();if(ticket==null)return;
        if(lost.contains(ticket.key)){queue.finish(ticket);next();return;}
        attempts.put(ticket.key,attempts.getOrDefault(ticket.key,0)+1);
        NsdManager.ResolveListener listener=new NsdManager.ResolveListener(){
            public void onResolveFailed(NsdServiceInfo s,int e){main.post(()->complete(ticket,null));}
            public void onServiceResolved(NsdServiceInfo s){main.post(()->complete(ticket,s));}
            public void onResolutionStopped(NsdServiceInfo s){}
            public void onStopResolutionFailed(NsdServiceInfo s,int error){}
        };
        active=listener;watchdog=()->{
            if(closed||active!=listener)return;
            try{api.cancel(listener);}catch(Exception ignored){}
            complete(ticket,null);
        };main.postDelayed(watchdog,8000);
        try{api.resolve(ticket.value,listener);}catch(RuntimeException e){complete(ticket,null);}
    }
    private void complete(DiscoveryQueue.Ticket<NsdServiceInfo> ticket,NsdServiceInfo service){
        if(closed||!queue.finish(ticket))return;
        if(watchdog!=null)main.removeCallbacks(watchdog);active=null;watchdog=null;
        if(!lost.contains(ticket.key)){
            if(service!=null){try{
                int port=service.getPort();
                if(port>0&&port<=65535&&isLocal(service)){
                    ports.put(ticket.key,port);attempts.remove(ticket.key);callback.found(service.getServiceType().contains("pairing"),port);
                }
            }catch(Exception e){error();}}
            else if(attempts.getOrDefault(ticket.key,0)<2){main.postDelayed(()->{if(!closed&&!lost.contains(ticket.key)){queue.offer(ticket.key,ticket.value);next();}},1000);}
            else error();
        }
        next();
    }
    private static boolean isLocal(NsdServiceInfo service)throws SocketException{
        List<InetAddress> hosts=Build.VERSION.SDK_INT>=34?service.getHostAddresses():Collections.emptyList();
        if(hosts.isEmpty()&&service.getHost()!=null)hosts=Collections.singletonList(service.getHost());
        for(InetAddress host:hosts)if(host.isLoopbackAddress()||NetworkInterface.getByInetAddress(host)!=null)return true;
        return false;
    }
    void close(){
        closed=true;if(watchdog!=null)main.removeCallbacks(watchdog);if(active!=null)try{api.cancel(active);}catch(Exception ignored){}
        for(NsdManager.DiscoveryListener listener:listeners)try{api.stop(listener);}catch(Exception ignored){}
        queue.clear();ports.clear();attempts.clear();lost.clear();
    }
}
