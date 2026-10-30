package app.tidecard;

import java.io.IOException;
import java.util.*;
import java.util.regex.*;

/** Reads only the three keys TideCard changes; unknown temporary override fields stay protected. */
final class CarrierRecovery {
    static final String COUNTRY="sim_country_iso_override_string",FLAG="carrier_name_override_bool",NAME="carrier_name_string";
    private static final Set<String> KEYS=new HashSet<>(Arrays.asList(COUNTRY,FLAG,NAME));
    private static final Pattern FIELD=Pattern.compile("^\\s*([a-zA-Z0-9_]+)\\s*=\\s*(.*)$");
    static boolean empty(String value){return value.trim().isEmpty()||value.trim().equals("null");}
    static String section(String dump,int slot,String label)throws IOException{
        dump=dump.replace("\r\n","\n").replace('\r','\n');
        Matcher phone=Pattern.compile("(?m)^Phone Id = "+slot+"\\s*$").matcher(dump);
        if(!phone.find())throw new IOException("无法读取卡 "+(slot+1)+" 的系统配置，未做修改");
        int start=phone.end(),end=dump.indexOf("\nPhone Id = ",start);String part=dump.substring(start,end<0?dump.length():end);
        String marker=label+" :";int at=part.indexOf(marker);
        if(at<0)throw new IOException("缺少系统配置层 "+label+"，未做修改");
        String rest=part.substring(at+marker.length());int blank=rest.indexOf("\n\n");
        return (blank<0?rest:rest.substring(0,blank)).trim();
    }
    private static Map<String,String> fields(String raw,boolean strict)throws IOException{
        Map<String,String> result=new HashMap<>();if(empty(raw))return result;
        for(String line:raw.split("\\r?\\n")){
            if(line.trim().isEmpty())continue;Matcher match=FIELD.matcher(line);
            if(!match.matches()){if(strict)throw new IOException("无法识别已有覆盖配置");continue;}
            String key=match.group(1);if(!KEYS.contains(key)){if(strict)throw new IOException("已有覆盖包含其他字段");continue;}
            if(result.put(key,match.group(2).trim())!=null)throw new IOException("已有配置存在重复字段");
        }
        return result;
    }
    static boolean compatible(String raw){
        try{Map<String,String> values=fields(raw,true);if(!values.keySet().equals(KEYS)||!"true".equals(values.get(FLAG)))return false;
            for(String[] region:Regions.ALL)if(region[0].equals(values.get(COUNTRY))&&region[3].equals(values.get(NAME)))return true;
        }catch(IOException ignored){}return false;
    }
    static boolean onlyManagedFields(String raw){if(empty(raw))return true;try{return fields(raw,true).keySet().equals(KEYS);}catch(IOException ignored){return false;}}
    /** Cleanup may discard partial or malformed values, but never fields outside the three region keys. */
    static Map<String,String> cleanupFields(String raw)throws IOException{return fields(raw,true);}
    static Map<String,String> baseline(String defaults,String platform,String carrier)throws IOException{
        Map<String,String> values=fields(defaults,false);
        if(!values.keySet().equals(KEYS))throw new IOException("原始系统配置不完整，未做修改");
        values.putAll(fields(platform,false));values.putAll(fields(carrier,false));
        String iso=values.get(COUNTRY),flag=values.get(FLAG);
        if(!iso.isEmpty()&&!iso.matches("[a-zA-Z]{2}"))throw new IOException("无法核对原始国家码，未做修改");
        if(!"true".equals(flag)&&!"false".equals(flag))throw new IOException("无法核对原始名称配置，未做修改");
        values.put(COUNTRY,iso.toLowerCase(Locale.ROOT));return values;
    }
}
