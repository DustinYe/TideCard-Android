package app.tidecard;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import app.tidecard.adb.*;
import java.util.UUID;

public class ActivationService extends Service {
    public static final String SCAN="app.tidecard.SCAN",CONNECT="app.tidecard.CONNECT",PAIR="app.tidecard.PAIR",CANCEL="app.tidecard.CANCEL";
    public static volatile int pairingPort,connectionPort;
    public static volatile boolean working;
    public static volatile String status="首次使用，请按步骤激活";
    static ActivationService current;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TideApp app;private LocalDiscovery discovery;private AdbIdentity identity;
    private volatile boolean alive;private volatile AdbClient client;
    private boolean requested,pairingSession,inputMode,successFeedback,manualPairPort,manualConnectionPort;private int successFrame;private String session;
    private final Runnable discoveryHint=()->{if(alive&&!working&&!successFeedback){if(pairingSession&&pairingPort==0)status("尚未发现配对服务。保持系统配对弹窗打开，可在通知中手动输入配对端口和六位码；也可暂时关闭 VPN 后重新搜索。");else if(requested&&connectionPort==0)status("配对后未发现连接端口，请在通知中输入无线调试主页的端口");}};
    private final Runnable timeout=()->finish("激活已超时，请重新开始配对");
    private final SharedPreferences.OnSharedPreferenceChangeListener options=(prefs,key)->{if(NotificationOptions.PAIRING_LIVE.equals(key))publish();};
    @Override public void onCreate(){
        super.onCreate();app=(TideApp)getApplication();alive=true;current=this;
        session=UUID.randomUUID().toString();pairingPort=0;connectionPort=0;working=false;
        PairingNotifications.createChannel(this);
        app.prefs().registerOnSharedPreferenceChangeListener(options);
    }
    private Notification notification(){return PairingNotifications.build(this,session,status,pairingPort,pairingSession,inputMode,working,successFeedback,successFrame);}
    private void publish(){if(!alive)return;app.changed();getSystemService(NotificationManager.class).notify(22,notification());}
    private void status(String text){if(!alive)return;status=text;publish();}
    private void armTimeout(){handler.removeCallbacks(timeout);handler.postDelayed(timeout,180000);}
    private void finish(String text){if(!alive)return;status=text;app.changed();stopSelf();}
    static void receive(String session,String code,boolean open){
        ActivationService s=current;
        if(s==null||!s.alive||!s.session.equals(session)||!s.pairingSession||s.successFeedback||s.working)return;
        if(open)s.openInput(false);else if(pairingPort>0)s.pair(code,pairingPort);else s.status("尚未发现配对服务，请使用通知中的手动配对");
    }
    static void openManual(String session){
        ActivationService s=current;if(s==null||!s.alive||!s.session.equals(session)||!s.pairingSession||s.working||s.successFeedback)return;
        s.openInput(true);
    }
    static void receiveManual(String session,String raw,boolean connect){
        ActivationService s=current;
        if(s==null||!s.alive||!s.session.equals(session)||s.working||s.successFeedback||connect==s.pairingSession)return;
        s.armTimeout();s.inputMode=true;
        try{
            if(connect){connectionPort=PairingInput.port(raw);s.manualConnectionPort=true;s.launch();}
            else{PairingInput.Pair input=PairingInput.pair(raw);pairingPort=input.port();s.manualPairPort=true;s.pair(input.code(),input.port());}
        }catch(IllegalArgumentException e){s.status(e.getMessage());}
    }
    static void retry(String session){
        ActivationService s=current;if(s==null||!s.alive||!s.session.equals(session)||s.working||s.successFeedback)return;
        if(s.discovery!=null)s.discovery.close();s.discovery=null;
        if(!s.manualPairPort)pairingPort=0;if(!s.manualConnectionPort)connectionPort=0;
        s.armTimeout();s.startDiscovery();s.status("正在重新搜索；也可在通知中手动填写系统当前显示的端口");
    }
    private void startDiscovery(){
        handler.removeCallbacks(discoveryHint);handler.postDelayed(discoveryHint,10000);
        if(discovery!=null)return;
        discovery=new LocalDiscovery(this,new LocalDiscovery.Callback(){
            public void found(boolean pairing,int port){
                if(!alive)return;
                if(pairing){if(!manualPairPort)pairingPort=port;if(pairingSession&&!working&&!successFeedback&&!inputMode)status("已发现配对窗口，请保持窗口打开并在通知中输入");}
                else{if(!manualConnectionPort)connectionPort=port;if(requested&&!successFeedback)launch();}
            }
            public void lost(boolean pairing,int port){
                if(!alive)return;
                if(pairing&&pairingPort==port&&!manualPairPort){pairingPort=0;if(!working&&!successFeedback){inputMode=true;status("配对服务暂不可见，可在通知中手动输入系统配对弹窗的端口和六位码");}}
                else if(!pairing&&connectionPort==port&&!manualConnectionPort)connectionPort=0;
            }
            public void error(String message){if(!working&&!successFeedback)status(message);}
        });discovery.start();
    }
    @android.annotation.SuppressLint("WrongConstant") // Optional hidden statusbar service; failure keeps manual shade input usable.
    private void openInput(boolean manual){
        // Android deliberately hides RemoteInput in promoted notifications. Demote this
        // notification before asking SystemUI to display its own protected input UI.
        inputMode=true;status(pairingPort>0&&!manual?"请展开本条通知，再点「输入六位码」；保持系统配对窗口打开":"请展开本条通知，点「手动配对」，输入配对端口和六位码，中间用空格分隔");
        try{Object bar=getSystemService("statusbar");bar.getClass().getMethod("expandNotificationsPanel").invoke(bar);}
        catch(Exception ignored){android.widget.Toast.makeText(this,I18n.tr(pairingPort>0&&!manual?"请下拉通知栏，点「输入六位码」":"请下拉通知栏，点「手动配对」"),android.widget.Toast.LENGTH_LONG).show();}
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        String action=intent==null?SCAN:intent.getAction();
        if(CANCEL.equals(action)){if(!session.equals(intent.getStringExtra("session")))return START_NOT_STICKY;finish("已取消激活");return START_NOT_STICKY;}
        if(working||successFeedback)return START_NOT_STICKY;
        if(SCAN.equals(action)&&!working){
            pairingSession=true;inputMode=false;successFeedback=false;requested=false;successFrame=0;
            session=UUID.randomUUID().toString();pairingPort=0;connectionPort=0;manualPairPort=false;manualConnectionPort=false;if(discovery!=null)discovery.close();discovery=null;status="请打开系统配对弹窗；通知内可直接手动填写配对端口和六位码";armTimeout();
        }
        if(Build.VERSION.SDK_INT>=34)startForeground(22,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(22,notification());
        if(app.authorized()&&CONNECT.equals(action)){finish("内置后台已激活");return START_NOT_STICKY;}
        startDiscovery();
        if(PAIR.equals(action)){pairingPort=intent.getIntExtra("port",pairingPort);manualPairPort=true;pair(intent.getStringExtra("code"),pairingPort);}
        else if(CONNECT.equals(action)){
            pairingSession=false;requested=true;armTimeout();int manual=intent.getIntExtra("port",0);if(manual>0){connectionPort=manual;manualConnectionPort=true;}
            if(connectionPort>0)launch();else status("请开启无线调试，正在寻找本机连接端口…");
        }else publish();
        return START_NOT_STICKY;
    }
    private AdbIdentity identity()throws Exception{if(identity==null)identity=new AdbIdentity(this);return identity;}
    private void pair(String code,int port){
        if(!alive||working||successFeedback)return;String value=code==null?"":code.trim();
        if(!value.matches("[0-9]{6}")||port<1||port>65535){status("请输入当前系统配对窗口中的六位数字");return;}
        pairingSession=true;working=true;armTimeout();
        status("正在配对，请保持系统配对窗口打开…");
        app.worker.execute(()->{try{
            if(!alive)return;
            PairingClient.pair(port,value,identity());if(!alive)return;
            app.prefs().edit().putBoolean("paired",true).commit();handler.post(()->{if(alive)paired();});
        }catch(Throwable e){handler.post(()->{if(!alive)return;working=false;inputMode=true;status("配对失败："+readable(e)+"。请保持窗口打开，输入当前六位码重试。");});}});
    }
    private void paired(){
        working=false;inputMode=false;successFeedback=true;requested=true;successFrame=0;status="配对成功";publish();
        for(int frame=1;frame<=3;frame++){final int f=frame;handler.postDelayed(()->{if(!alive)return;successFrame=f;publish();},frame*260L);}
        handler.postDelayed(()->{
            if(!alive)return;
            // End promotion after feedback even if backend activation still needs time.
            pairingSession=false;successFeedback=false;stopForeground(STOP_FOREGROUND_REMOVE);
            getSystemService(NotificationManager.class).cancel(22);
            if(app.authorized()){finish("配对成功，内置后台已就绪");return;}
            status="配对成功，正在启动内置后台…";
            if(Build.VERSION.SDK_INT>=34)startForeground(22,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(22,notification());
            if(connectionPort>0)launch();else{status("配对成功；请在通知中输入无线调试主页的连接端口");handler.postDelayed(discoveryHint,10000);}
        },2000);
    }
    private void launch(){
        if(!alive||working||successFeedback||connectionPort==0)return;
        if(app.authorized()){finish("内置后台已激活");return;}
        working=true;requested=true;status("正在启动内置后台…");int port=connectionPort;
        app.worker.execute(()->{try(AdbClient local=new AdbClient()){
            client=local;if(!alive)return;local.connect(port,identity());if(!alive)return;
            String output=local.launch(app.startCommand());
            if(output.contains("not found")||output.contains("Permission denied"))throw new IllegalStateException(output.trim());
            handler.post(new Runnable(){int attempts;public void run(){if(!alive)return;
                if(app.authorized()){working=false;finish("内置后台已激活，无需外部应用");}
                else if(++attempts>20){working=false;status("后台连接超时，请返回应用重试，或使用电脑激活");}
                else handler.postDelayed(this,700);
            }});
        }catch(Throwable e){handler.post(()->{if(!alive)return;working=false;status("启动失败："+readable(e)+"。请确认无线调试已开启，或重新配对。");});}
        finally{client=null;}});
    }
    private static String readable(Throwable e){String s=e.getMessage();return s==null?e.getClass().getSimpleName():s;}
    @Override public void onDestroy(){
        alive=false;app.prefs().unregisterOnSharedPreferenceChangeListener(options);if(current==this)current=null;handler.removeCallbacksAndMessages(null);if(discovery!=null)discovery.close();
        if(client!=null)client.close();pairingPort=0;connectionPort=0;working=false;
        getSystemService(NotificationManager.class).cancel(23);stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}
}
