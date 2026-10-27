package app.tidecard;

import android.os.*;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.telephony.SubscriptionInfo;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Runs exclusively in TideCard's ADB-shell backend. */
public class CarrierBackend extends ICarrierBridge.Stub {
    private final int ownerUid;
    private static final String COUNTRY="sim_country_iso_override_string", NAME_FLAG="carrier_name_override_bool", NAME="carrier_name_string";
    private Object loader, subscriptions;
    private Class<?> carrierApi, subApi;
    private IBinder carrierBinder;
    private boolean compatibilityWrite;
    public CarrierBackend(int ownerUid) {this.ownerUid=ownerUid;}
    @Override public boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
        if(Binder.getCallingUid()!=ownerUid)throw new SecurityException("This backend only serves TideCard");
        return super.onTransact(code,data,reply,flags);
    }
    private void init() throws Exception {
        if(loader!=null)return;
        Class<?> sm=Class.forName("android.os.ServiceManager");
        carrierBinder=(IBinder)sm.getMethod("getService",String.class).invoke(null,"carrier_config");
        carrierApi=Class.forName("com.android.internal.telephony.ICarrierConfigLoader");
        loader=Class.forName("com.android.internal.telephony.ICarrierConfigLoader$Stub").getMethod("asInterface",IBinder.class).invoke(null,carrierBinder);
        IBinder b=(IBinder)sm.getMethod("getService",String.class).invoke(null,"isub");
        subApi=Class.forName("com.android.internal.telephony.ISub");
        subscriptions=Class.forName("com.android.internal.telephony.ISub$Stub").getMethod("asInterface",IBinder.class).invoke(null,b);
    }
    private int subId(int slot) throws Exception {return (Integer)subApi.getMethod("getSubId",int.class).invoke(subscriptions,slot);}
    private SubscriptionInfo info(int id) throws Exception {
        return (SubscriptionInfo)subApi.getMethod("getActiveSubscriptionInfo",int.class,String.class,String.class).invoke(subscriptions,id,"com.android.shell",null);
    }
    private PersistableBundle config(int id) throws Exception {
        return (PersistableBundle)carrierApi.getMethod("getConfigForSubIdWithFeature",int.class,String.class,String.class).invoke(loader,id,"com.android.shell",null);
    }
    private void override(int id,PersistableBundle b) throws Exception {
        if(!compatibilityWrite){try{carrierApi.getMethod("overrideConfig",int.class,PersistableBundle.class,boolean.class).invoke(loader,id,b,false);return;}
            catch(InvocationTargetException e){Throwable cause=e.getCause();if(!(cause instanceof SecurityException)||!CarrierWritePolicy.shellDenied(cause.getMessage()))throw e;compatibilityWrite=true;}}
        CarrierCompatibility.write(ownerUid,Binder.getCallingPid(),id,b,carrierBinder);
    }
    private JSONObject inspect() throws Exception {
        JSONArray cards=new JSONArray();
        for(int slot=0;slot<2;slot++) {
            int id=subId(slot); JSONObject card=new JSONObject().put("slot",slot).put("subId",id);
            if(id>=0) {
                SubscriptionInfo si=info(id); PersistableBundle b=config(id);
                card.put("country",si==null?"":si.getCountryIso()).put("carrier",si==null?"":String.valueOf(si.getCarrierName()));
                card.put("override",b==null?"":b.getString(COUNTRY,""));
            }
            cards.put(card);
        }
        return new JSONObject().put("ok",true).put("uid",android.os.Process.myUid()).put("writeMode",compatibilityWrite?"compatibility":"direct").put("cards",cards);
    }
    private String dump() throws Exception {
        ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe(); ByteArrayOutputStream out=new ByteArrayOutputStream();
        Thread reader=new Thread(()-> {try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(pipe[0])){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}catch(IOException ignored){}});
        reader.start();try{carrierBinder.dump(pipe[1].getFileDescriptor(),new String[0]);}finally{pipe[1].close();}
        reader.join(4000);if(reader.isAlive())throw new IOException("读取原配置超时，请重试");
        return out.toString("UTF-8");
    }
    private String section(String dump,int slot,String label) throws Exception {
        return CarrierRecovery.section(dump,slot,label);
    }
    private boolean empty(String s){return s.isEmpty()||s.equals("null");}
    private String simCountry(SubscriptionInfo info)throws Exception{
        String mcc=info.getMccString();if(mcc==null||!mcc.matches("[0-9]{3}"))throw new IOException("无法读取 SIM 原始国家码，未做修改");
        Class<?> table;
        try{table=Class.forName("com.android.internal.telephony.MccTable");}
        catch(ClassNotFoundException e){table=Class.forName("com.android.internal.telephony.MccTable",true,new dalvik.system.PathClassLoader("/system/framework/telephony-common.jar",ClassLoader.getSystemClassLoader()));}
        String iso;
        try{iso=(String)table.getMethod("countryCodeForMcc",String.class).invoke(null,mcc);}
        catch(NoSuchMethodException e){iso=(String)table.getMethod("countryCodeForMcc",int.class).invoke(null,Integer.parseInt(mcc));}
        if(iso==null||!iso.matches("[a-zA-Z]{2}"))throw new IOException("此 SIM 的原始地区尚无法核对，请重启手机后再试");
        return iso.toLowerCase(Locale.ROOT);
    }
    private JSONObject recoveredCard(String state,int slot,int id,SubscriptionInfo info)throws Exception{
        Map<String,String> base=CarrierRecovery.baseline(section(state,slot,"Default Values from CarrierConfigManager"),
                section(state,slot,"mConfigFromDefaultApp"),section(state,slot,"mConfigFromCarrierApp"));
        String configCountry=base.get(COUNTRY),country=configCountry.isEmpty()?simCountry(info):configCountry;
        return new JSONObject().put("slot",slot).put("subId",id).put("country",country).put("configCountry",configCountry)
                .put("nameFlag",Boolean.parseBoolean(base.get(NAME_FLAG))).put("name",base.get(NAME)).put("recoveredExisting",true);
    }
    private JSONObject prepare(int mask) throws Exception {
        if(mask<1||mask>3)throw new IllegalArgumentException("无效的卡槽选择");
        String state=dump();JSONArray saved=new JSONArray();boolean recovered=false;
        for(int slot=0;slot<2;slot++)if((mask&(1<<slot))!=0) {
            int id=subId(slot);if(id<0)continue;
            if(!empty(section(state,slot,"mPersistentOverrideConfigs")))throw new IOException("卡 "+(slot+1)+" 存在持久覆盖，未做修改；请先在设置它的工具中恢复。");
            String temporary=section(state,slot,"mOverrideConfigs");
            if(!empty(temporary)&&!CarrierRecovery.compatible(temporary))throw new IOException("卡 "+(slot+1)+" 存在无法识别的覆盖，已保留且未修改；请先在设置它的工具中恢复。");
            SubscriptionInfo si=info(id);if(si==null||si.getCountryIso()==null||si.getCountryIso().isEmpty())throw new IOException("卡 "+(slot+1)+" 尚未就绪，请稍后重试");
            if(!empty(temporary)){saved.put(recoveredCard(state,slot,id,si));recovered=true;continue;}
            PersistableBundle b=config(id);
            saved.put(new JSONObject().put("slot",slot).put("subId",id).put("country",si.getCountryIso()).put("configCountry",b.getString(COUNTRY,""))
                .put("nameFlag",b.getBoolean(NAME_FLAG,false)).put("name",b.getString(NAME,"")));
        }
        if(saved.length()==0)throw new IOException("所选卡槽没有可用 SIM 卡");
        return new JSONObject().put("ok",true).put("recoveredExisting",recovered).put("cards",saved);
    }
    private JSONObject apply(JSONObject payload) throws Exception {
        String iso=payload.getString("country"),name=payload.getString("name");
        boolean allowed=false;for(String[] r:Regions.ALL)if(r[0].equals(iso)&&r[3].equals(name))allowed=true;
        if(!allowed)throw new IllegalArgumentException("无效的目标地区");
        JSONArray saved=payload.getJSONObject("snapshot").getJSONArray("cards");
        for(int n=0;n<saved.length();n++){JSONObject c=saved.getJSONObject(n);if(subId(c.getInt("slot"))!=c.getInt("subId"))throw new IOException("SIM 卡已变更，请先停止并恢复原设置");}
        int modifiedMask=0;
        try{for(int n=0;n<saved.length();n++) {
            int id=saved.getJSONObject(n).getInt("subId");PersistableBundle b=config(id);
            if(!iso.equals(b.getString(COUNTRY))||!b.getBoolean(NAME_FLAG)||!name.equals(b.getString(NAME))) {
                PersistableBundle change=new PersistableBundle();change.putString(COUNTRY,iso);change.putBoolean(NAME_FLAG,true);change.putString(NAME,name);override(id,change);
                modifiedMask|=1<<saved.getJSONObject(n).getInt("slot");
            }
        }}catch(Throwable error){
            Throwable cause=error;while(cause instanceof InvocationTargetException&&cause.getCause()!=null)cause=cause.getCause();
            boolean denied=cause instanceof SecurityException||(cause instanceof CarrierCompatibility.WriteFailure&&((CarrierCompatibility.WriteFailure)cause).definitelyDenied);
            boolean unchanged=false;if(denied&&modifiedMask==0)try{unchanged=originalUnchanged(payload.getJSONObject("snapshot"));}catch(Exception ignored){}
            throw new ApplyFailure((denied?"系统未允许改卡写入，兼容调用未完成：":"改卡未完成：")+message(cause),denied,modifiedMask,CarrierWritePolicy.canDiscardBackup(denied,modifiedMask,unchanged));
        }
        return inspect();
    }
    private static final class ApplyFailure extends Exception {
        final boolean denied,safeDiscard;final int modifiedMask;
        ApplyFailure(String message,boolean denied,int mask,boolean safe){super(message);this.denied=denied;modifiedMask=mask;safeDiscard=safe;}
    }
    private boolean originalUnchanged(JSONObject snapshot)throws Exception{
        String state=dump();JSONArray saved=snapshot.getJSONArray("cards");if(saved.length()==0)return false;
        for(int n=0;n<saved.length();n++){JSONObject card=saved.getJSONObject(n);int slot=card.getInt("slot"),id=card.getInt("subId");
            if(subId(slot)!=id||!empty(section(state,slot,"mOverrideConfigs"))||!empty(section(state,slot,"mPersistentOverrideConfigs")))return false;
            SubscriptionInfo si=info(id);PersistableBundle b=config(id);
            if(si==null||b==null||!card.getString("country").equals(si.getCountryIso())||!Objects.equals(card.optString("configCountry",""),b.getString(COUNTRY,""))||card.getBoolean("nameFlag")!=b.getBoolean(NAME_FLAG,false)||!Objects.equals(card.optString("name",""),b.getString(NAME,"")))return false;
        }return true;
    }
    private JSONObject restore(JSONObject snapshot) throws Exception {
        if(originalUnchanged(snapshot))return inspect(); // A failed first write needs no further writes to stop safely.
        JSONArray saved=snapshot.getJSONArray("cards");List<String> errors=new ArrayList<>();Set<Integer> restored=new HashSet<>();String before=dump();
        for(int n=0;n<saved.length();n++) {
            JSONObject c=saved.getJSONObject(n);int slot=c.getInt("slot"),id=c.getInt("subId");
            if(subId(slot)!=id){errors.add("卡 "+(slot+1)+" 已移除或更换；插回原卡后再点停止");continue;}
            if(!empty(section(before,slot,"mPersistentOverrideConfigs"))||!CarrierRecovery.onlyManagedFields(section(before,slot,"mOverrideConfigs"))){errors.add("卡 "+(slot+1)+" 出现其他覆盖，已保留且未修改");continue;}
            try {
                PersistableBundle original=new PersistableBundle();original.putString(COUNTRY,c.getString("country"));original.putBoolean(NAME_FLAG,c.getBoolean("nameFlag"));original.putString(NAME,c.optString("name",""));override(id,original);
                restored.add(slot);
            }catch(Exception e){errors.add("卡 "+(slot+1)+"："+message(e));}
        }
        Thread.sleep(1600);
        String state=dump();
        for(int n=0;n<saved.length();n++) {
            JSONObject c=saved.getJSONObject(n);int slot=c.getInt("slot"),id=c.getInt("subId");if(subId(slot)!=id||!restored.contains(slot))continue;
            String current=section(state,slot,"mOverrideConfigs");boolean other=false;
            for(String line:current.split("\n")){int eq=line.indexOf(" =");if(eq>0){String key=line.substring(0,eq).trim();if(!key.equals(COUNTRY)&&!key.equals(NAME_FLAG)&&!key.equals(NAME))other=true;}}
            if(other){errors.add("卡 "+(slot+1)+" 出现其他工具的覆盖配置；为避免误清除，请先还原其他工具后重试停止");continue;}
            override(id,null);
        }
        Thread.sleep(1200);
        for(int n=0;n<saved.length();n++) {
            JSONObject c=saved.getJSONObject(n);int slot=c.getInt("slot");if(subId(slot)!=c.getInt("subId"))continue;
            SubscriptionInfo si=info(c.getInt("subId"));if(si==null||!c.getString("country").equals(si.getCountryIso()))errors.add("卡 "+(slot+1)+" 国家码尚未恢复，请重试停止");
        }
        if(!errors.isEmpty())throw new IOException(String.join("；",errors));
        return inspect();
    }
    private static String message(Throwable t){while(t instanceof InvocationTargetException&&t.getCause()!=null)t=t.getCause();return t.getMessage()==null?t.getClass().getSimpleName():t.getMessage();}
    /** Only this app's fixed ongoing channel can be changed; full channel metadata is retained. */
    private JSONObject sessionNotification(JSONObject payload)throws Exception{
        boolean enabled=payload.getBoolean("enabled");int importance=payload.optInt("importance",NotificationManager.IMPORTANCE_LOW);
        if(importance<NotificationManager.IMPORTANCE_MIN||importance>NotificationManager.IMPORTANCE_HIGH)throw new IllegalArgumentException("无效的通知级别");
        Class<?> api=Class.forName("android.app.INotificationManager");
        IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"notification");
        Object manager=Class.forName("android.app.INotificationManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
        Method get=api.getMethod("getNotificationChannel",String.class,int.class,String.class,String.class);
        String pkg="app.tidecard",channelId="region_session";int user=ownerUid/100000;
        NotificationChannel channel=(NotificationChannel)get.invoke(manager,"com.android.shell",user,pkg,channelId);
        if(channel==null)throw new IOException("请先在应用内打开通知设置");
        int target=enabled?importance:NotificationManager.IMPORTANCE_NONE;channel.setImportance(target);
        api.getMethod("updateNotificationChannelForPackage",String.class,int.class,NotificationChannel.class).invoke(manager,pkg,ownerUid,channel);
        NotificationChannel after=(NotificationChannel)get.invoke(manager,"com.android.shell",user,pkg,channelId);
        if(after==null||after.getImportance()!=target)throw new IOException("系统未接受通知设置");
        return new JSONObject().put("ok",true).put("enabled",target>0).put("importance",target);
    }
    @Override public synchronized String execute(String command,String data) {
        try {
            JSONObject p=new JSONObject(data==null?"{}":data);
            if("session_notification".equals(command))return sessionNotification(p).toString();
            init();
            return switch(command){case "inspect"->inspect().toString();case "prepare"->prepare(p.getInt("mask")).toString();case "apply"->apply(p).toString();case "restore"->restore(p).toString();default->throw new IllegalArgumentException("未知操作");};
        } catch(Throwable e){try{JSONObject failure=new JSONObject().put("ok",false).put("error",message(e));if(e instanceof ApplyFailure){ApplyFailure apply=(ApplyFailure)e;failure.put("code",apply.denied?"carrier_write_rejected":"carrier_write_failed").put("modifiedMask",apply.modifiedMask).put("safeDiscardSnapshot",apply.safeDiscard);}return failure.toString();}catch(Exception ignored){return "{\"ok\":false,\"error\":\"系统调用失败\"}";}}
    }
    @Override public void destroy(){System.exit(0);}
}
