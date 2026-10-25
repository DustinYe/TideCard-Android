package app.tidecard;

public final class Regions {
    public static final String[][] ALL = {
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
    public static String[] get(String iso) { for (String[] r : ALL) if(r[0].equals(iso))return r; return ALL[0]; }
    public static String slots(int mask) { return mask == 3 ? "双卡" : mask == 1 ? "卡 1" : "卡 2"; }
}
