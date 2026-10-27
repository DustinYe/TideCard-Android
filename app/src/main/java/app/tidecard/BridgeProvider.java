package app.tidecard;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class BridgeProvider extends ContentProvider {
    private final IBinder life=new Binder();
    @Override public boolean onCreate(){return true;}
    @Override public Bundle call(String method,String token,Bundle extras){
        UsbActivationProtocol.requireShell(Binder.getCallingUid());
        TideApp app=(TideApp)getContext().getApplicationContext();
        if("usb_prepare".equals(method)){
            String path=android.util.Base64.encodeToString(app.getApplicationInfo().sourceDir.getBytes(StandardCharsets.UTF_8),android.util.Base64.NO_WRAP);
            Bundle reply=new Bundle();reply.putString("record",UsbActivationProtocol.prepare(BuildConfig.VERSION_CODE,app.getApplicationInfo().uid,app.token(),path));
            app.main.post(()->{app.stopService(new Intent(app,ActivationService.class));ActivationService.status="已切换到电脑 USB 激活";app.changed();});
            return reply;
        }
        if("usb_status".equals(method)){Bundle reply=new Bundle();reply.putString("record",UsbActivationProtocol.status(BuildConfig.VERSION_CODE,app.authorized()));return reply;}
        if(!"attach".equals(method)||token==null||!MessageDigest.isEqual(app.token().getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8)))throw new SecurityException("Invalid activation token");
        if(extras==null||extras.getInt("version")!=BuildConfig.VERSION_CODE)throw new SecurityException("Backend version mismatch");
        IBinder backend=extras.getBinder("backend");if(backend==null||!backend.pingBinder())throw new IllegalArgumentException("Missing backend");
        app.accept(backend);Bundle reply=new Bundle();reply.putBoolean("ok",true);reply.putBinder("client",life);return reply;
    }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new SecurityException();}
    @Override public String getType(Uri u){return null;}
    @Override public Uri insert(Uri u,ContentValues v){throw new SecurityException();}
    @Override public int delete(Uri u,String s,String[] a){throw new SecurityException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new SecurityException();}
}
