package app.tidecard;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import org.json.*;

public class KeepService extends Service {
    public static final String START="app.tidecard.START",STOP="app.tidecard.STOP";
    private TideApp app;
    private boolean stopping;
    private int waits;
    private static final String CHANNEL="region_session";
    @Override public void onCreate(){super.onCreate();app=(TideApp)getApplication();NotificationChannel c=new NotificationChannel(CHANNEL,"持续改卡",NotificationManager.IMPORTANCE_LOW);c.setDescription("显示改卡运行状态，并提供停止恢复按钮");getSystemService(NotificationManager.class).createNotificationChannel(c);}
    private Notification notification(){
        Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,KeepService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        String title=app.prefs().getBoolean("restorePending",false)?"恢复待处理 · 原配置备份已保留":app.active()?Regions.get(app.country())[1]+" · "+Regions.slots(app.mask())+" · 持续改卡":"正在准备改卡";
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_launcher).setContentTitle(title).setContentText(app.message).setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null,"停止并恢复",stop).build()).build();
    }
    private void update(){getSystemService(NotificationManager.class).notify(21,notification());app.changed();}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(Build.VERSION.SDK_INT>=34)startForeground(21,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(21,notification());
        if(intent!=null&&STOP.equals(intent.getAction())){stopSession();return START_NOT_STICKY;}
        if(app.busy)return START_STICKY;
        app.main.removeCallbacks(tick);app.connect();app.main.post(tick);return START_STICKY;
    }
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(stopping)return;
        if(app.prefs().getBoolean("restorePending",false)){app.message="恢复待处理，请点击停止重试；配置不会被继续保持";update();return;}
        if(app.bridge==null){
            app.connect();app.message=app.active()?"等待内置后台连接；原配置备份已保留":"等待内置后台连接";update();
            if(!app.active()&&++waits>=10){app.message="连接超时，请重新激活内置后台后重试";stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();app.changed();return;}
            app.main.postDelayed(this,2000);return;
        }
        if(app.busy){app.main.postDelayed(this,2000);return;}
        app.busy=true;app.changed();
        app.worker.execute(()->{
            try {
                if(!app.active()){
                    app.message="正在备份原配置";update();
                    JSONObject snapshot=app.call("prepare",new JSONObject().put("mask",app.mask()));
                    if(!app.prefs().edit().putString("snapshot",snapshot.toString()).commit())throw new IllegalStateException("无法保存恢复备份，未做修改");
                }
                JSONObject snap=new JSONObject(app.prefs().getString("snapshot","{}"));String[] region=Regions.get(app.country());
                app.call("apply",new JSONObject().put("snapshot",snap).put("country",region[0]).put("name",region[3]));
                Thread.sleep(1500);app.cards=app.call("inspect",new JSONObject());
                boolean verified=true;JSONArray before=snap.getJSONArray("cards"),current=app.cards.getJSONArray("cards");
                for(int i=0;i<before.length();i++){int slot=before.getJSONObject(i).getInt("slot");if(!region[0].equals(current.getJSONObject(slot).optString("country")))verified=false;}
                app.message=verified?"已核对国家码 · 持续保持中":"配置已应用，等待系统更新国家码";
            }catch(Exception e){app.message=e.getMessage();}
            finally{app.busy=false;update();if(app.active())app.main.postDelayed(tick,30000);else app.main.post(()->{stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();});}
        });
    }};
    private void stopSession(){
        if(stopping)return;stopping=true;app.main.removeCallbacks(tick);app.prefs().edit().putBoolean("restorePending",true).commit();app.busy=true;app.message="正在恢复原配置";update();
        app.worker.execute(()->{
            boolean success=false;
            try{
                if(app.active())app.cards=app.call("restore",new JSONObject(app.prefs().getString("snapshot","{}")));
                app.prefs().edit().remove("snapshot").remove("restorePending").commit();app.message="已停止，原配置已恢复";success=true;
            }catch(Exception e){app.message="恢复未完成："+e.getMessage()+"。原配置备份已保留，可重试停止。";}
            final boolean restored=success;
            app.main.post(()->{app.busy=false;stopping=false;if(restored){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}else update();app.changed();});
        });
    }
    @Override public void onDestroy(){app.main.removeCallbacks(tick);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
