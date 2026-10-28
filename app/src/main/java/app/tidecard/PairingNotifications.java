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
        Intent i=new Intent(c,PairingReceiver.class).setAction(reply?PairingReceiver.REPLY:PairingReceiver.OPEN).putExtra("session",session).addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        return PendingIntent.getBroadcast(c,reply?11:13,i,PendingIntent.FLAG_CANCEL_CURRENT|(reply?PendingIntent.FLAG_MUTABLE:PendingIntent.FLAG_IMMUTABLE));
    }
    static Notification.Action reply(Context c,String session){
        RemoteInput field=new RemoteInput.Builder("pairCode").setLabel(I18n.tr("六位配对码")).setAllowFreeFormInput(true).build();
        return new Notification.Action.Builder(Icon.createWithResource(c,R.drawable.ic_pair),I18n.tr("输入六位码"),input(c,session,true)).addRemoteInput(field).setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY).setAllowGeneratedReplies(false).build();
    }
    static Notification build(Context c,String session,String status,int port,boolean pairing,boolean inputMode,boolean working,boolean success,int frame){
        int[] icons={R.drawable.ic_pair_success_0,R.drawable.ic_pair_success_1,R.drawable.ic_pair_success_2,R.drawable.ic_pair_success_3};
        boolean input=pairing&&port>0&&!working&&!success;
        boolean promoted=pairing&&!inputMode&&NotificationOptions.useLive(c);
        String help=success?I18n.tr("已安全保存本机配对身份，即将收起"):input?(!promoted?I18n.tr("展开本条通知，点「输入六位码」。无需切换应用。"):I18n.tr("点「打开配对输入」，进入系统通知输入；请保持配对窗口打开")):pairing?I18n.tr("打开系统无线调试 → 使用配对码配对设备"):I18n.tr("激活完成后此通知自动结束");
        Notification.Builder b=new Notification.Builder(c,CHANNEL).setSmallIcon(success?icons[frame]:R.drawable.ic_pair).setContentTitle(success?I18n.tr("潮汐改卡 · 配对成功"):pairing?I18n.tr("潮汐改卡 · 设备配对"):I18n.tr("潮汐改卡 · 启动后台"))
                .setContentText(I18n.message(status)).setStyle(new Notification.BigTextStyle().bigText(I18n.message(status)+"\n"+help)).setCategory(Notification.CATEGORY_PROGRESS).setOnlyAlertOnce(true).setShowWhen(false).setOngoing(true);
        Bundle extras=new Bundle();extras.putBoolean(PROMOTE,promoted);b.addExtras(extras);
        if(Build.VERSION.SDK_INT>=36)b.setShortCriticalText(success?I18n.tr("配对成功"):input?I18n.tr("输入配对码"):working?I18n.tr("正在配对"):I18n.tr("等待配对"));
        if(input){PendingIntent open=input(c,session,false);b.setContentIntent(open);b.addAction(!promoted?reply(c,session):new Notification.Action.Builder(null,I18n.tr("打开配对输入"),open).build());}
        PendingIntent cancel=PendingIntent.getService(c,12,new Intent(c,ActivationService.class).setAction(ActivationService.CANCEL),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        b.addAction(new Notification.Action.Builder(null,I18n.tr("取消激活"),cancel).build());return b.build();
    }
}
