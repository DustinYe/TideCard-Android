package app.tidecard;

import android.app.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Readable license and attribution copies shipped inside the APK. */
public final class LicensesActivity extends ThemedActivity {
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(colors.background);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(24),dp(20),dp(24));scroll.addView(root);setContentView(scroll);
        root.setOnApplyWindowInsetsListener((v,in)->{android.graphics.Insets bars=in.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(20)+bars.left,dp(20)+bars.top,dp(20)+bars.right,dp(20)+bars.bottom);return in;});
        TextView title=new TextView(this);title.setText("开源许可与来源");title.setTextSize(25);title.setTextColor(colors.ink);root.addView(title);
        String[][] items={{"来源、改动与致谢","NOTICE.txt"},{"第三方组件说明","THIRD_PARTY_NOTICES.txt"},{"Shizuku · Apache 2.0","Shizuku-LICENSE.txt"},{"Conscrypt · Apache 2.0 / 附带声明","Conscrypt-LICENSE.txt"},{"BoringSSL · 2025 原生库许可","BoringSSL-20250114-LICENSE.txt"},{"BoringSSL · Conscrypt 许可副本","BoringSSL-LICENSE.txt"},{"LLVM libc++ · Apache 2.0 with LLVM Exceptions","LLVM-libcxx-LICENSE.txt"},{"Nrfr · 参考方案许可","Nrfr-LICENSE.txt"}};
        for(String[] item:items){Button button=new Button(this);button.setAllCaps(false);button.setText(item[0]);root.addView(button,new LinearLayout.LayoutParams(-1,-2));button.setOnClickListener(v->show(item[0],item[1]));}
        Button close=new Button(this);close.setText("返回");root.addView(close);close.setOnClickListener(v->finish());
    }
    private void show(String title,String file){
        try(InputStream in=getAssets().open(file)){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);
            TextView text=new TextView(this);text.setText(bytes.toString(StandardCharsets.UTF_8.name()));text.setTextSize(13);text.setTextIsSelectable(true);text.setPadding(dp(18),dp(14),dp(18),dp(14));
            ScrollView scroll=new ScrollView(this);scroll.addView(text);new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("关闭",null).show();
        }catch(IOException e){Toast.makeText(this,"无法读取许可文件",Toast.LENGTH_LONG).show();}
    }
}
