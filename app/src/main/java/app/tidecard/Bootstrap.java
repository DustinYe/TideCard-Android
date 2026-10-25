package app.tidecard;
import android.content.Context;
import android.net.Uri;
import android.os.*;

public final class Bootstrap {
    public static void main(String[] args)throws Exception{
        if(android.os.Process.myUid()!=2000||args.length!=2)throw new SecurityException("ADB shell activation required");
        String token=args[0];int owner=Integer.parseInt(args[1]);if(owner<10000)throw new SecurityException("Invalid owner");
        Looper.prepareMainLooper();Object thread=Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
        Context system=(Context)thread.getClass().getMethod("getSystemContext").invoke(thread);
        if(system.getPackageManager().getApplicationInfo("app.tidecard",0).uid!=owner)throw new SecurityException("Owner mismatch");
        CarrierBackend backend=new CarrierBackend(owner);Handler handler=new Handler(Looper.getMainLooper());
        Runnable attach=new Runnable(){IBinder client;int failures;
            @Override public void run(){
                if(client!=null&&client.pingBinder()){handler.postDelayed(this,5000);return;}
                try{
                    Bundle payload=new Bundle();payload.putBinder("backend",backend);payload.putInt("version",BuildConfig.VERSION_CODE);
                    Bundle reply=attachProvider(token,payload);
                    if(reply==null||!reply.getBoolean("ok"))throw new IllegalStateException("Activation rejected");
                    client=reply.getBinder("client");failures=0;System.out.println("TideCard backend connected; uid="+android.os.Process.myUid());
                }catch(SecurityException e){System.err.println("Activation rejected: "+e.getMessage());System.exit(2);return;}
                catch(Exception e){Throwable reason=e;while(reason instanceof java.lang.reflect.InvocationTargetException&&reason.getCause()!=null)reason=reason.getCause();System.err.println(reason.getClass().getSimpleName()+": "+reason.getMessage());if(++failures>60){System.exit(3);return;}}
                handler.postDelayed(this,5000);
            }
        };handler.post(attach);Looper.loop();
    }
    private static Bundle attachProvider(String token,Bundle payload)throws Exception{
        String authority="app.tidecard.bridge";Class<?> amApi=Class.forName("android.app.IActivityManager");Object am=Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        Object holder=amApi.getMethod("getContentProviderExternal",String.class,int.class,IBinder.class,String.class).invoke(am,authority,0,null,"TideCard");
        if(holder==null)throw new IllegalStateException("Client provider unavailable");
        try{
            Object provider=holder.getClass().getField("provider").get(holder);Class<?> cpApi=Class.forName("android.content.IContentProvider");
            if(Build.VERSION.SDK_INT>=31)return (Bundle)cpApi.getMethod("call",android.content.AttributionSource.class,String.class,String.class,String.class,Bundle.class).invoke(provider,new android.content.AttributionSource.Builder(2000).setPackageName("com.android.shell").build(),authority,"attach",token,payload);
            return (Bundle)cpApi.getMethod("call",String.class,String.class,String.class,String.class,String.class,Bundle.class).invoke(provider,"com.android.shell",null,authority,"attach",token,payload);
        }finally{try{amApi.getMethod("removeContentProviderExternalAsUser",String.class,IBinder.class,int.class).invoke(am,authority,null,0);}catch(Exception ignored){}}
    }
}
