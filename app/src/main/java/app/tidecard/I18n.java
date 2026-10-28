package app.tidecard;

import android.content.Context;
import java.util.*;

/** Android resource catalogs also translate canonical backend messages retained in old snapshots. */
public final class I18n {
    private static Context app;private static String tag;private static Map<String,String> catalog=Collections.emptyMap();private static List<String> sources=Collections.emptyList();
    static synchronized void init(Context c){app=c.getApplicationContext();invalidate();}
    static synchronized void invalidate(){tag=null;}
    private static synchronized void load(){if(app==null)return;String current=AppLanguage.locale(app).toLanguageTag();if(current.equals(tag))return;Context localized=AppLanguage.wrap(app);String[] from=localized.getResources().getStringArray(R.array.message_sources),to=localized.getResources().getStringArray(R.array.message_translations);if(from.length!=to.length)throw new IllegalStateException("Translation catalog length mismatch");Map<String,String> map=new HashMap<>();for(int n=0;n<from.length;n++)map.put(from[n],to[n]);List<String> order=new ArrayList<>(map.keySet());order.sort((a,b)->Integer.compare(b.length(),a.length()));catalog=map;sources=order;tag=current;}
    public static synchronized String tr(String source){load();return catalog.getOrDefault(source,source);}
    /** Single pass over the canonical source avoids cascading replacements in Traditional Chinese. */
    public static synchronized String message(String source){if(source==null)return "";load();String exact=catalog.get(source);if(exact!=null)return exact;StringBuilder out=new StringBuilder();for(int at=0;at<source.length();){String found=null;for(String key:sources)if(source.startsWith(key,at)){found=key;break;}if(found==null){out.append(source.charAt(at++));}else{out.append(catalog.get(found));at+=found.length();}}return out.toString();}
    public static String region(String[] row){if(app==null)return row[1];return new Locale("",row[4]).getDisplayCountry(AppLanguage.locale(app));}
    public static String slots(int mask){return tr(mask==3?"双卡":mask==1?"卡 1":"卡 2");}
    private I18n(){}
}
