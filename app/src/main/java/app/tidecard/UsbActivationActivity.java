package app.tidecard;

import android.app.*;
import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Exported only as an informational screen. All activation operations remain shell-only. */
public final class UsbActivationActivity extends ThemedActivity {
    private TideApp app;private TextView state;private final Runnable observer=this::render;
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private void text(LinearLayout root,String value,int size,int margin){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(colors.ink);t.setLineSpacing(dp(5),1);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(margin);root.addView(t,p);}
    private void button(LinearLayout root,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(colors.accent);b.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(12);root.addView(b,p);}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);app=(TideApp)getApplication();getWindow().setStatusBarColor(colors.background);getWindow().setNavigationBarColor(colors.background);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(colors.background);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(24),dp(30),dp(24),dp(32));scroll.addView(root);setContentView(scroll);
        root.setOnApplyWindowInsetsListener((v,in)->{android.graphics.Insets bars=in.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(24)+bars.left,dp(22)+bars.top,dp(24)+bars.right,dp(30)+bars.bottom);return in;});
        text(root,"电脑 USB 激活",28,0);text(root,"没有配对码或 IP，也可以通过数据线启动内置后台。",14,10);
        state=new TextView(this);state.setTextSize(16);state.setTextColor(colors.accent);state.setPadding(dp(18),dp(18),dp(18),dp(18));GradientDrawable bg=new GradientDrawable();bg.setColor(colors.soft);bg.setCornerRadius(dp(16));state.setBackground(bg);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(24);root.addView(state,p);
        text(root,"1  在 Windows 电脑解压「潮汐改卡 USB 激活工具」，打开 TideCard-USB.exe。",15,24);
        text(root,"2  在手机开发者选项开启「USB 调试」，用可传输数据的数据线连接电脑。",15,16);
        text(root,"3  解锁手机，在「允许 USB 调试」提示中确认这是自己的电脑并允许。电脑中选择这台手机，点击「安装 / 更新并激活」。",15,16);
        text(root,"4  若系统要求允许 USB 安装，请手动允许。这里显示已激活后，返回改卡界面选择地区和卡槽。",15,16);
        text(root,"无需开启无线调试或填写配对码。电脑只负责安装和激活，不会替你选择地区或开始改卡。激活后可拔线；重启或系统终止后台后可能需要再次连接。USB 激活不代表系统一定允许改卡。",13,24);
        button(root,"打开开发者选项",()->{try{startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}catch(Exception e){Toast.makeText(this,"请在系统设置中打开开发者选项和 USB 调试",Toast.LENGTH_LONG).show();}});
        button(root,"手动命令（备用）",()->{String command="adb shell \""+app.startCommand()+"\"";new AlertDialog.Builder(this).setTitle("手动电脑激活").setMessage("仅供自己的电脑使用。命令含本次安装的激活凭据，请勿公开分享。").setPositiveButton("复制命令",(d,w)->{getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("TideCard activation",command));Toast.makeText(this,"已复制激活命令",Toast.LENGTH_SHORT).show();}).setNegativeButton("取消",null).show();});
        button(root,"返回改卡界面",()->{startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();});render();
    }
    private void render(){state.setText(app.authorized()?"● 内置后台已激活\n可以拔线，返回主界面使用":"○ 等待电脑激活\n请在电脑工具中点击「安装 / 更新并激活」");}
    @Override protected void onResume(){super.onResume();app.observers.addIfAbsent(observer);render();}
    @Override protected void onPause(){app.observers.remove(observer);super.onPause();}
}
