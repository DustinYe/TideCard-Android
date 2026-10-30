package app.tidecard;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;

public class MainActivity extends ThemedActivity {
    private int INK,MUTED,TEAL,BG;
    private TideApp app;
    private TextView status,heroTitle,heroSub,code,regionTitle,regionSub,simStatus,authTitle,authDetail,dockTitle,dockSub;
    private TextView[] slots=new TextView[3];
    private LinearLayout region;
    private PlayButton play;
    private final Runnable observer=this::render;
    public int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");if(bold)t.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD));return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private void add(LinearLayout l,View v,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);l.addView(v,p);}
    private LinearLayout card(){LinearLayout l=column();l.setPadding(dp(20),dp(18),dp(20),dp(18));l.setBackground(bg(colors.surface,22));return l;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);INK=colors.ink;MUTED=colors.muted;TEAL=colors.accent;BG=colors.background;app=(TideApp)getApplication();getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(BG);getWindow().setDecorFitsSystemWindows(false);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(BG);root.setOnApplyWindowInsetsListener((v,in)->{android.graphics.Insets bars=in.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return in;});setContentView(root);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);FrameLayout.LayoutParams scrollLP=new FrameLayout.LayoutParams(-1,-1);scrollLP.bottomMargin=dp(112);root.addView(scroll,scrollLP);
        LinearLayout content=column();content.setPadding(dp(24),dp(12),dp(24),dp(24));scroll.addView(content);
        LinearLayout brand=new LinearLayout(this);brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView wordmark=text("T I D E C A R D",11,TEAL,true);wordmark.setLetterSpacing(.13f);brand.addView(wordmark,new LinearLayout.LayoutParams(0,-2,1));TextView settings=text(I18n.tr("设置"),12,TEAL,false);settings.setId(R.id.notification_settings);settings.setPadding(dp(8),dp(8),dp(8),dp(8));settings.setOnClickListener(v->startActivity(new Intent(this,NotificationSettingsActivity.class)));brand.addView(settings);TextView help=text(I18n.tr("使用说明  ↗"),12,MUTED,false);help.setPadding(dp(8),dp(8),0,dp(8));help.setOnClickListener(v->help());brand.addView(help);add(content,brand,0);
        add(content,text(I18n.tr("潮汐改卡"),32,INK,true),12);add(content,text(I18n.tr("为你的 SIM 信息，换一个目的地。"),13,MUTED,false),5);
        if(BuildConfig.VERSION_NAME.contains("-rc"))add(content,text(BuildConfig.VERSION_NAME+I18n.tr(" · 兼容测试版，请以实际国家码读回为准"),11,TEAL,false),8);
        LinearLayout hero=column();hero.setPadding(dp(23),dp(23),dp(23),dp(24));GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{colors.heroStart,colors.heroEnd});gradient.setCornerRadius(dp(26));hero.setBackground(gradient);
        status=text(I18n.tr("●  已停止"),12,TEAL,true);status.setId(R.id.status);add(hero,status,0);
        LinearLayout heroRow=new LinearLayout(this);heroRow.setGravity(Gravity.CENTER_VERTICAL);LinearLayout heroWords=column();heroTitle=text(I18n.tr("准备启程"),27,INK,true);heroWords.addView(heroTitle);heroSub=text(I18n.tr("轻触播放键，开始持续改卡"),12,MUTED,false);add(heroWords,heroSub,7);heroRow.addView(heroWords,new LinearLayout.LayoutParams(0,-2,1));code=text("JP",36,TEAL,true);code.setGravity(Gravity.CENTER);code.setTypeface(android.graphics.Typeface.create("sans-serif-light",Typeface.NORMAL));heroRow.addView(code,new LinearLayout.LayoutParams(dp(73),dp(75)));add(hero,heroRow,17);add(content,hero,24);
        region=card();region.setId(R.id.region);region.setClickable(true);region.setFocusable(true);LinearLayout regionHead=new LinearLayout(this);TextView regionLabel=text(I18n.tr("目标地区"),12,MUTED,true);regionHead.addView(regionLabel,new LinearLayout.LayoutParams(0,-2,1));regionHead.addView(text(I18n.tr("更改  ›"),12,TEAL,true));region.addView(regionHead);regionTitle=text(I18n.tr("日本"),21,INK,true);add(region,regionTitle,9);regionSub=text("Japan · JP",12,MUTED,false);add(region,regionSub,4);region.setOnClickListener(v->{if(!app.active()&&!app.busy)chooseRegion();});add(content,region,16);
        TextView advice=text(I18n.tr("建议改卡目标区域与 VPN 区域相同。"),12,MUTED,false);advice.setLineSpacing(dp(3),1);add(content,advice,11);
        LinearLayout sim=card();LinearLayout simHead=new LinearLayout(this);simHead.addView(text(I18n.tr("更改哪些卡"),14,INK,true),new LinearLayout.LayoutParams(0,-2,1));TextView recommended=text(I18n.tr("推荐改双卡"),11,TEAL,true);recommended.setBackground(bg(colors.soft,8));recommended.setPadding(dp(8),dp(5),dp(8),dp(5));simHead.addView(recommended);sim.addView(simHead);
        LinearLayout segments=new LinearLayout(this);segments.setPadding(dp(4),dp(4),dp(4),dp(4));segments.setBackground(bg(BG,14));String[] labels={I18n.tr("双卡"),I18n.tr("卡 1"),I18n.tr("卡 2")};int[] masks={3,1,2};int[] ids={R.id.slot_both,R.id.slot_one,R.id.slot_two};
        for(int i=0;i<3;i++){final int mask=masks[i];TextView t=text(labels[i],14,INK,true);t.setId(ids[i]);t.setGravity(Gravity.CENTER);t.setClickable(true);t.setFocusable(true);slots[i]=t;LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);segments.addView(t,lp);t.setOnClickListener(v->{if(!app.active()&&!app.busy)app.saveSelection(app.country(),mask);});}add(sim,segments,14);
        simStatus=text(I18n.tr("激活内置后台后显示两张卡的实际国家码"),11,MUTED,false);simStatus.setLineSpacing(dp(4),1);add(sim,simStatus,13);add(content,sim,16);
        LinearLayout auth=card();authTitle=text(I18n.tr("内置后台 · 待激活"),14,INK,true);auth.addView(authTitle);authDetail=text(I18n.tr("首次配对后即可一键改卡，无需外部应用"),12,MUTED,false);add(auth,authDetail,7);TextView authorize=text(I18n.tr("激活 / 配对  ↗"),13,TEAL,true);authorize.setPadding(0,dp(15),0,dp(2));authorize.setOnClickListener(v->authorize());auth.addView(authorize);add(content,auth,16);
        Button cleanup=new Button(this);cleanup.setId(R.id.cleanup_open);cleanup.setAllCaps(false);cleanup.setText(I18n.tr("清除旧地区配置  ›"));cleanup.setTextColor(TEAL);cleanup.setOnClickListener(v->startActivity(new Intent(this,CleanupActivity.class)));add(content,cleanup,8);
        TextView note=text(I18n.tr("运行时自动检查，停止时恢复原配置。\n不占用 VPN 通道，不修改实体 SIM 卡。"),11,MUTED,false);note.setLineSpacing(dp(4),1);add(content,note,18);
        LinearLayout dock=new LinearLayout(this);dock.setGravity(Gravity.CENTER_VERTICAL);dock.setPadding(dp(26),0,dp(14),0);GradientDrawable dockBg=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{BG&0x00FFFFFF,BG,BG});dock.setBackground(dockBg);FrameLayout.LayoutParams dockLP=new FrameLayout.LayoutParams(-1,dp(112),Gravity.BOTTOM);root.addView(dock,dockLP);
        LinearLayout dockWords=column();dockTitle=text(I18n.tr("一键启程"),16,INK,true);dockSub=text(I18n.tr("激活内置后台后即可开始"),11,MUTED,false);dockWords.addView(dockTitle);add(dockWords,dockSub,5);dock.addView(dockWords,new LinearLayout.LayoutParams(0,-2,1));play=new PlayButton(this);play.setId(R.id.toggle);dock.addView(play,new LinearLayout.LayoutParams(dp(98),dp(98)));play.setOnClickListener(v->toggle());
        render();
    }
    @Override protected void onResume(){super.onResume();app.observers.addIfAbsent(observer);app.connect();app.refresh();render();if(app.active()&&!app.prefs().getBoolean("restorePending",false)&&!app.prefs().contains("sessionFailure"))startForegroundService(new Intent(this,KeepService.class).setAction(KeepService.START));}
    @Override protected void onPause(){app.observers.remove(observer);super.onPause();}
    private void chooseRegion(){startActivityForResult(new Intent(this,RegionPickerActivity.class).putExtra("country",app.country()),81);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==81&&result==RESULT_OK&&data!=null){String iso=data.getStringExtra("country");if(app.active()||app.busy){Toast.makeText(this,I18n.tr("请先停止当前会话再更改地区"),Toast.LENGTH_SHORT).show();return;}if(Regions.find(iso)!=null)app.saveSelection(iso,app.mask());}}
    private void authorize(){startActivity(new Intent(this,ActivationActivity.class));}
    private void toggle(){
        if(app.busy)return;
        if(app.active()){startForegroundService(new Intent(this,KeepService.class).setAction(KeepService.STOP));return;}
        if(!app.authorized()){authorize();return;}
        if(app.bridge==null){app.connect();Toast.makeText(this,I18n.tr("正在连接改卡服务，请稍候"),Toast.LENGTH_SHORT).show();return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!app.prefs().getBoolean("notificationAsked",false)){
            app.prefs().edit().putBoolean("notificationAsked",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},8);return;
        }
        startForegroundService(new Intent(this,KeepService.class).setAction(KeepService.START));
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==8)toggle();}
    private void help(){new AlertDialog.Builder(this).setTitle(I18n.tr("开始前，三步就绪")).setMessage(I18n.tr("1. 点击「激活 / 配对」进入内置激活向导。首次通过系统无线调试配对；在通知中输入配对码，无需安装其他应用。也可用电脑 USB 激活。\n\n2. 选择目标地区，建议与 VPN 出口地区相同。选择卡 1、卡 2 或双卡；推荐改双卡。\n\n3. 点击右下角播放键开始，点击停止键或通知中的停止按钮恢复原配置。\n\n运行时每 30 秒检查所选卡的配置，仅在偏离目标时重新应用。请先停止再拔卡、换卡或卸载。重启手机后可能需要重新激活后台，通常无需重新配对。\n\n本应用不提供 VPN，也不保证第三方应用一定可用。网络权限用于本机无线调试配对和连接，以及你主动检查更新时访问 GitHub 获取版本信息和 APK。")).setNeutralButton(I18n.tr("开源许可"),(d,w)->startActivity(new Intent(this,LicensesActivity.class))).setPositiveButton(I18n.tr("知道了"),null).show();}
    private void render(){
        if(play==null)return;boolean running=app.active(),busy=app.busy;String[] r=Regions.get(app.country());code.setText(r[4]);regionTitle.setText(I18n.region(r));regionSub.setText(r[2]+" · "+r[4]+(running?I18n.tr("   停止后可更改"):""));region.setAlpha(running||busy?.58f:1f);
        boolean recovery=app.prefs().getBoolean("restorePending",false);
        boolean failure=app.prefs().contains("sessionFailure");
        status.setText(busy?I18n.tr("●  正在处理"):recovery?I18n.tr("●  操作未完成 · 待恢复"):failure?I18n.tr("●  启动未成功"):running?I18n.tr("●  会话运行中"):I18n.tr("●  已停止"));heroTitle.setText(busy?I18n.tr("稍候片刻"):recovery?I18n.tr("等待恢复"):failure?I18n.tr("改卡未成功"):running?I18n.tr("保持目的地"):I18n.tr("准备启程"));heroSub.setText(recovery?I18n.tr("已停止持续检查，原配置备份已保留"):failure?I18n.tr("请查看下方失败原因，可重新尝试"):I18n.region(r)+" · "+I18n.slots(app.mask())+(running?I18n.tr(" · 可随时停止"):I18n.tr(" · 点击播放键开始")));
        for(int i=0;i<3;i++){int mask=i==0?3:i==1?1:2;boolean selected=app.mask()==mask;slots[i].setBackground(bg(selected?TEAL:Color.TRANSPARENT,11));slots[i].setTextColor(selected?colors.onAccent:MUTED);slots[i].setAlpha(running||busy?.6f:1f);slots[i].setEnabled(!running&&!busy);}
        if(app.cards!=null){try{StringBuilder s=new StringBuilder();JSONArray cards=app.cards.getJSONArray("cards");for(int i=0;i<cards.length();i++){JSONObject c=cards.getJSONObject(i);if(i>0)s.append('\n');s.append(I18n.tr("卡 ")).append(i+1).append("  ·  ");if(c.optInt("subId",-1)<0)s.append(I18n.tr("未检测到 SIM"));else s.append(c.optString("carrier")).append("  /  ").append(c.optString("country","—").toUpperCase(java.util.Locale.ROOT));}simStatus.setText(s);}catch(Exception ignored){}}
        boolean ready=app.authorized();authTitle.setText(I18n.tr("内置后台 · ")+(ready?I18n.tr("已激活"):I18n.tr("待激活")));authDetail.setText(I18n.message(app.message));dockTitle.setText(busy?I18n.tr("正在处理…"):recovery?I18n.tr("重试恢复"):running?I18n.tr("停止并恢复"):failure?I18n.tr("重新尝试"):I18n.tr("一键启程"));dockSub.setText(running?I18n.tr("点击停止键恢复原配置"):ready?I18n.region(r)+" · "+I18n.slots(app.mask()):I18n.tr("首次使用，先激活内置后台"));play.state(running,busy,recovery||failure);
    }
}
