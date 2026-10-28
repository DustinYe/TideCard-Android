package app.tidecard;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.WindowInsetsController;

public class ThemedActivity extends Activity {
    protected AppPalette colors;private String appearance,language;
    @Override protected void attachBaseContext(Context base){super.attachBaseContext(AppLanguage.wrap(AppAppearance.wrap(base)));}
    @Override protected void onCreate(Bundle saved){
        language=AppLanguage.locale(this).toLanguageTag();appearance=AppAppearance.mode(this);colors=new AppPalette(AppAppearance.dark(this));
        setTheme(colors.dark?R.style.TideDarkTheme:R.style.AppTheme);super.onCreate(saved);
        getWindow().setStatusBarColor(colors.background);getWindow().setNavigationBarColor(colors.background);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(colors.background));
    }
    @Override protected void onPostResume(){
        super.onPostResume();WindowInsetsController controller=getWindow().getDecorView().getWindowInsetsController();
        if(controller!=null)controller.setSystemBarsAppearance(colors.dark?0:WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
    }
    @Override protected void onResume(){super.onResume();if(!appearance.equals(AppAppearance.mode(this))||colors.dark!=AppAppearance.dark(this)||!language.equals(AppLanguage.locale(this).toLanguageTag()))recreate();}
}
