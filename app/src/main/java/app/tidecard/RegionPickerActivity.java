package app.tidecard;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;

/** Full-height, scrollable dial keeps search, a draft preview and an explicit confirmation. */
public final class RegionPickerActivity extends ThemedActivity {
    private RegionWheelModel model;private RegionDialView dial;private TextView selection,counter;private Button confirm,previous,next;private EditText search;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(colors.ink);t.setLineSpacing(dp(4),1);return t;}
    private void add(LinearLayout parent,View child,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(child,p);}
    private Button button(String label){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setMinHeight(dp(48));b.setTextColor(colors.accent);b.setPadding(dp(14),dp(10),dp(14),dp(10));GradientDrawable background=new GradientDrawable();background.setColor(colors.soft);background.setCornerRadius(dp(26));b.setBackground(background);return b;}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);TideApp app=(TideApp)getApplication();model=new RegionWheelModel(saved==null?getIntent().getStringExtra("country"):saved.getString("draft"));if(saved!=null)model.filter(saved.getString("query",""));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(colors.background);scroll.setFillViewport(true);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);scroll.addView(root);setContentView(scroll);
        root.setOnApplyWindowInsetsListener((v,in)->{android.graphics.Insets bars=in.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(dp(22)+bars.left,dp(10)+bars.top,dp(22)+bars.right,dp(24)+bars.bottom);return in;});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);Button back=button("‹ 返回");back.setOnClickListener(v->finish());header.addView(back);TextView title=text("选择目的地",22);title.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));add(root,header,0);
        add(root,text("向世界，转动一格。",28),18);TextView hint=text("旋转圆盘或点选地区码，确认后才会保存。",13);hint.setTextColor(colors.muted);add(root,hint,7);
        search=new EditText(this);search.setId(R.id.region_search);search.setSingleLine(true);search.setHint("搜索国家 / 地区 · 中文、English、ISO");search.setTextSize(14);search.setTextColor(colors.ink);search.setHintTextColor(colors.muted);search.setPadding(dp(17),dp(12),dp(17),dp(12));GradientDrawable field=new GradientDrawable();field.setColor(colors.surface);field.setCornerRadius(dp(24));search.setBackground(field);add(root,search,22);
        dial=new RegionDialView(this,model,this::render);dial.setId(R.id.region_dial);add(root,dial,16);
        counter=text("",12);counter.setGravity(Gravity.CENTER);counter.setTextColor(colors.muted);add(root,counter,10);
        LinearLayout arrows=new LinearLayout(this);previous=button("‹  上一个");previous.setId(R.id.region_previous);previous.setContentDescription("选择上一个地区");previous.setOnClickListener(v->dial.step(-1));next=button("下一个  ›");next.setId(R.id.region_next);next.setContentDescription("选择下一个地区");next.setOnClickListener(v->dial.step(1));arrows.addView(previous,new LinearLayout.LayoutParams(0,-2,1));arrows.addView(next,new LinearLayout.LayoutParams(0,-2,1));add(root,arrows,6);
        selection=text("",18);selection.setId(R.id.region_selection);selection.setGravity(Gravity.CENTER);add(root,selection,14);
        TextView advice=text("建议目标地区与 VPN 出口地区相同。",12);advice.setTextColor(colors.muted);advice.setGravity(Gravity.CENTER);add(root,advice,9);
        confirm=button("使用此地区");confirm.setId(R.id.region_confirm);confirm.setTextColor(colors.onAccent);GradientDrawable action=new GradientDrawable();action.setColor(colors.accent);action.setCornerRadius(dp(28));confirm.setBackground(action);confirm.setPadding(dp(18),dp(12),dp(18),dp(12));confirm.setOnClickListener(v->{String[] row=model.selected();if(row==null)return;if(app.active()||app.busy){Toast.makeText(this,"请先停止当前会话再更改地区",Toast.LENGTH_SHORT).show();return;}setResult(RESULT_OK,new Intent().putExtra("country",row[0]));finish();});add(root,confirm,20);
        search.setText(model.query());search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){model.filter(s.toString());dial.refresh();render();}public void afterTextChanged(Editable e){}});root.setFocusableInTouchMode(true);root.requestFocus();render();
    }
    private void render(){if(confirm==null)return;String[] row=model.selected();boolean can=row!=null;previous.setEnabled(model.size()>1);next.setEnabled(model.size()>1);confirm.setEnabled(can);confirm.setAlpha(can?1f:.45f);counter.setText(can?String.format(java.util.Locale.ROOT,"%02d / %02d",model.index()+1,model.size())+"  ·  共 "+Regions.ALL.length+" 个国家和地区":"没有匹配项 · 可尝试完整名称或两位地区码");selection.setText(can?row[1]+"\n"+row[2]+" · "+row[4]:"试试其他搜索词");confirm.setText(can?"使用 "+row[1]:"请选择地区");}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);String[] row=model.selected();out.putString("draft",row==null?"jp":row[0]);out.putString("query",model.query());}
}
