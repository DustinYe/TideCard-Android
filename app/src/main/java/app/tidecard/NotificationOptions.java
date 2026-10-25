package app.tidecard;

import android.app.*;
import android.content.Context;
import android.os.Build;

final class NotificationOptions {
    static final String SESSION_CHANNEL="region_session";
    static final String PAIRING_LIVE="pairingLiveUpdates";
    static final String SESSION_IMPORTANCE="sessionNotificationImportance";
    static void ensureSessionChannel(Context c){
        NotificationChannel channel=new NotificationChannel(SESSION_CHANNEL,"持续改卡",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("显示改卡运行状态，并提供停止恢复按钮");
        c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    static boolean pairingLiveEnabled(Context c){return ((TideApp)c.getApplicationContext()).prefs().getBoolean(PAIRING_LIVE,true);}
    static boolean useLive(Context c){return pairingLiveEnabled(c)&&Build.VERSION.SDK_INT>=36&&c.getSystemService(NotificationManager.class).canPostPromotedNotifications();}
    static int sessionImportance(Context c){NotificationChannel channel=c.getSystemService(NotificationManager.class).getNotificationChannel(SESSION_CHANNEL);return channel==null?NotificationManager.IMPORTANCE_LOW:channel.getImportance();}
}
