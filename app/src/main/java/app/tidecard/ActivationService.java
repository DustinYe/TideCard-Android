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
    private boolean requested,pairingSession,inputMode,successFeedback;private int successFrame;private String session;
    private final Runnable timeout=()->finish("激活已超时，请重新开始配对");
    @Override public void onCreate(){
        super.onCreate();app=(TideApp)getApplication();alive=true;current=this;
        session=UUID.randomUUID().toString();pairingPort=0;connectionPort=0;working=false;
        PairingNotifications.createChannel(this);
    }
    private Notification notification(){return PairingNotifications.build(this,session,status,pairingPort,pairingSession,inputMode,working,successFeedback,successFrame);}
    private void publish(){if(!alive)return;app.changed();getSystemService(NotificationManager.class).notify(22,notification());}
    private void status(String text){if(!alive)return;status=text;publish();}
    private void armTimeout(){handler.removeCallbacks(timeout);handler.postDelayed(timeout,180000);}
    private void finish(String text){if(!alive)return;status=text;app.changed();stopSelf();}
    static void receive(String session,String code,boolean open){
        ActivationService s=current;
        if(s==null||!s.alive||!s.session.equals(session)||!s.pairingSession||s.successFeedback||s.working||pairingPort==0)return;
        if(open)s.openInput();else s.pair(code,pairingPort);
    }
    @android.annotation.SuppressLint("WrongConstant") // Optional hidden statusbar service; failure keeps manual shade input usable.
    private void openInput(){
        // Android deliberately hides RemoteInput in promoted notifications. Demote this
        // notification before asking SystemUI to display its own protected input UI.
        inputMode=true;status("请展开本条通知，再点「输入六位码」；保持系统配对窗口打开");
        try{Object bar=getSystemService("statusbar");bar.getClass().getMethod("expandNotificationsPanel").invoke(bar);}
        catch(Exception ignored){android.widget.Toast.makeText(this,"请下拉通知栏，点「输入六位码」",android.widget.Toast.LENGTH_LONG).show();}
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        String action=intent==null?SCAN:intent.getAction();
        if(CANCEL.equals(action)){finish("已取消激活");return START_NOT_STICKY;}
        if((working||successFeedback)&&(SCAN.equals(action)||CONNECT.equals(action)))return START_NOT_STICKY;
        if(SCAN.equals(action)&&!working){
            pairingSession=true;inputMode=false;successFeedback=false;requested=false;successFrame=0;
            session=UUID.randomUUID().toString();status="等待打开系统的「使用配对码配对设备」";armTimeout();
        }
        if(Build.VERSION.SDK_INT>=34)startForeground(22,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(22,notification());
        if(app.authorized()&&CONNECT.equals(action)){finish("内置后台已激活");return START_NOT_STICKY;}
        if(discovery==null){
            discovery=new LocalDiscovery(this,new LocalDiscovery.Callback(){
                public void found(boolean pairing,int port){
                    if(!alive)return;
                    if(pairing){pairingPort=port;if(pairingSession&&!working&&!successFeedback)status("已发现配对窗口 · 展开岛卡片后点击输入");}
                    else{connectionPort=port;app.prefs().edit().putInt("connectPort",port).apply();if(requested&&!successFeedback)launch();}
                }
                public void lost(boolean pairing,int port){
                    if(!alive)return;
                    if(pairing&&pairingPort==port){pairingPort=0;if(!working&&!successFeedback){inputMode=false;status("配对窗口已关闭，请重新打开系统配对窗口");}}
                    else if(!pairing&&connectionPort==port)connectionPort=0;
                }
                public void error(String message){if(!working&&!successFeedback)status(message);}
            });discovery.start();
        }
        if(PAIR.equals(action))pair(intent.getStringExtra("code"),intent.getIntExtra("port",pairingPort));
        else if(CONNECT.equals(action)){
            pairingSession=false;requested=true;armTimeout();int manual=intent.getIntExtra("port",0);if(manual>0)connectionPort=manual;
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
            if(connectionPort>0)launch();else publish();
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
        alive=false;if(current==this)current=null;handler.removeCallbacksAndMessages(null);if(discovery!=null)discovery.close();
        if(client!=null)client.close();pairingPort=0;connectionPort=0;working=false;
        getSystemService(NotificationManager.class).cancel(23);stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}
}
