package app.tidecard;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.os.*;
import java.lang.reflect.*;
import java.util.UUID;
import java.util.concurrent.*;

/** Shell-side dispatcher. Uses only same-app, no-restart instrumentation on the fixed carrier API. */
final class CarrierCompatibility {
    static final String PID="tidecard.ownerPid",NONCE="tidecard.nonce",CALLBACK="tidecard.callback",SERVICE="tidecard.carrier",TRANSACTION="tidecard.transaction",SUB="tidecard.sub",CONFIG="tidecard.config",PERSISTENT="tidecard.persistentReset";
    static final class WriteFailure extends Exception {
        final boolean definitelyDenied;
        WriteFailure(String text,boolean denied){super(text);definitelyDenied=denied;}
    }
    private static Throwable unwrap(Throwable error){while(error instanceof InvocationTargetException&&error.getCause()!=null)error=error.getCause();return error;}
    @android.annotation.SuppressLint("BlockedPrivateApi") // This method is guarded to run only in the ADB shell backend, never the ordinary app UID.
    static void write(int ownerUid,int callerPid,int subId,PersistableBundle config,IBinder carrier)throws Exception{
        write(ownerUid,callerPid,subId,config,carrier,false);
    }
    @android.annotation.SuppressLint("BlockedPrivateApi")
    static void write(int ownerUid,int callerPid,int subId,PersistableBundle config,IBinder carrier,boolean persistent)throws Exception{
        CarrierWritePolicy.checkPersistentReset(config!=null,persistent);
        if(android.os.Process.myUid()!=2000||callerPid<=0)throw new SecurityException("ADB owner required");
        final Class<?> api;final Object am;final int flags,transactionCode;
        try{
            api=Class.forName("android.app.IActivityManager");am=Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
            // Never fall back to ordinary instrumentation: it would restart the visible app.
            Field flag=ActivityManager.class.getDeclaredField("INSTR_FLAG_NO_RESTART");flag.setAccessible(true);flags=flag.getInt(null);
            Field transaction=Class.forName("com.android.internal.telephony.ICarrierConfigLoader$Stub").getDeclaredField("TRANSACTION_overrideConfig");transaction.setAccessible(true);transactionCode=transaction.getInt(null);
        }catch(ReflectiveOperationException e){throw new WriteFailure("当前系统缺少兼容调用入口，未执行写入",true);}
        String nonce=UUID.randomUUID().toString();CarrierWritePolicy.Gate gate=new CarrierWritePolicy.Gate(nonce,ownerUid,callerPid);
        CountDownLatch done=new CountDownLatch(1);
        class Reply extends ICarrierOverrideCallback.Stub {
            volatile boolean success,denied;volatile String error="兼容调用未返回结果";boolean delegated,grantedOnce;
            private void check(String value){if(!gate.accepts(value,Binder.getCallingUid(),Binder.getCallingPid()))throw new SecurityException("Invalid compatibility callback");}
            @Override public synchronized boolean begin(String value){
                if(!gate.begin(value,Binder.getCallingUid(),Binder.getCallingPid()))return false;
                long identity=Binder.clearCallingIdentity();try{
                    api.getMethod("startDelegateShellPermissionIdentity",int.class,String[].class).invoke(am,ownerUid,new String[]{"android.permission.MODIFY_PHONE_STATE"});delegated=true;grantedOnce=true;return true;
                }catch(Exception e){error=String.valueOf(unwrap(e).getMessage());denied=true;return false;}
                finally{Binder.restoreCallingIdentity(identity);}
            }
            private void revoke()throws Exception{if(delegated){api.getMethod("stopDelegateShellPermissionIdentity").invoke(am);delegated=false;}}
            @Override public synchronized void end(String value){check(value);long identity=Binder.clearCallingIdentity();try{revoke();}catch(Exception e){throw new IllegalStateException("无法撤销临时测试权限",e);}finally{Binder.restoreCallingIdentity(identity);}}
            @Override public synchronized void complete(String value,boolean ok,boolean rejected,String detail){check(value);if(ok&&(!gate.begun()||!grantedOnce||delegated))throw new SecurityException("Incomplete compatibility permission lifecycle");success=ok;denied=rejected;error=detail;gate.close();done.countDown();}
            synchronized void close(){gate.close();long identity=Binder.clearCallingIdentity();try{revoke();}catch(Exception ignored){}finally{Binder.restoreCallingIdentity(identity);}}
        }
        Reply reply=new Reply();Bundle args=new Bundle();args.putInt(PID,callerPid);args.putString(NONCE,nonce);args.putBinder(CALLBACK,reply);args.putBinder(SERVICE,carrier);args.putInt(TRANSACTION,transactionCode);args.putInt(SUB,subId);args.putParcelable(CONFIG,config);args.putBoolean(PERSISTENT,persistent);
        try{
            Object ui=Class.forName("android.app.UiAutomationConnection").getConstructor().newInstance();
            Method start=api.getMethod("startInstrumentation",ComponentName.class,String.class,int.class,Bundle.class,Class.forName("android.app.IInstrumentationWatcher"),Class.forName("android.app.IUiAutomationConnection"),int.class,String.class);
            boolean accepted=(Boolean)start.invoke(am,new ComponentName("app.tidecard",CarrierCompatibilityInstrumentation.class.getName()),null,flags,args,null,ui,ownerUid/100000,null);
            if(!accepted)throw new WriteFailure("系统未允许无重启兼容调用",true);
            if(!done.await(12,TimeUnit.SECONDS))throw new WriteFailure("兼容调用超时；恢复备份已保留",false);
            if(!reply.success)throw new WriteFailure("兼容调用未成功："+(reply.error==null?"系统拒绝操作":reply.error),reply.denied);
        }catch(InvocationTargetException e){Throwable cause=unwrap(e);throw new WriteFailure("无法启动兼容调用："+cause.getMessage(),!gate.begun());}
        catch(ReflectiveOperationException e){throw new WriteFailure("当前系统缺少兼容调用入口",!gate.begun());}
        finally{reply.close();}
    }
}
