package app.tidecard;

import java.util.*;

/** Bounded, deduplicated serial resolution; late callbacks cannot finish a newer request. */
final class DiscoveryQueue<T> {
    static final class Ticket<T> {final String key;final T value;Ticket(String key,T value){this.key=key;this.value=value;}}
    private final LinkedHashMap<String,T> pending=new LinkedHashMap<>();
    private Ticket<T> active;
    void offer(String key,T value){if((active==null||!active.key.equals(key))&&pending.size()<64)pending.putIfAbsent(key,value);}
    Ticket<T> next(){if(active!=null||pending.isEmpty())return null;String key=pending.keySet().iterator().next();active=new Ticket<>(key,pending.remove(key));return active;}
    boolean finish(Ticket<T> ticket){if(ticket==null||active!=ticket)return false;active=null;return true;}
    void lost(String key){pending.remove(key);}
    void clear(){pending.clear();active=null;}
}
