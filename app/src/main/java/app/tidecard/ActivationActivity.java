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

public class ActivationActivity extends ThemedActivity {
    private TideApp app;private TextView status,permissionStatus,steps;private final Runnable observer=this::render;
    int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(colors.ink);t.setLineSpacing(dp(5),1);return t;}
    void add(LinearLayout root,View v,int margin){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(margin);root.addView(v,p);}
    Button button(String label){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(colors.accent);return b;}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);app=(TideApp)getApplication();getWindow().setStatusBarColor(colors.background);getWindow().setNavigationBarColor(colors.background);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(colors.background);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(24),dp(30),dp(24),dp(40));scroll.addView(root);setContentView(scroll);
        root.setOnApplyWindowInsetsListener((v,in)->{android.graphics.Insets bars=in.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(24)+bars.left,dp(22)+bars.top,dp(24)+bars.right,dp(30)+bars.bottom);return in;});
        add(root,text("内置后台激活",28),0);add(root,text("一个应用即可完成，无需安装 Shizuku。",14),10);status=text("",14);status.setPadding(dp(16),dp(18),dp(16),dp(18));GradientDrawable bg=new GradientDrawable();bg.setColor(colors.soft);bg.setCornerRadius(dp(16));status.setBackground(bg);add(root,status,24);
        Button usb=button("使用电脑 USB 激活 ↗");usb.setOnClickListener(v->startActivity(new Intent(this,UsbActivationActivity.class)));add(root,usb,18);add(root,text("或使用无线配对",21),25);steps=text("",14);add(root,steps,12);
        permissionStatus=text("",13);add(root,permissionStatus,16);
        Button notifications=button("通知与超级岛设置");notifications.setOnClickListener(v->startActivity(new Intent(this,NotificationSettingsActivity.class)));add(root,notifications,8);
        Button scan=button("开始配对");scan.setId(android.R.id.button1);scan.setOnClickListener(v->scan());add(root,scan,18);
        Button settings=button("打开无线调试设置 ↗");settings.setOnClickListener(v->settings());add(root,settings,8);
        add(root,text("已配对过？",20),26);add(root,text("开启无线调试后，点击下面的按钮即可再次启动。重启手机后通常可复用配对。",13),9);Button connect=button("启动内置后台");connect.setOnClickListener(v->startForegroundService(new Intent(this,ActivationService.class).setAction(ActivationService.CONNECT)));add(root,connect,10);
        Button manual=button("手动输入端口 / 配对码");manual.setOnClickListener(v->manual());add(root,manual,7);
        Button pc=button("电脑 USB 一键激活（无需无线配对）");pc.setOnClickListener(v->startActivity(new Intent(this,UsbActivationActivity.class)));add(root,pc,14);
        add(root,text("配对和连接仅访问本机。首次系统授权需要你手动完成；应用不能自动开启调试。若系统停止后台，可回到这里重新激活。",12),22);
        Button done=button("返回改卡界面");done.setOnClickListener(v->finish());add(root,done,20);
    }
    private void scan(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},30);return;}startForegroundService(new Intent(this,ActivationService.class).setAction(ActivationService.SCAN));}
    @Override public void onRequestPermissionsResult(int c,String[] p,int[] r){super.onRequestPermissionsResult(c,p,r);if(c==30){if(r.length>0&&r[0]==PackageManager.PERMISSION_GRANTED)scan();else new AlertDialog.Builder(this).setMessage("通知权限用于在系统配对窗口保持打开时输入配对码。也可使用手动输入或电脑 USB 激活。").setPositiveButton("知道了",null).show();}}
    private void settings(){Intent i=new Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS");if(i.resolveActivity(getPackageManager())==null)i=new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);try{startActivity(i);}catch(Exception e){Toast.makeText(this,"请在系统设置中打开开发者选项 → 无线调试",Toast.LENGTH_LONG).show();}}
    private void manual(){LinearLayout box=new LinearLayout(this);box.setPadding(dp(20),dp(4),dp(20),0);box.setOrientation(LinearLayout.VERTICAL);EditText port=new EditText(this);port.setHint("系统配对窗口中的端口");port.setInputType(2);if(ActivationService.pairingPort>0)port.setText(""+ActivationService.pairingPort);box.addView(port);EditText code=new EditText(this);code.setHint("六位配对码（需保持配对窗口打开）");code.setInputType(2);box.addView(code);new AlertDialog.Builder(this).setTitle("手动配对 / 连接").setView(box).setPositiveButton("配对",(d,w)->{try{int number=Integer.parseInt(port.getText().toString());startForegroundService(new Intent(this,ActivationService.class).setAction(ActivationService.PAIR).putExtra("port",number).putExtra("code",code.getText().toString()));}catch(Exception e){Toast.makeText(this,"请输入端口和配对码",Toast.LENGTH_SHORT).show();}}).setNeutralButton("仅连接该端口",(d,w)->{try{startForegroundService(new Intent(this,ActivationService.class).setAction(ActivationService.CONNECT).putExtra("port",Integer.parseInt(port.getText().toString())));}catch(Exception ignored){}}).setNegativeButton("取消",null).show();}
    private void render(){status.setText(ActivationService.current==null&&app.authorized()?"● 内置后台已激活\n返回主界面即可一键改卡":ActivationService.status);String live=!NotificationOptions.pairingLiveEnabled(this)?"超级岛已关闭 · 直接使用普通通知输入":Build.VERSION.SDK_INT>=36?(getSystemService(NotificationManager.class).canPostPromotedNotifications()?"实况通知可用 · 最终样式由系统决定":"系统已关闭实况通知，可使用通知栏输入"):"本系统使用通知栏配对输入";permissionStatus.setText(live+"\n系统不允许在实况卡片中直接输入；输入使用普通通知，配对成功后自动结束。");steps.setText("1  连接 Wi-Fi，开启系统「无线调试」。\n\n2  点击「开始配对」，打开系统的「使用配对码配对设备」。\n\n3  保持系统配对窗口打开。"+(NotificationOptions.useLive(this)?"点岛展开卡片 →「打开配对输入」→ 展开系统通知并点「输入六位码」。岛被系统隐藏时，可直接下拉通知栏操作。":"下拉通知栏，展开潮汐改卡的配对通知，点「输入六位码」并发送。")+"\n\n配对成功后显示短暂勾选反馈，配对通知随即结束，日常改卡不占用岛。");}
    @Override protected void onResume(){super.onResume();app.observers.addIfAbsent(observer);render();}
    @Override protected void onPause(){app.observers.remove(observer);super.onPause();}
}
