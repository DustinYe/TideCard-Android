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
        if(Binder.getCallingUid()!=2000)throw new SecurityException("Only authorized ADB shell can attach the backend");
        TideApp app=(TideApp)getContext().getApplicationContext();
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
