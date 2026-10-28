package app.tidecard;

public final class Regions {
    private static final String[][] LEGACY = {
        {"jp", "日本", "Japan", "NTT DOCOMO", "JP"},
        {"us", "美国", "United States", "T-Mobile", "US"},
        {"sg", "新加坡", "Singapore", "Singtel", "SG"},
        {"gb", "英国", "United Kingdom", "EE", "GB"},
        {"de", "德国", "Germany", "Telekom", "DE"},
        {"fr", "法国", "France", "Orange", "FR"},
        {"ca", "加拿大", "Canada", "Rogers", "CA"},
        {"au", "澳大利亚", "Australia", "Telstra", "AU"},
        {"kr", "韩国", "South Korea", "SK Telecom", "KR"},
        {"tw", "中国台湾", "Taiwan", "Chunghwa Telecom", "TW"}
    };
    // Frozen ISO country codes; labels use Android's built-in locale data, never a network request.
    private static final String CODES="AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BV BW BY BZ CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE EG EH ER ES ET FI FJ FK FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU ID IE IL IM IN IO IQ IR IS IT JE JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK TL TM TN TO TR TT TV TW TZ UA UG UM US UY UZ VA VC VE VG VI VN VU WF WS YE YT ZA ZM ZW";
    public static final String[][] ALL=build();
    private static String[][] build(){
        java.util.LinkedHashMap<String,String[]> rows=new java.util.LinkedHashMap<>();for(String[] row:LEGACY)rows.put(row[0],row);
        for(String code:CODES.split(" ")){String iso=code.toLowerCase(java.util.Locale.ROOT);if(rows.containsKey(iso))continue;
            java.util.Locale locale=new java.util.Locale("",code);String name=locale.getDisplayCountry(java.util.Locale.SIMPLIFIED_CHINESE);
            if("CN".equals(code))name="中国大陆";else if("HK".equals(code))name="中国香港";else if("MO".equals(code))name="中国澳门";
            rows.put(iso,new String[]{iso,name,locale.getDisplayCountry(java.util.Locale.ENGLISH),"TideCard "+code,code});
        }return rows.values().toArray(new String[0][]);
    }
    public static String[] find(String iso){for(String[] r:ALL)if(r[0].equals(iso))return r;return null;}
    public static String[] get(String iso) { String[] row=find(iso);return row==null?ALL[0]:row; }
    public static java.util.List<String[]> search(String query){
        String q=normalize(query);java.util.ArrayList<String[]> result=new java.util.ArrayList<>();
        for(String[] row:ALL){String traditional=normalize(new java.util.Locale("",row[4]).getDisplayCountry(java.util.Locale.TRADITIONAL_CHINESE));if(q.isEmpty()||normalize(row[0]).contains(q)||normalize(row[1]).contains(q)||normalize(row[2]).contains(q)||traditional.contains(q)){if(!q.isEmpty()&&(row[0].equals(q)||normalize(row[1]).equals(q)||normalize(row[2]).equals(q)||traditional.equals(q)))result.add(0,row);else result.add(row);}}return result;
    }
    private static String normalize(String value){return java.text.Normalizer.normalize(value==null?"":value.trim().replace('臺','台'),java.text.Normalizer.Form.NFKD).replaceAll("\\p{M}+","").toLowerCase(java.util.Locale.ROOT);}
    public static String slots(int mask) { return mask == 3 ? "双卡" : mask == 1 ? "卡 1" : "卡 2"; }
}
