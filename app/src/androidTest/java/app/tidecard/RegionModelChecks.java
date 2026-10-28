package app.tidecard;

import java.util.*;

/** Host checks: catalog completeness, exact legacy recovery names, draft wheel and theme decisions. */
public final class RegionModelChecks {
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        require(Regions.ALL.length==249,"catalog count");Set<String> codes=new HashSet<>();
        for(String[] row:Regions.ALL){require(row.length==5&&row[0].matches("[a-z]{2}")&&codes.add(row[0]),"duplicate/invalid code");require(!row[1].isEmpty()&&!row[2].isEmpty()&&row[4].equals(row[0].toUpperCase(Locale.ROOT)),"missing display label");String raw=CarrierRecovery.COUNTRY+" = "+row[0]+"\n"+CarrierRecovery.FLAG+" = true\n"+CarrierRecovery.NAME+" = "+row[3];require(CarrierRecovery.compatible(raw),"known recovery tuple "+row[0]);}
        require(codes.equals(new HashSet<>(Arrays.asList(Locale.getISOCountries()).stream().map(v->v.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toList()))),"ISO catalog differs from host ISO data");
        String[][] legacy={{"jp","NTT DOCOMO"},{"us","T-Mobile"},{"sg","Singtel"},{"gb","EE"},{"de","Telekom"},{"fr","Orange"},{"ca","Rogers"},{"au","Telstra"},{"kr","SK Telecom"},{"tw","Chunghwa Telecom"}};
        for(String[] row:legacy)require(Regions.get(row[0])[3].equals(row[1]),"legacy recovery name changed");
        require("TideCard NL".equals(Regions.get("nl")[3]),"new labels must not invent a physical carrier");
        require("us".equals(Regions.search("US").get(0)[0]),"exact code priority");require("jp".equals(Regions.search("日本").get(0)[0]),"Chinese search");require("nl".equals(Regions.search("Netherlands").get(0)[0]),"exact English name priority");require(Regions.search("there-is-no-country").isEmpty(),"empty result");
        RegionWheelModel wheel=new RegionWheelModel("gb");require("gb".equals(wheel.selected()[0]),"initial selection");wheel.step(-1);wheel.step(1);require("gb".equals(wheel.selected()[0]),"reverse rotation");wheel.step(249);require("gb".equals(wheel.selected()[0]),"full wrap");Set<String> neighbors=new HashSet<>();for(int i=-3;i<=4;i++)require(neighbors.add(wheel.neighbor(i)[0]),"duplicate visible chips");
        wheel.filter("Netherlands");require("nl".equals(wheel.selected()[0]),"English selection");wheel.filter("新加坡");require(wheel.size()==1&&"sg".equals(wheel.selected()[0]),"single result");wheel.step(10);require("sg".equals(wheel.selected()[0]),"single-result wrap");wheel.filter("nonexistent-region");require(wheel.selected()==null,"empty selection");wheel.step(1);require(wheel.selected()==null,"empty rotation");wheel.filter("");require("sg".equals(wheel.selected()[0]),"draft retained after search");
        require(AppearanceMode.dark("system",true)&&!AppearanceMode.dark("system",false),"follow system");require(AppearanceMode.dark("dark",false)&&!AppearanceMode.dark("light",true),"manual theme");require("system".equals(AppearanceMode.normalize("unknown")),"invalid theme fallback");
        for(boolean dark:new boolean[]{false,true}){AppPalette p=new AppPalette(dark);require(contrast(p.ink,p.background)>7,"body contrast");require(contrast(p.muted,p.background)>4.5,"secondary text contrast");require(contrast(p.accent,p.surface)>4.5,"accent contrast");require(contrast(p.onAccent,p.accent)>4.5,"button contrast");}
        System.out.println("PASS 249 ISO regions, legacy recovery tuples, multilingual search, draft wheel, wrap and theme contrast");
    }
    private static double contrast(int a,int b){double l=luminance(a),r=luminance(b);return (Math.max(l,r)+.05)/(Math.min(l,r)+.05);}
    private static double luminance(int color){double[] c={((color>>16)&255)/255.,((color>>8)&255)/255.,(color&255)/255.};for(int n=0;n<3;n++)c[n]=c[n]<=.04045?c[n]/12.92:Math.pow((c[n]+.055)/1.055,2.4);return .2126*c[0]+.7152*c[1]+.0722*c[2];}
}
