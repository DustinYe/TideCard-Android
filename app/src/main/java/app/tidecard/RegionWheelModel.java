package app.tidecard;

import java.util.List;

/** Draft-only wheel state. Nothing here touches preferences, services or SIM configuration. */
public final class RegionWheelModel {
    private List<String[]> rows;private int index;private String draft,query="";
    public RegionWheelModel(String iso){draft=Regions.get(iso)[0];filter("");}
    public void filter(String text){query=text==null?"":text;rows=Regions.search(query);index=0;for(int i=0;i<rows.size();i++)if(rows.get(i)[0].equals(draft)){index=i;break;}if(!rows.isEmpty())draft=rows.get(index)[0];}
    public void step(int steps){if(rows.isEmpty())return;index=Math.floorMod(index+steps,rows.size());draft=rows.get(index)[0];}
    public String[] selected(){return rows.isEmpty()?null:rows.get(index);}
    public String[] neighbor(int offset){return rows.isEmpty()?null:rows.get(Math.floorMod(index+offset,rows.size()));}
    public int size(){return rows.size();}public int index(){return index;}public String query(){return query;}
}
