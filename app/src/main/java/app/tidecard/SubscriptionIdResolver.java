package app.tidecard;

/** Decode the scalar and legacy array forms of ISub.getSubId without guessing another slot. */
public final class SubscriptionIdResolver {
    public static final int INVALID=-1;
    private static boolean valid(int id){return id>=0&&id!=Integer.MAX_VALUE;}
    public static int from(Object value){
        // Legacy ISub implementations may return null when this slot has no active subscription.
        if(value==null)return INVALID;
        if(value instanceof Integer){int id=(Integer)value;return valid(id)?id:INVALID;}
        if(value instanceof int[]){
            int result=INVALID;
            for(int id:(int[])value){
                if(!valid(id))continue;
                if(result!=INVALID&&result!=id)throw new IllegalStateException("卡槽订阅信息存在多个有效编号，已停止操作");
                result=id;
            }
            return result;
        }
        throw new IllegalStateException("系统返回了不支持的卡槽订阅格式");
    }
    private SubscriptionIdResolver(){}
}
