package app.tidecard;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import app.tidecard.adb.*;

public class ActivationService extends Service {
    public static final String SCAN="app.tidecard.SCAN",CONNECT="app.tidecard.CONNECT",PAIR="app.tidecard.PAIR",CANCEL="app.tidecard.CANCEL";
    public static volatile int pairingPort,connectionPort;
    public static volatile boolean working;
    public static volatile String status="首次使用，请按步骤激活";
    private TideApp app;private LocalDiscovery discovery;private AdbIdentity identity;private boolean requested;
    private static final String CHANNEL="activation";
    @Override public void onCreate(){super.onCreate();app=(TideApp)getApplication();getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"内置后台激活",NotificationManager.IMPORTANCE_DEFAULT));}
    private Notification notification(){
        PendingIntent open=PendingIntent.getActivity(this,10,new Intent(this,ActivationActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_launcher).setContentTitle("潮汐改卡 · 激活内置后台").setContentText(status).setStyle(new Notification.BigTextStyle().bigText(status+"\n在系统配对窗口保持打开时，在此通知中输入六位配对码。")).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true);
        if(pairingPort>0&&!working){PendingIntent reply=PendingIntent.getBroadcast(this,11,new Intent(this,PairingReceiver.class),PendingIntent.FLAG_MUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);RemoteInput input=new RemoteInput.Builder("pairCode").setLabel("六位配对码").build();b.addAction(new Notification.Action.Builder(null,"输入配对码",reply).addRemoteInput(input).setAllowGeneratedReplies(false).build());}
        PendingIntent cancel=PendingIntent.getService(this,12,new Intent(this,ActivationService.class).setAction(CANCEL),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);b.addAction(new Notification.Action.Builder(null,"取消激活",cancel).build());return b.build();
    }
    private void status(String text){status=text;app.changed();getSystemService(NotificationManager.class).notify(22,notification());}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        String action=intent==null?SCAN:intent.getAction();if(CANCEL.equals(action)){stopSelf();return START_NOT_STICKY;}
        if(Build.VERSION.SDK_INT>=34)startForeground(22,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(22,notification());
        if(app.authorized()&&CONNECT.equals(action)){status("内置后台已激活");stopSelf();return START_NOT_STICKY;}
        if(discovery==null){pairingPort=0;connectionPort=0;discovery=new LocalDiscovery(this,new LocalDiscovery.Callback(){
            public void found(boolean pairing,int port){if(pairing){pairingPort=port;status("已发现本机配对窗口，可在通知中输入配对码");}else{connectionPort=port;app.prefs().edit().putInt("connectPort",port).apply();if(requested||app.prefs().getBoolean("paired",false))launch();}}
            public void error(String message){status(message);}
        });discovery.start();status("正在寻找本机无线调试服务…");}
        if(PAIR.equals(action))pair(intent.getStringExtra("code"),intent.getIntExtra("port",pairingPort));
        else if(CONNECT.equals(action)){requested=true;int manual=intent.getIntExtra("port",0);if(manual>0)connectionPort=manual;if(connectionPort>0)launch();else status("请开启无线调试，正在寻找本机连接端口…");}
        return START_NOT_STICKY;
    }
    private AdbIdentity identity()throws Exception{if(identity==null)identity=new AdbIdentity(this);return identity;}
    private void pair(String code,int port){if(working)return;working=true;status("正在与本机配对，请保持系统配对窗口打开…");app.worker.execute(()->{try{
        PairingClient.pair(port,code==null?"":code.trim(),identity());app.prefs().edit().putBoolean("paired",true).commit();app.main.post(()->{working=false;requested=true;if(app.authorized()){status("配对成功，内置后台已就绪");stopSelf();}else{status("配对成功，正在启动内置后台…");if(connectionPort>0)launch();}});
    }catch(Throwable e){app.main.post(()->{working=false;status("配对失败："+readable(e));});}});}
    private void launch(){if(working||app.authorized())return;working=true;requested=true;status("正在启动内置后台…");int port=connectionPort;
        app.worker.execute(()->{try(AdbClient client=new AdbClient()){
            client.connect(port,identity());String output=client.launch(app.startCommand());if(output.contains("not found")||output.contains("Permission denied"))throw new IllegalStateException(output.trim());
            app.main.post(new Runnable(){int attempts;public void run(){if(app.authorized()){working=false;status("内置后台已激活，无需外部应用");stopSelf();}else if(++attempts>20){working=false;status("后台连接超时，请返回应用重试，或使用电脑激活");}else app.main.postDelayed(this,700);}});
        }catch(Throwable e){app.main.post(()->{working=false;status("启动失败："+readable(e)+"。请确认无线调试已开启，或重新配对。");});}});
    }
    private static String readable(Throwable e){String s=e.getMessage();return s==null?e.getClass().getSimpleName():s;}
    @Override public void onDestroy(){if(discovery!=null)discovery.close();working=false;stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
