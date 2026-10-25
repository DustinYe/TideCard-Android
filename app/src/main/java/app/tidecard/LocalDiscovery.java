/* SPDX-License-Identifier: Apache-2.0
 * TideCard adaptation, 2026: local-only Java implementation and application integration.
 * Protocol / pairing reference: RikkaApps/Shizuku v13.6.0 (RikkaApps contributors).
 * See NOTICE and THIRD_PARTY.md for source links and modification details.
 */
package app.tidecard;
import android.content.Context;
import android.net.nsd.*;
import android.os.*;
import java.net.*;
import java.util.*;

final class LocalDiscovery {
    interface Callback{void found(boolean pairing,int port);void lost(boolean pairing,int port);void error(String message);}
    private final NsdManager manager;private final Callback callback;private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayList<NsdManager.DiscoveryListener> listeners=new ArrayList<>();private final ArrayDeque<NsdServiceInfo> queue=new ArrayDeque<>();private boolean resolving,closed;
    private final HashMap<String,Integer> ports=new HashMap<>();private final HashSet<String> lost=new HashSet<>();
    private String key(NsdServiceInfo s){return s.getServiceType()+":"+s.getServiceName();}
    private void error(String message){main.post(()->{if(!closed)callback.error(message);});}
    LocalDiscovery(Context c,Callback cb){manager=c.getSystemService(NsdManager.class);callback=cb;}
    void start(){discover("_adb-tls-pairing._tcp.");discover("_adb-tls-connect._tcp.");}
    private void discover(String type){
        NsdManager.DiscoveryListener listener=new NsdManager.DiscoveryListener(){
            public void onDiscoveryStarted(String t){}public void onDiscoveryStopped(String t){}public void onStopDiscoveryFailed(String t,int e){}
            public void onStartDiscoveryFailed(String t,int e){error("自动发现暂不可用，可手动输入系统显示的端口");}
            public void onServiceLost(NsdServiceInfo info){main.post(()->{if(closed)return;String k=key(info);lost.add(k);queue.removeIf(s->key(s).equals(k));Integer port=ports.remove(k);if(port!=null)callback.lost(info.getServiceType().contains("pairing"),port);});}
            public void onServiceFound(NsdServiceInfo info){main.post(()->{if(!closed){lost.remove(key(info));queue.add(info);next();}});}
        };listeners.add(listener);try{manager.discoverServices(type,NsdManager.PROTOCOL_DNS_SD,listener);}catch(Exception e){error("请连接 Wi-Fi 并开启无线调试");}
    }
    private void next(){if(closed||resolving||queue.isEmpty())return;resolving=true;NsdServiceInfo info=queue.remove();
        try{manager.resolveService(info,new NsdManager.ResolveListener(){
            public void onResolveFailed(NsdServiceInfo s,int e){main.post(()->{resolving=false;next();});}
            public void onServiceResolved(NsdServiceInfo s){main.post(()->{try{InetAddress host=s.getHost();if(!closed&&!lost.contains(key(info))&&host!=null&&(host.isLoopbackAddress()||NetworkInterface.getByInetAddress(host)!=null)){ports.put(key(info),s.getPort());callback.found(s.getServiceType().contains("pairing"),s.getPort());}}catch(Exception ignored){}resolving=false;next();});}
        });}catch(RuntimeException e){resolving=false;next();}
    }
    void close(){closed=true;for(NsdManager.DiscoveryListener listener:listeners)try{manager.stopServiceDiscovery(listener);}catch(Exception ignored){}queue.clear();}
}
