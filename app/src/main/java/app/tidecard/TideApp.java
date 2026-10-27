package app.tidecard;
import android.app.*;
import android.content.*;
import android.os.*;
import java.security.*;
import java.util.concurrent.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.*;

public class TideApp extends Application {
    public final Handler main=new Handler(Looper.getMainLooper());
    public final ExecutorService worker=Executors.newSingleThreadExecutor();
    public final CopyOnWriteArrayList<Runnable> observers=new CopyOnWriteArrayList<>();
    public volatile ICarrierBridge bridge;
    public volatile boolean binding,busy;
    public volatile String message="首次使用，请激活内置后台";
    public volatile JSONObject cards;
    private SharedPreferences prefs;
    @Override public void onCreate(){super.onCreate();prefs=getSharedPreferences("tidecard",MODE_PRIVATE);token();if(prefs.contains("sessionFailure"))message=prefs.getString("sessionFailure",message);}
    public SharedPreferences prefs(){return prefs;}
    public synchronized String token(){String token=prefs.getString("bridgeToken",null);if(token==null){byte[] b=new byte[32];new SecureRandom().nextBytes(b);token=android.util.Base64.encodeToString(b,android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING);if(!prefs.edit().putString("bridgeToken",token).commit())throw new IllegalStateException("无法保存激活凭据");}return token;}
    public boolean active(){return prefs.contains("snapshot");}
    public boolean authorized(){ICarrierBridge b=bridge;return b!=null&&b.asBinder().pingBinder();}
    public void accept(IBinder binder){main.post(()->{try{
        ICarrierBridge old=bridge;bridge=ICarrierBridge.Stub.asInterface(binder);binding=false;
        binder.linkToDeath(()->{if(bridge!=null&&bridge.asBinder()==binder){bridge=null;message=active()?"后台已断开，备份已保留；重新激活后可恢复":"后台已断开，请重新激活";changed();}},0);
        if(old!=null&&old.asBinder()!=binder&&old.asBinder().pingBinder())worker.execute(()->{try{old.destroy();}catch(Exception ignored){}});
        message=prefs.contains("sessionFailure")?prefs.getString("sessionFailure",""):active()?"内置后台已重连，正在同步会话":"内置后台已激活，可以一键改卡";refresh();changed();
    }catch(Exception e){bridge=null;message="连接失败："+e.getMessage();changed();}});}
    public void connect(){if(!authorized())bridge=null;changed();}
    public void requestAuthorization(){startActivity(new Intent(this,ActivationActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    public static final class BackendException extends Exception {public final boolean safeDiscardSnapshot;public final String code;BackendException(JSONObject result){super(result.optString("error","系统调用失败"));safeDiscardSnapshot=result.optBoolean("safeDiscardSnapshot",false);code=result.optString("code","");}}
    public JSONObject call(String command,JSONObject payload)throws Exception{ICarrierBridge b=bridge;if(b==null)throw new IllegalStateException("内置后台未激活，请先激活");JSONObject r=new JSONObject(b.execute(command,payload.toString()));if(!r.optBoolean("ok"))throw new BackendException(r);return r;}
    public void refresh(){if(!authorized())return;worker.execute(()->{try{cards=call("inspect",new JSONObject());}catch(Exception e){message=e.getMessage();}changed();});}
    public void changed(){main.post(()->{for(Runnable r:observers)r.run();});}
    public void saveSelection(String iso,int mask){prefs.edit().putString("country",iso).putInt("mask",mask).apply();changed();}
    public String country(){return prefs.getString("country","jp");}
    public int mask(){return prefs.getInt("mask",3);}
    public String startCommand(){return "CLASSPATH='"+getApplicationInfo().sourceDir+"' /system/bin/nohup /system/bin/setsid /system/bin/app_process /system/bin --nice-name=app.tidecard:bridge app.tidecard.Bootstrap '"+token()+"' "+getApplicationInfo().uid+" >/data/local/tmp/tidecard-bridge.log 2>&1 </dev/null & sleep 2";}
}
