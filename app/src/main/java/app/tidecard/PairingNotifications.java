package app.tidecard;
import android.app.*;
import android.content.*;
import android.graphics.drawable.Icon;
import android.os.*;

final class PairingNotifications {
    static final String PROMOTE="android.requestPromotedOngoing";
    private static final String CHANNEL="activation";
    static void createChannel(Context c){NotificationChannel channel=new NotificationChannel(CHANNEL,I18n.tr("配对与后台激活"),NotificationManager.IMPORTANCE_DEFAULT);channel.setDescription(I18n.tr("配对时显示实况状态；配对成功后自动结束"));c.getSystemService(NotificationManager.class).createNotificationChannel(channel);}
    static PendingIntent input(Context c,String session,boolean reply){
        Intent i=new Intent(c,PairingReceiver.class).setAction(reply?PairingReceiver.REPLY:PairingReceiver.OPEN).putExtra("session",session).setData(android.net.Uri.parse("tidecard://activation/"+session+"/"+(reply?"reply":"open"))).addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        return PendingIntent.getBroadcast(c,reply?11:13,i,PendingIntent.FLAG_UPDATE_CURRENT|(reply?PendingIntent.FLAG_MUTABLE:PendingIntent.FLAG_IMMUTABLE));
    }
    static Notification.Action reply(Context c,String session){
        RemoteInput field=new RemoteInput.Builder("pairCode").setLabel(I18n.tr("六位配对码")).setAllowFreeFormInput(true).build();
        return new Notification.Action.Builder(Icon.createWithResource(c,R.drawable.ic_pair),I18n.tr("输入六位码"),input(c,session,true)).addRemoteInput(field).setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY).setAllowGeneratedReplies(false).build();
    }
    static PendingIntent action(Context c,String session,String action,int request,boolean mutable){
        Intent i=new Intent(c,PairingReceiver.class).setAction(action).putExtra("session",session).setData(android.net.Uri.parse("tidecard://activation/"+session+"/"+action)).addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        return PendingIntent.getBroadcast(c,request,i,PendingIntent.FLAG_UPDATE_CURRENT|(mutable?PendingIntent.FLAG_MUTABLE:PendingIntent.FLAG_IMMUTABLE));
    }
    private static Notification.Action manual(Context c,String session,boolean connect,boolean promoted){
        String title=connect?"输入连接端口":"手动配对";
        if(promoted)return new Notification.Action.Builder(null,I18n.tr(title),action(c,session,PairingReceiver.OPEN_MANUAL,17,false)).build();
        String key=connect?"connectPort":"manualPair";
        RemoteInput field=new RemoteInput.Builder(key).setLabel(I18n.tr(connect?"无线调试主页的连接端口":"配对端口 六位码，例如 39461 123456")).setAllowFreeFormInput(true).build();
        return new Notification.Action.Builder(null,I18n.tr(title),action(c,session,connect?PairingReceiver.CONNECTION:PairingReceiver.MANUAL,connect?15:14,true)).addRemoteInput(field).setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY).setAllowGeneratedReplies(false).build();
    }
    static Notification build(Context c,String session,String status,int port,boolean pairing,boolean inputMode,boolean working,boolean success,int frame){
        int[] icons={R.drawable.ic_pair_success_0,R.drawable.ic_pair_success_1,R.drawable.ic_pair_success_2,R.drawable.ic_pair_success_3};
        boolean editable=!working&&!success;
        boolean input=pairing&&port>0&&editable;
        boolean promoted=pairing&&!inputMode&&NotificationOptions.useLive(c);
        String help=success?I18n.tr("已安全保存本机配对身份，即将收起"):input?(!promoted?I18n.tr("展开本条通知，点「输入六位码」。无需切换应用。"):I18n.tr("点「打开配对输入」，进入系统通知输入；请保持配对窗口打开")):pairing?I18n.tr("保持系统配对弹窗打开；自动发现失败时，可在此通知手动输入配对端口和六位码"):I18n.tr("配对后未发现连接端口时，请在此通知填写无线调试主页的端口");
        Notification.Builder b=new Notification.Builder(c,CHANNEL).setSmallIcon(success?icons[frame]:R.drawable.ic_pair).setContentTitle(success?I18n.tr("潮汐改卡 · 配对成功"):pairing?I18n.tr("潮汐改卡 · 设备配对"):I18n.tr("潮汐改卡 · 启动后台"))
                .setContentText(I18n.message(status)).setStyle(new Notification.BigTextStyle().bigText(I18n.message(status)+"\n"+help)).setCategory(Notification.CATEGORY_PROGRESS).setOnlyAlertOnce(true).setShowWhen(false).setOngoing(true);
        Bundle extras=new Bundle();extras.putBoolean(PROMOTE,promoted);b.addExtras(extras);
        if(Build.VERSION.SDK_INT>=36)b.setShortCriticalText(success?I18n.tr("配对成功"):input?I18n.tr("输入配对码"):working?I18n.tr("正在配对"):I18n.tr("等待配对"));
        if(input){PendingIntent open=input(c,session,false);b.setContentIntent(open);b.addAction(!promoted?reply(c,session):new Notification.Action.Builder(null,I18n.tr("打开配对输入"),open).build());}
        if(editable){
            b.addAction(manual(c,session,!pairing,promoted));
            if(!input)b.addAction(new Notification.Action.Builder(null,I18n.tr("重新搜索"),action(c,session,PairingReceiver.RETRY,16,false)).build());
        }
        PendingIntent cancel=PendingIntent.getService(c,12,new Intent(c,ActivationService.class).setAction(ActivationService.CANCEL).putExtra("session",session).setData(android.net.Uri.parse("tidecard://activation/"+session+"/cancel")),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        b.addAction(new Notification.Action.Builder(null,I18n.tr("取消激活"),cancel).build());return b.build();
    }
}
