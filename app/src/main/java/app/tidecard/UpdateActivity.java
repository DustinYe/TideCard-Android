package app.tidecard;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;

public final class UpdateActivity extends ThemedActivity {
    private TideApp app;private UpdateController updates;private TextView status,notes;private Button check,action;private final Runnable observer=this::render;private boolean permissionRequested;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(I18n.tr(s));t.setTextSize(size);t.setTextColor(colors.ink);t.setLineSpacing(dp(4),1);return t;}
    private void add(LinearLayout root,View v,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);root.addView(v,p);}
    private Button button(String s){Button b=new Button(this);b.setText(I18n.tr(s));b.setAllCaps(false);b.setMinHeight(dp(48));return b;}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);app=(TideApp)getApplication();updates=app.updates;permissionRequested=saved!=null&&saved.getBoolean("permissionRequested");ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(colors.background);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);scroll.addView(root);setContentView(scroll);root.setOnApplyWindowInsetsListener((v,in)->{android.graphics.Insets bars=in.getInsets(WindowInsets.Type.systemBars());root.setPadding(dp(24)+bars.left,dp(14)+bars.top,dp(24)+bars.right,dp(30)+bars.bottom);return in;});
        Button back=button("‹ 返回");back.setOnClickListener(v->finish());add(root,back,0);add(root,text("应用更新",28),20);add(root,text(I18n.tr("当前版本：")+BuildConfig.VERSION_NAME,14),8);
        add(root,text("仅在你点击时访问 GitHub 官方仓库，检查最新正式版并下载 APK。安装前会验证文件校验和、包名、版本和签名。",14),16);
        status=text("",17);status.setId(R.id.update_status);status.setPadding(dp(18),dp(18),dp(18),dp(18));GradientDrawable bg=new GradientDrawable();bg.setColor(colors.soft);bg.setCornerRadius(dp(20));status.setBackground(bg);add(root,status,22);
        check=button("检查更新");check.setId(R.id.update_check);check.setOnClickListener(v->updates.check());add(root,check,20);action=button("下载更新");action.setId(R.id.update_action);action.setOnClickListener(v->{if(updates.state==UpdateController.State.READY)install();else updates.download();});add(root,action,8);
        notes=text("",13);notes.setTextIsSelectable(true);add(root,notes,20);Button page=button("打开 GitHub 发布页 ↗");page.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(ReleaseInfo.REPO+"/releases")));}catch(ActivityNotFoundException e){Toast.makeText(this,I18n.tr("没有可用浏览器"),Toast.LENGTH_LONG).show();}});add(root,page,16);
        add(root,text("首次安装更新需要允许本应用安装未知应用，并在系统安装界面确认。更新前请先停止改卡并恢复原配置；升级后可能需要重新激活后台。",13),16);if(saved==null&&getIntent().getBooleanExtra("check",false))updates.check();render();
    }
    private void install(){
        if(app.active()||app.busy){new AlertDialog.Builder(this).setMessage(I18n.tr("请先返回主界面停止改卡并恢复原配置，再安装更新。")).setPositiveButton(I18n.tr("知道了"),null).show();return;}
        if(updates.ready==null||!updates.ready.isFile()){updates.ready=null;updates.state=UpdateController.State.ERROR;updates.error="Downloaded APK is missing";render();return;}
        if(!getPackageManager().canRequestPackageInstalls()){new AlertDialog.Builder(this).setTitle(I18n.tr("允许安装更新")).setMessage(I18n.tr("请在下一页允许来自潮汐改卡的安装。返回后将打开系统安装界面。")).setPositiveButton(I18n.tr("前往设置"),(d,w)->{try{permissionRequested=true;startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException e){permissionRequested=false;Toast.makeText(this,I18n.tr("请在系统设置中允许安装未知应用"),Toast.LENGTH_LONG).show();}}).setNegativeButton(I18n.tr("取消"),null).show();return;}
        try{UpdateController.verifyApk(getPackageManager(),getPackageName(),updates.ready,updates.release.version);Uri apk=FileProvider.getUriForFile(this,getPackageName()+".updates",updates.ready);Intent installer=new Intent(Intent.ACTION_VIEW).setDataAndType(apk,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(installer);}catch(Exception e){Toast.makeText(this,I18n.tr("无法打开安装界面：")+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void render(){if(status==null)return;UpdateController.State state=updates.state;String message;switch(state){case CHECKING:message="正在检查更新…";break;case CURRENT:message=updates.release!=null&&ReleaseInfo.compare(updates.release.version,BuildConfig.VERSION_NAME)<0?"当前版本高于 GitHub 正式版":"已是最新正式版";break;case AVAILABLE:message="发现新版本：";break;case DOWNLOADING:message="正在下载：";break;case READY:message="校验通过，可以安装";break;case ERROR:message="更新失败，请重试或打开发布页";break;default:message="点击检查更新，获取最新正式版";}
        String details=state==UpdateController.State.AVAILABLE?updates.release.version:state==UpdateController.State.DOWNLOADING?updates.progress+"%":state==UpdateController.State.ERROR?"\n"+updates.error:"";status.setText(I18n.tr(message)+details);check.setEnabled(state!=UpdateController.State.CHECKING&&state!=UpdateController.State.DOWNLOADING);action.setVisibility(state==UpdateController.State.AVAILABLE||state==UpdateController.State.READY?View.VISIBLE:View.GONE);action.setText(I18n.tr(state==UpdateController.State.READY?"安装更新":"下载更新"));notes.setText(updates.release==null?"":I18n.tr("GitHub 版本说明（原文）：")+"\n\n"+updates.release.notes);
    }
    @Override protected void onResume(){super.onResume();app.observers.addIfAbsent(observer);render();if(permissionRequested){permissionRequested=false;if(getPackageManager().canRequestPackageInstalls())install();}}
    @Override protected void onPause(){app.observers.remove(observer);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putBoolean("permissionRequested",permissionRequested);}
}
