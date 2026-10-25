package app.tidecard;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;

public class NotificationSettingsActivity extends Activity {
    private TideApp app;
    private Switch session,live;
    private TextView sessionState,liveState;
    private Button system;
    private boolean rendering,saving;
    private final Runnable observer=this::render;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(0xff163c44);t.setLineSpacing(dp(4),1);return t;}
    private void add(LinearLayout parent,View child,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(child,p);}
    private LinearLayout card(LinearLayout parent){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(18),dp(20),dp(20));GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(22));box.setBackground(bg);add(parent,box,18);return box;}
    private Switch toggle(String label,int id){Switch s=new Switch(this);s.setId(id);s.setText(label);s.setTextSize(17);s.setTextColor(0xff163c44);s.setSwitchPadding(dp(16));s.setMinHeight(dp(52));return s;}
    private Button button(String label){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(0xff087f8c);return b;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);app=(TideApp)getApplication();NotificationOptions.ensureSessionChannel(this);
        getWindow().setStatusBarColor(0xfff3f7f7);getWindow().setNavigationBarColor(0xfff3f7f7);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xfff3f7f7);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);scroll.addView(root);setContentView(scroll);
        root.setOnApplyWindowInsetsListener((v,in)->{android.graphics.Insets bars=in.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(24)+bars.left,dp(16)+bars.top,dp(24)+bars.right,dp(32)+bars.bottom);return in;});
        Button back=button("‹ 返回");back.setOnClickListener(v->finish());add(root,back,0);
        add(root,text("通知与超级岛",28),18);add(root,text("分别选择改卡提醒与配对时的呈现方式。",14),8);
        LinearLayout ongoing=card(root);session=toggle("持续改卡通知",R.id.session_notice_switch);add(ongoing,session,0);
        add(ongoing,text("关闭后只隐藏提醒，改卡与定时检查继续运行。停止并恢复时，请回到主界面操作。",13),9);
        sessionState=text("",13);sessionState.setId(R.id.session_notice_state);add(ongoing,sessionState,12);
        LinearLayout pairing=card(root);live=toggle("配对时显示超级岛",R.id.pairing_live_switch);add(pairing,live,0);
        add(pairing,text("仅在主动配对时显示，成功反馈后自动结束。关闭后直接在普通通知中输入配对码。",13),9);
        liveState=text("",13);add(pairing,liveState,12);
        system=button("系统通知设置 ↗");system.setOnClickListener(v->systemSettings(null));add(root,system,22);
        Button channel=button("持续改卡通知的系统设置 ↗");channel.setOnClickListener(v->systemSettings(NotificationOptions.SESSION_CHANNEL));add(root,channel,5);
        add(root,text("系统可能隐藏受保护设置页面上的岛；配对输入仍使用系统通知。通知关闭后，Android 仍可能在“活动应用”中列出运行中的服务。",12),15);
        session.setOnCheckedChangeListener((v,enabled)->{if(!rendering)saveSession(enabled);});
        live.setOnCheckedChangeListener((v,enabled)->{if(rendering)return;app.prefs().edit().putBoolean(NotificationOptions.PAIRING_LIVE,enabled).apply();app.changed();render();});
        render();
    }
    private void saveSession(boolean enabled){
        NotificationManager manager=getSystemService(NotificationManager.class);
        if(enabled&&!manager.areNotificationsEnabled()){
            render();Toast.makeText(this,"请先允许本应用的系统通知权限",Toast.LENGTH_LONG).show();
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},40);
            else systemSettings(null);return;
        }
        if(!app.authorized()){
            render();Toast.makeText(this,"内置后台未激活，请在系统中调整本通知类别",Toast.LENGTH_LONG).show();systemSettings(NotificationOptions.SESSION_CHANNEL);return;
        }
        int current=NotificationOptions.sessionImportance(this);
        if(!enabled&&current>NotificationManager.IMPORTANCE_NONE)app.prefs().edit().putInt(NotificationOptions.SESSION_IMPORTANCE,current).apply();
        int importance=app.prefs().getInt(NotificationOptions.SESSION_IMPORTANCE,NotificationManager.IMPORTANCE_LOW);
        saving=true;render();
        app.worker.execute(()->{String error=null;try{
            app.call("session_notification",new JSONObject().put("enabled",enabled).put("importance",importance));
        }catch(Exception e){error=e.getMessage();}
            final String problem=error;app.main.post(()->{saving=false;if(problem!=null)Toast.makeText(this,"未能确认通知设置，请检查当前状态，或使用下方系统设置。",Toast.LENGTH_LONG).show();KeepService.refreshNotification();app.changed();render();});
        });
    }
    private void systemSettings(String channel){
        Intent i=channel==null?new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()):
                new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID,channel);
        try{startActivity(i);}catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));}
    }
    private void render(){
        if(session==null)return;rendering=true;
        NotificationManager manager=getSystemService(NotificationManager.class);boolean allowed=manager.areNotificationsEnabled();boolean shown=allowed&&NotificationOptions.sessionImportance(this)>NotificationManager.IMPORTANCE_NONE;
        session.setChecked(shown);session.setEnabled(!saving);
        sessionState.setText(saving?"正在保存…":!allowed?"系统通知总权限已关闭":shown?"已显示持续改卡通知":"已隐藏持续改卡通知，运行状态请看主界面");
        live.setChecked(NotificationOptions.pairingLiveEnabled(this));
        liveState.setText(!NotificationOptions.pairingLiveEnabled(this)?"已选择普通通知配对输入":Build.VERSION.SDK_INT<36?"已保存选择；当前系统使用普通通知":!allowed?"已保存选择；需先允许系统通知权限":!manager.canPostPromotedNotifications()?"已保存选择；系统暂未允许实况通知":"已选择实况展示，具体样式由系统决定");
        rendering=false;
    }
    @Override protected void onResume(){super.onResume();app.observers.addIfAbsent(observer);render();}
    @Override protected void onPause(){app.observers.remove(observer);super.onPause();}
    @Override public void onRequestPermissionsResult(int c,String[] p,int[] r){super.onRequestPermissionsResult(c,p,r);render();if(c==40&&r.length>0&&r[0]==PackageManager.PERMISSION_GRANTED)saveSession(true);}
}
