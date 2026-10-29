package app.tidecard;

/** Plain JVM regression: legacy Binder values are exercised through reflection, without a phone. */
public final class SubscriptionIdChecks {
    public static final class Legacy {public int[] getSubId(int slot){return slot==0?new int[]{17}:null;}}
    public static final class Modern {public int getSubId(int slot){return slot==0?17:-1;}}
    private static int count;
    private static void require(boolean ok,String message){count++;if(!ok)throw new AssertionError(message);}
    private static Object read(Object service,int slot)throws Exception{return service.getClass().getMethod("getSubId",int.class).invoke(service,slot);}
    private static int oldRead(Object service,int slot)throws Exception{return (Integer)read(service,slot);}
    private static void rejects(Object value){boolean rejected=false;try{SubscriptionIdResolver.from(value);}catch(IllegalStateException expected){rejected=true;}require(rejected,"ambiguous or unsupported ID accepted");}
    public static void main(String[] args)throws Exception{
        boolean nullFailure=false;try{oldRead(new Legacy(),1);}catch(NullPointerException expected){nullFailure=true;}
        require(nullFailure,"original null-unboxing failure not reproduced");
        boolean arrayFailure=false;try{oldRead(new Legacy(),0);}catch(ClassCastException expected){arrayFailure=true;}
        require(arrayFailure,"original legacy-array failure not reproduced");
        for(Object service:new Object[]{new Legacy(),new Modern()}){
            require(SubscriptionIdResolver.from(read(service,0))==17,"active slot mapping lost");
            require(SubscriptionIdResolver.from(read(service,1))==-1,"empty slot not excluded");
            for(int mask:new int[]{1,2,3}){int active=0;for(int slot=0;slot<2;slot++)if((mask&(1<<slot))!=0&&SubscriptionIdResolver.from(read(service,slot))>=0)active++;require(active==(mask==2?0:1),"empty slot substituted another SIM");}
        }
        require(SubscriptionIdResolver.from(new int[0])==-1,"empty array");
        require(SubscriptionIdResolver.from(new int[]{-1,-2,Integer.MAX_VALUE})==-1,"sentinel array");
        require(SubscriptionIdResolver.from(new int[]{-1,17,17})==17,"duplicate same ID");
        require(SubscriptionIdResolver.from(Integer.MAX_VALUE)==-1,"default-subscription sentinel");
        require(SubscriptionIdResolver.from(0)==0,"valid zero ID");
        rejects(new int[]{17,18});rejects("17");rejects(17L);rejects(17.5);rejects(new Integer[]{17});
        System.out.println("PASS "+count+" checks: original failure reproduced; scalar/legacy/null mappings; slot isolation; ambiguous and unsupported responses rejected");
    }
}
