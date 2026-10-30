package app.tidecard;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.WindowInsets;
import android.widget.*;
import org.json.*;

public final class CleanupActivity extends ThemedActivity {
    private TideApp app;
    private CleanupController controller;
    private TextView status,report;
    private Button check,clear;
    private final Button[] slots=new Button[3];
    private final int[] masks={3,1,2};
    private final Runnable observer=this::render;
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(colors.ink);view.setLineSpacing(dp(4),1);return view;}
    private Button button(String value){Button view=new Button(this);view.setText(value);view.setAllCaps(false);view.setTextColor(colors.accent);view.setMinHeight(dp(48));return view;}
    private void add(LinearLayout root,android.view.View view,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);root.addView(view,p);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);app=(TideApp)getApplication();controller=app.cleanup;
        getWindow().setStatusBarColor(colors.background);getWindow().setNavigationBarColor(colors.background);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(colors.background);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);scroll.addView(root);setContentView(scroll);
        root.setOnApplyWindowInsetsListener((v,in)->{android.graphics.Insets bars=in.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(24)+bars.left,dp(16)+bars.top,dp(24)+bars.right,dp(32)+bars.bottom);return in;});
        Button back=button(I18n.tr("‹ 返回"));back.setOnClickListener(v->finish());add(root,back,0);
        add(root,text(I18n.tr("清除旧地区配置"),27),18);
        add(root,text(I18n.tr("清除其他改卡工具或旧版本留下的地区与运营商名称覆盖，包括持久配置。请先停止其他改卡工具，避免它们重新写入。"),14),12);
        add(root,text(I18n.tr("仅处理地区和运营商名称字段；发现其他字段时会保留配置并停止。"),13),10);
        LinearLayout selection=new LinearLayout(this);
        for(int n=0;n<3;n++){final int value=masks[n];Button choice=button(I18n.slots(value));choice.setId(n==0?R.id.cleanup_both:n==1?R.id.cleanup_one:R.id.cleanup_two);choice.setOnClickListener(v->controller.select(value));slots[n]=choice;selection.addView(choice,new LinearLayout.LayoutParams(0,-2,1));}add(root,selection,18);
        status=text("",15);status.setId(R.id.cleanup_status);add(root,status,16);
        report=text("",14);report.setId(R.id.cleanup_report);report.setTextIsSelectable(true);report.setPadding(dp(16),dp(16),dp(16),dp(16));GradientDrawable bg=new GradientDrawable();bg.setColor(colors.surface);bg.setCornerRadius(dp(18));report.setBackground(bg);add(root,report,12);
        check=button(I18n.tr("检查旧配置"));check.setId(R.id.cleanup_check);check.setOnClickListener(v->controller.check());add(root,check,18);
        clear=button(I18n.tr("一键清除旧配置"));clear.setId(R.id.cleanup_clear);clear.setOnClickListener(v->confirm());add(root,clear,8);
        Button activate=button(I18n.tr("激活 / 配对  ↗"));activate.setOnClickListener(v->startActivity(new Intent(this,ActivationActivity.class)));add(root,activate,10);render();
    }
    private String report(JSONObject preview){
        if(preview==null)return "";StringBuilder result=new StringBuilder();JSONArray cards=preview.optJSONArray("cards");if(cards==null)return "";
        for(int n=0;n<cards.length();n++){JSONObject card=cards.optJSONObject(n);if(card==null)continue;if(result.length()>0)result.append("\n\n");
            result.append(I18n.tr("卡 ")).append(card.optInt("slot")+1).append(" · ");
            if(!card.optBoolean("present")){result.append(I18n.tr("未检测到 SIM"));continue;}
            if(!card.optBoolean("needsClear")){result.append(I18n.tr("无需清除"));continue;}
            result.append(I18n.tr("当前国家码：")).append(card.optString("country").toUpperCase(java.util.Locale.ROOT));
            result.append("\n").append(I18n.tr("恢复目标：")).append(card.optString("originalCountry").toUpperCase(java.util.Locale.ROOT));
            if(card.optBoolean("temporary"))result.append("\n").append(I18n.tr("临时地区覆盖"));
            if(card.optBoolean("persistent"))result.append("\n").append(I18n.tr("持久地区覆盖"));
            if(!card.optBoolean("temporary")&&!card.optBoolean("persistent"))result.append("\n").append(I18n.tr("国家码回读异常"));
        }return result.toString();
    }
    private void confirm(){
        if(!controller.canClear())return;JSONObject preview=controller.preview;String token=preview.optString("token");
        new AlertDialog.Builder(this).setTitle(I18n.tr("清除所选卡槽的旧配置？"))
            .setMessage(report(preview)+"\n\n"+I18n.tr("这会移除以上卡槽的地区和运营商名称覆盖，并按系统原配置恢复。清除后请重新开始改卡。"))
            .setNegativeButton(I18n.tr("取消"),null).setPositiveButton(I18n.tr("确认清除"),(d,w)->controller.clear(token)).show();
    }
    private void render(){
        if(check==null)return;
        String message=controller.blocked()?"请先在主界面停止并恢复当前会话，再清除旧配置":!app.authorized()?"请先激活内置后台，再检查旧配置":controller.status;
        status.setText(I18n.message(message));report.setText(report(controller.preview));report.setVisibility(report.length()==0?android.view.View.GONE:android.view.View.VISIBLE);
        check.setEnabled(!app.busy&&!controller.blocked()&&app.authorized());clear.setEnabled(controller.canClear());
        for(int n=0;n<3;n++){slots[n].setText((controller.mask==masks[n]?"● ":"○ ")+I18n.slots(masks[n]));slots[n].setEnabled(!app.busy);}
    }
    @Override protected void onResume(){super.onResume();app.observers.addIfAbsent(observer);render();}
    @Override protected void onPause(){app.observers.remove(observer);super.onPause();}
}
