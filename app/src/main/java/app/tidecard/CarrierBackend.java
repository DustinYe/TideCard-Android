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
        carrierApi.getMethod("overrideConfig",int.class,PersistableBundle.class,boolean.class).invoke(loader,id,b,false);
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
        return new JSONObject().put("ok",true).put("uid",android.os.Process.myUid()).put("cards",cards);
    }
    private String dump() throws Exception {
        ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe(); ByteArrayOutputStream out=new ByteArrayOutputStream();
        Thread reader=new Thread(()-> {try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(pipe[0])){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}catch(IOException ignored){}});
        reader.start();try{carrierBinder.dump(pipe[1].getFileDescriptor(),new String[0]);}finally{pipe[1].close();}
        reader.join(4000);if(reader.isAlive())throw new IOException("读取原配置超时，请重试");
        return out.toString("UTF-8");
    }
    private String section(String dump,int slot,String label) throws Exception {
        String marker="Phone Id = "+slot;int start=dump.indexOf(marker);if(start<0)throw new IOException("此系统的配置格式暂不支持安全恢复");
        int end=dump.indexOf("Phone Id = ",start+marker.length());String phone=dump.substring(start,end<0?dump.length():end);
        int pos=phone.indexOf(label+" :");if(pos<0)throw new IOException("无法读取原覆盖配置，未做修改");
        String rest=phone.substring(pos+label.length()+2);int blank=rest.indexOf("\n\n");
        return (blank<0?rest:rest.substring(0,blank)).trim();
    }
    private boolean empty(String s){return s.isEmpty()||s.equals("null");}
    private JSONObject prepare(int mask) throws Exception {
        String state=dump();JSONArray saved=new JSONArray();
        for(int slot=0;slot<2;slot++)if((mask&(1<<slot))!=0) {
            int id=subId(slot);if(id<0)continue;
            if(!empty(section(state,slot,"mPersistentOverrideConfigs"))||!empty(section(state,slot,"mOverrideConfigs")))
                throw new IOException("卡 "+(slot+1)+" 已有其他工具的覆盖设置。请先在原工具还原，再启动潮汐改卡。");
            SubscriptionInfo si=info(id);if(si==null||si.getCountryIso()==null||si.getCountryIso().isEmpty())throw new IOException("卡 "+(slot+1)+" 尚未就绪，请稍后重试");
            PersistableBundle b=config(id);
            saved.put(new JSONObject().put("slot",slot).put("subId",id).put("country",si.getCountryIso()).put("configCountry",b.getString(COUNTRY,""))
                .put("nameFlag",b.getBoolean(NAME_FLAG,false)).put("name",b.getString(NAME,"")));
        }
        if(saved.length()==0)throw new IOException("所选卡槽没有可用 SIM 卡");
        return new JSONObject().put("ok",true).put("cards",saved);
    }
    private JSONObject apply(JSONObject payload) throws Exception {
        String iso=payload.getString("country"),name=payload.getString("name");
        boolean allowed=false;for(String[] r:Regions.ALL)if(r[0].equals(iso)&&r[3].equals(name))allowed=true;
        if(!allowed)throw new IllegalArgumentException("无效的目标地区");
        JSONArray saved=payload.getJSONObject("snapshot").getJSONArray("cards");
        for(int n=0;n<saved.length();n++){JSONObject c=saved.getJSONObject(n);if(subId(c.getInt("slot"))!=c.getInt("subId"))throw new IOException("SIM 卡已变更，请先停止并恢复原设置");}
        for(int n=0;n<saved.length();n++) {
            int id=saved.getJSONObject(n).getInt("subId");PersistableBundle b=config(id);
            if(!iso.equals(b.getString(COUNTRY))||!b.getBoolean(NAME_FLAG)||!name.equals(b.getString(NAME))) {
                PersistableBundle change=new PersistableBundle();change.putString(COUNTRY,iso);change.putBoolean(NAME_FLAG,true);change.putString(NAME,name);override(id,change);
            }
        }
        return inspect();
    }
    private JSONObject restore(JSONObject snapshot) throws Exception {
        JSONArray saved=snapshot.getJSONArray("cards");List<String> errors=new ArrayList<>();
        for(int n=0;n<saved.length();n++) {
            JSONObject c=saved.getJSONObject(n);int slot=c.getInt("slot"),id=c.getInt("subId");
            if(subId(slot)!=id){errors.add("卡 "+(slot+1)+" 已移除或更换；插回原卡后再点停止");continue;}
            try {
                PersistableBundle original=new PersistableBundle();original.putString(COUNTRY,c.getString("country"));original.putBoolean(NAME_FLAG,c.getBoolean("nameFlag"));original.putString(NAME,c.optString("name",""));override(id,original);
            }catch(Exception e){errors.add("卡 "+(slot+1)+"："+message(e));}
        }
        Thread.sleep(1600);
        String state=dump();
        for(int n=0;n<saved.length();n++) {
            JSONObject c=saved.getJSONObject(n);int slot=c.getInt("slot"),id=c.getInt("subId");if(subId(slot)!=id)continue;
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
        } catch(Throwable e){try{return new JSONObject().put("ok",false).put("error",message(e)).toString();}catch(Exception ignored){return "{\"ok\":false,\"error\":\"系统调用失败\"}";}}
    }
    @Override public void destroy(){System.exit(0);}
}
