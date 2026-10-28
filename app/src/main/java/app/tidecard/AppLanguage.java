package app.tidecard;

import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import java.util.Locale;

/** Uses Android's app-language setting on API 33+, with an app-local fallback on 30–32. */
public final class AppLanguage {
    public static final String KEY="language";
    public static String mode(Context c){
        if(Build.VERSION.SDK_INT>=33){LocaleList locales=c.getSystemService(LocaleManager.class).getApplicationLocales();return locales.isEmpty()?"system":normalize(locales.get(0).toLanguageTag());}
        return normalize(c.getSharedPreferences("tidecard",0).getString(KEY,"system"));
    }
    public static String normalize(String tag){if(tag==null||tag.equals("system")||tag.isEmpty())return "system";Locale l=Locale.forLanguageTag(tag);return l.getLanguage().equals("zh")?(l.getScript().equals("Hant")||l.getCountry().matches("TW|HK|MO")?"zh-Hant":"zh-Hans"):"en";}
    public static Locale locale(Context c){String mode=mode(c);if(mode.equals("system"))mode=normalize(Resources.getSystem().getConfiguration().getLocales().get(0).toLanguageTag());return Locale.forLanguageTag(mode.equals("system")?"en":mode);}
    public static Context wrap(Context c){Configuration config=new Configuration(c.getResources().getConfiguration());config.setLocales(new LocaleList(locale(c)));return c.createConfigurationContext(config);}
    public static void set(Context c,String value){String normalized=normalize(value);if(Build.VERSION.SDK_INT>=33)c.getSystemService(LocaleManager.class).setApplicationLocales(normalized.equals("system")?LocaleList.getEmptyLocaleList():LocaleList.forLanguageTags(normalized));else if(!c.getSharedPreferences("tidecard",0).edit().putString(KEY,normalized).commit())throw new IllegalStateException("Language preference could not be saved");I18n.invalidate();}
    private AppLanguage(){}
}
