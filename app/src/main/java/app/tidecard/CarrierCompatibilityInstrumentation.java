package app.tidecard;

import android.app.*;
import android.os.*;
import java.util.*;

/** One acknowledged write in the existing app PID; no shell commands or arbitrary Binder targets. */
public final class CarrierCompatibilityInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){
        super.onCreate(args);
        if(args==null||args.getInt(CarrierCompatibility.PID,-1)!=android.os.Process.myPid()){finish(Activity.RESULT_CANCELED,new Bundle());return;}
        String nonce=args.getString(CarrierCompatibility.NONCE);ICarrierOverrideCallback callback=ICarrierOverrideCallback.Stub.asInterface(args.getBinder(CarrierCompatibility.CALLBACK));
        IBinder service=args.getBinder(CarrierCompatibility.SERVICE);int transaction=args.getInt(CarrierCompatibility.TRANSACTION,-1),sub=args.getInt(CarrierCompatibility.SUB,-1);
        PersistableBundle config=Build.VERSION.SDK_INT>=33?args.getParcelable(CarrierCompatibility.CONFIG,PersistableBundle.class):args.getParcelable(CarrierCompatibility.CONFIG);
        boolean persistent=args.getBoolean(CarrierCompatibility.PERSISTENT,false);
        new Thread(()->{
            boolean ok=false,denied=false;String error=null;
            try{
                if(callback==null||nonce==null||service==null||transaction<=0||sub<0)throw new SecurityException("Invalid compatibility request");
                if(!"com.android.internal.telephony.ICarrierConfigLoader".equals(service.getInterfaceDescriptor()))throw new SecurityException("Unexpected carrier service");
                if(config!=null&&!config.keySet().equals(new HashSet<>(Arrays.asList(CarrierRecovery.COUNTRY,CarrierRecovery.FLAG,CarrierRecovery.NAME))))throw new SecurityException("Unexpected carrier fields");
                CarrierWritePolicy.checkPersistentReset(config!=null,persistent);
                if(!callback.begin(nonce))throw new SecurityException("Compatibility permission was not granted");
                Parcel data=Parcel.obtain(),reply=Parcel.obtain();try{
                    data.writeInterfaceToken("com.android.internal.telephony.ICarrierConfigLoader");data.writeInt(sub);data.writeTypedObject(config,0);data.writeInt(persistent?1:0); // persistent=true is restricted to a null reset
                    if(!service.transact(transaction,data,reply,0))throw new IllegalStateException("Carrier transaction unavailable");
                    reply.readException();ok=true;
                }finally{data.recycle();reply.recycle();}
            }catch(SecurityException e){denied=true;error=e.getMessage();}
            catch(Throwable e){error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
            finally{
                try{if(callback!=null)callback.end(nonce);}catch(Exception e){ok=false;denied=false;error="临时权限清理未完成";}
                final boolean success=ok,rejected=denied;final String detail=error;
                new Handler(Looper.getMainLooper()).post(()->{
                    boolean finished=success,definitelyRejected=rejected;String result=detail;
                    try{finish(success?Activity.RESULT_OK:Activity.RESULT_CANCELED,new Bundle());}
                    catch(Throwable e){finished=false;definitelyRejected=false;result="兼容调用清理失败，请重启应用后重试";}
                    try{if(callback!=null)callback.complete(nonce,finished,definitelyRejected,result);}catch(Exception ignored){}
                });
            }
        },"tidecard-carrier-compat").start();
    }
}
