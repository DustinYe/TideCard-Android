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
    interface Callback{void found(boolean pairing,int port);void error(String message);}
    private final NsdManager manager;private final Callback callback;private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayList<NsdManager.DiscoveryListener> listeners=new ArrayList<>();private final ArrayDeque<NsdServiceInfo> queue=new ArrayDeque<>();private boolean resolving,closed;
    LocalDiscovery(Context c,Callback cb){manager=c.getSystemService(NsdManager.class);callback=cb;}
    void start(){discover("_adb-tls-pairing._tcp.");discover("_adb-tls-connect._tcp.");}
    private void discover(String type){
        NsdManager.DiscoveryListener listener=new NsdManager.DiscoveryListener(){
            public void onDiscoveryStarted(String t){}public void onDiscoveryStopped(String t){}public void onStopDiscoveryFailed(String t,int e){}
            public void onStartDiscoveryFailed(String t,int e){callback.error("自动发现暂不可用，可手动输入系统显示的端口");}
            public void onServiceLost(NsdServiceInfo info){}
            public void onServiceFound(NsdServiceInfo info){main.post(()->{if(!closed){queue.add(info);next();}});}
        };listeners.add(listener);try{manager.discoverServices(type,NsdManager.PROTOCOL_DNS_SD,listener);}catch(Exception e){callback.error("请连接 Wi-Fi 并开启无线调试");}
    }
    private void next(){if(closed||resolving||queue.isEmpty())return;resolving=true;NsdServiceInfo info=queue.remove();
        manager.resolveService(info,new NsdManager.ResolveListener(){
            public void onResolveFailed(NsdServiceInfo s,int e){main.post(()->{resolving=false;next();});}
            public void onServiceResolved(NsdServiceInfo s){main.post(()->{try{InetAddress host=s.getHost();if(host!=null&&(host.isLoopbackAddress()||NetworkInterface.getByInetAddress(host)!=null))callback.found(s.getServiceType().contains("pairing"),s.getPort());}catch(Exception ignored){}resolving=false;next();});}
        });
    }
    void close(){closed=true;for(NsdManager.DiscoveryListener listener:listeners)try{manager.stopServiceDiscovery(listener);}catch(Exception ignored){}queue.clear();}
}
