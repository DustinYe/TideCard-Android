package app.tidecard;

import org.json.*;
import java.net.URI;
import java.util.regex.*;

/** Only stable version tags and an unambiguous APK from the official repository are accepted. */
public final class ReleaseInfo {
    public static final String REPO="https://github.com/DustinYe/TideCard-Android";
    public final String version,url,digest,notes;public final long size;
    private ReleaseInfo(String v,String u,String d,String n,long s){version=v;url=u;digest=d;notes=n;size=s;}
    public static int compare(String left,String right){int[] a=parts(left),b=parts(right);for(int n=0;n<3;n++){int c=Integer.compare(a[n],b[n]);if(c!=0)return c;}return 0;}
    private static int[] parts(String v){Matcher m=Pattern.compile("^v?(0|[1-9][0-9]{0,7})\\.(0|[1-9][0-9]{0,7})\\.(0|[1-9][0-9]{0,7})$").matcher(v);if(!m.matches())throw new IllegalArgumentException("Invalid stable version");return new int[]{Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3))};}
    public static ReleaseInfo parse(JSONObject json)throws Exception{
        if(json.optBoolean("draft")||json.optBoolean("prerelease"))throw new IllegalArgumentException("Not a stable release");
        String tag=json.getString("tag_name");parts(tag);String version=tag.startsWith("v")?tag.substring(1):tag;
        JSONArray assets=json.getJSONArray("assets");JSONObject apk=null;for(int n=0;n<assets.length();n++){JSONObject a=assets.getJSONObject(n);if(a.optString("name").equals("TideCard-"+version+".apk")){if(apk!=null)throw new IllegalArgumentException("Ambiguous APK");apk=a;}}
        if(apk==null)throw new IllegalArgumentException("Release APK missing");String url=apk.getString("browser_download_url");URI uri=new URI(url);
        if(!url.startsWith(REPO+"/releases/download/"+tag+"/")||!"https".equals(uri.getScheme())||uri.getRawUserInfo()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("Unexpected release URL");
        String digest=apk.optString("digest","");if(!digest.matches("sha256:[a-fA-F0-9]{64}"))throw new IllegalArgumentException("Release SHA-256 missing");
        long size=apk.getLong("size");if(size<1||size>100L*1024*1024)throw new IllegalArgumentException("Unexpected APK size");
        return new ReleaseInfo(version,url,digest.substring(7).toLowerCase(java.util.Locale.ROOT),json.optString("body",""),size);
    }
}
