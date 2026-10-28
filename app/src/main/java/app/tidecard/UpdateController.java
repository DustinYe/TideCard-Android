package app.tidecard;

import android.content.pm.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;

/** User-initiated only. No startup checks, background scheduler, privileged installs or telemetry. */
public final class UpdateController {
    public static final String API="https://api.github.com/repos/DustinYe/TideCard-Android/releases/latest";
    public enum State { IDLE,CHECKING,CURRENT,AVAILABLE,DOWNLOADING,READY,ERROR }
    private final TideApp app;private final ExecutorService network=Executors.newSingleThreadExecutor();
    public volatile State state=State.IDLE;public volatile ReleaseInfo release;public volatile int progress;public volatile String error="";public volatile File ready;
    public UpdateController(TideApp app){this.app=app;}
    private void changed(){app.changed();}
    public synchronized void check(){if(state==State.CHECKING||state==State.DOWNLOADING)return;state=State.CHECKING;release=null;ready=null;error="";changed();network.execute(()->{try{
        HttpsURLConnection connection=open(API);ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=connection.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>1024*1024)throw new IOException("Release metadata too large");bytes.write(b,0,n);}}finally{connection.disconnect();}
        ReleaseInfo found=ReleaseInfo.parse(new JSONObject(bytes.toString("UTF-8")));release=found;state=ReleaseInfo.compare(found.version,BuildConfig.VERSION_NAME)>0?State.AVAILABLE:State.CURRENT;
    }catch(Exception e){fail(e);}finally{changed();}});}
    public synchronized void download(){if(state!=State.AVAILABLE||release==null)return;ReleaseInfo chosen=release;state=State.DOWNLOADING;progress=0;error="";changed();network.execute(()->{File partial=null;try{
        File dir=new File(app.getFilesDir(),"updates");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot create update directory");partial=new File(dir,"download.part");File complete=new File(dir,"TideCard-update.apk");ready=null;
        HttpsURLConnection connection=open(chosen.url);MessageDigest digest=MessageDigest.getInstance("SHA-256");long count=0;try(InputStream in=connection.getInputStream();FileOutputStream out=new FileOutputStream(partial)){byte[] b=new byte[32768];int n,last=-1;while((n=in.read(b))!=-1){count+=n;if(count>chosen.size)throw new IOException("APK larger than release metadata");out.write(b,0,n);digest.update(b,0,n);int percent=(int)(count*100/chosen.size);if(percent!=last){last=percent;progress=percent;changed();}}out.getFD().sync();}finally{connection.disconnect();}
        if(count!=chosen.size||!hex(digest.digest()).equals(chosen.digest))throw new IOException("APK SHA-256 or size mismatch");
        verifyApk(app.getPackageManager(),app.getPackageName(),partial,chosen.version);
        if(complete.exists()&&!complete.delete())throw new IOException("Cannot replace cached APK");if(!partial.renameTo(complete))throw new IOException("Cannot finalize download");ready=complete;state=State.READY;
    }catch(Exception e){if(partial!=null)partial.delete();fail(e);}finally{changed();}});}
    private void fail(Exception e){error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();state=State.ERROR;}
    static HttpsURLConnection open(String initial)throws Exception{
        URL url=new URL(initial);for(int redirects=0;redirects<=5;redirects++){
            String host=url.getHost();if(!url.getProtocol().equals("https")||url.getUserInfo()!=null||!(host.equals("api.github.com")||host.equals("github.com")||host.equals("release-assets.githubusercontent.com")||host.equals("objects.githubusercontent.com")))throw new IOException("Untrusted update host");
            HttpsURLConnection c=(HttpsURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestProperty("User-Agent","TideCard/"+BuildConfig.VERSION_NAME);c.setRequestProperty("Accept","application/vnd.github+json");int status=c.getResponseCode();
            if(status==301||status==302||status==303||status==307||status==308){String location=c.getHeaderField("Location");c.disconnect();if(location==null)throw new IOException("Missing redirect");url=new URL(url,location);continue;}
            if(status!=200){c.disconnect();throw new IOException(status==403||status==429?"GitHub rate limit; try again later":"GitHub HTTP "+status);}return c;
        }throw new IOException("Too many redirects");
    }
    public static void verifyApk(PackageManager pm,String packageName,File file,String expectedVersion)throws Exception{
        PackageInfo installed=pm.getPackageInfo(packageName,PackageManager.GET_SIGNING_CERTIFICATES),candidate=pm.getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
        if(candidate==null||!packageName.equals(candidate.packageName)||!expectedVersion.equals(candidate.versionName)||candidate.getLongVersionCode()<=installed.getLongVersionCode())throw new SecurityException("APK package or version mismatch");
        if(candidate.signingInfo==null||installed.signingInfo==null||!signers(candidate).equals(signers(installed)))throw new SecurityException("APK signing certificate mismatch");
    }
    private static Set<String> signers(PackageInfo info)throws Exception{Set<String> set=new HashSet<>();for(Signature signature:info.signingInfo.getApkContentsSigners())set.add(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));return set;}
    static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
}
