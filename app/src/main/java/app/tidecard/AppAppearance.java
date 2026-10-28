package app.tidecard;

import android.content.*;
import android.content.res.*;

/** Local context override works on API 30+ without changing the phone's global night mode. */
public final class AppAppearance {
    public static final String KEY="appearance";
    public static String mode(Context c){return AppearanceMode.normalize(c.getSharedPreferences("tidecard",Context.MODE_PRIVATE).getString(KEY,"system"));}
    public static boolean dark(Context c){return AppearanceMode.dark(mode(c),(Resources.getSystem().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);}
    public static Context wrap(Context c){Configuration config=new Configuration(c.getResources().getConfiguration());config.uiMode=(config.uiMode&~Configuration.UI_MODE_NIGHT_MASK)|(dark(c)?Configuration.UI_MODE_NIGHT_YES:Configuration.UI_MODE_NIGHT_NO);return c.createConfigurationContext(config);}
    public static void set(Context c,String mode){if(!c.getSharedPreferences("tidecard",Context.MODE_PRIVATE).edit().putString(KEY,AppearanceMode.normalize(mode)).commit())throw new IllegalStateException("无法保存外观选择");}
    private AppAppearance(){}
}
