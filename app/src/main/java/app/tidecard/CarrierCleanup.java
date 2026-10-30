package app.tidecard;

import java.io.IOException;
import java.util.*;

/** Explicitly confirmed removal of region-only overrides, including persistent ones. */
final class CarrierCleanup {
    static final long PLAN_TTL = 120_000, SETTLE_TIMEOUT = 8_000;
    interface Access {
        List<Card> read(int mask) throws Exception;
        void prime(Card card) throws Exception;
        void reset(Card card) throws Exception;
        long now();
        void pause() throws Exception;
    }
    static final class Card {
        final int slot, subId;
        final String temporary, persistent, country, originalCountry;
        final Map<String,String> baseline;
        Card(int slot,int subId,String temporary,String persistent,String country,String originalCountry,Map<String,String> baseline) {
            this.slot=slot;this.subId=subId;this.temporary=temporary;this.persistent=persistent;
            this.country=country;this.originalCountry=originalCountry;
            this.baseline=Collections.unmodifiableMap(new HashMap<>(baseline));
        }
        boolean present(){return subId>=0;}
        boolean resetPersistent(){return !CarrierRecovery.empty(persistent);}
        boolean needsClear(){return present()&&(!CarrierRecovery.empty(temporary)||!CarrierRecovery.empty(persistent)||!originalCountry.equalsIgnoreCase(country));}
        Map<String,String> primeValues(){Map<String,String> result=new HashMap<>(baseline);result.put(CarrierRecovery.COUNTRY,originalCountry);return result;}
        boolean same(Card other)throws IOException {
            return slot==other.slot&&subId==other.subId&&country.equals(other.country)&&originalCountry.equals(other.originalCountry)
                &&baseline.equals(other.baseline)&&CarrierRecovery.cleanupFields(temporary).equals(CarrierRecovery.cleanupFields(other.temporary))
                &&CarrierRecovery.cleanupFields(persistent).equals(CarrierRecovery.cleanupFields(other.persistent));
        }
    }
    static final class Preview {
        final String token;
        final int mask;
        final List<Card> cards;
        final long created;
        Preview(String token,int mask,List<Card> cards,long created){this.token=token;this.mask=mask;this.cards=Collections.unmodifiableList(new ArrayList<>(cards));this.created=created;}
        int count(){int count=0;for(Card c:cards)if(c.needsClear())count++;return count;}
    }
    static final class Failure extends IOException {
        final int startedMask,completedMask;
        Failure(int started,int completed,Throwable cause){super("清除未完成，请重新检查配置后重试："+(cause.getMessage()==null?cause.getClass().getSimpleName():cause.getMessage()),cause);startedMask=started;completedMask=completed;}
    }
    private final Access access;
    private Preview pending;
    CarrierCleanup(Access access){this.access=access;}
    private List<Card> read(int mask)throws Exception {
        if(mask<1||mask>3)throw new IOException("无效的卡槽选择");
        List<Card> cards=access.read(mask);int seen=0;
        for(Card c:cards){
            if(c.slot<0||c.slot>1||(mask&(1<<c.slot))==0||(seen&(1<<c.slot))!=0)throw new IOException("无法核对卡槽配置，未做修改");
            seen|=1<<c.slot;if(!c.present())continue;
            try{CarrierRecovery.cleanupFields(c.temporary);CarrierRecovery.cleanupFields(c.persistent);}
            catch(IOException e){throw new IOException("卡 "+(c.slot+1)+" 包含其他字段或无法识别的配置，已保留且未修改");}
            if(!c.originalCountry.matches("[a-z]{2}")||!c.baseline.keySet().equals(new HashSet<>(Arrays.asList(CarrierRecovery.COUNTRY,CarrierRecovery.FLAG,CarrierRecovery.NAME)))
                ||!("true".equals(c.baseline.get(CarrierRecovery.FLAG))||"false".equals(c.baseline.get(CarrierRecovery.FLAG))))
                throw new IOException("无法核对原始地区配置，未做修改");
        }
        if(seen!=mask)throw new IOException("无法核对卡槽配置，未做修改");
        cards.sort(Comparator.comparingInt(c->c.slot));return cards;
    }
    synchronized Preview preview(int mask)throws Exception {
        pending=null;List<Card> cards=read(mask);
        if(cards.stream().noneMatch(Card::present))throw new IOException("所选卡槽没有可用 SIM 卡");
        Preview result=new Preview(UUID.randomUUID().toString(),mask,cards,access.now());
        if(result.count()>0)pending=result;return result;
    }
    synchronized int clear(String token)throws Exception {
        Preview plan=pending;pending=null;
        if(plan==null||!plan.token.equals(token)||access.now()-plan.created<0||access.now()-plan.created>PLAN_TTL)
            throw new IOException("检查结果已失效，请重新检查旧配置");
        List<Card> current=read(plan.mask);
        for(int i=0;i<current.size();i++)if(!plan.cards.get(i).same(current.get(i)))throw new IOException("配置已发生变化，请重新检查旧配置");
        int started=0,completed=0;
        try{
            for(Card card:plan.cards)if(card.needsClear()){
                Card before=read(1<<card.slot).get(0);
                if(!card.same(before))throw new IOException("配置已发生变化，请重新检查旧配置");
                started|=1<<card.slot;access.prime(card);await(card,false);
                // A fresh read immediately before reset refuses intervening foreign configuration.
                Card primed=read(1<<card.slot).get(0);
                if(!matches(card,primed,false))throw new IOException("配置已发生变化，请重新检查旧配置");
                access.reset(card);await(card,true);completed|=1<<card.slot;
            }
            for(Card card:plan.cards)if(card.needsClear()&&!matches(card,read(1<<card.slot).get(0),true))
                throw new IOException("配置被再次写入，请停止其他改卡工具后重试");
            return completed;
        }catch(Exception error){throw new Failure(started,completed,error);}
    }
    private boolean matches(Card original,Card current,boolean cleared)throws IOException {
        if(original.slot!=current.slot||original.subId!=current.subId||!original.baseline.equals(current.baseline)
            ||!original.originalCountry.equals(current.originalCountry))throw new IOException("SIM 或原始配置已变更，请重新检查");
        Map<String,String> temporary=CarrierRecovery.cleanupFields(current.temporary),persistent=CarrierRecovery.cleanupFields(current.persistent);
        if(cleared){if(!persistent.isEmpty()||!temporary.isEmpty())return false;}
        else if(!persistent.equals(CarrierRecovery.cleanupFields(original.persistent))||!temporary.equals(original.primeValues()))return false;
        return original.originalCountry.equalsIgnoreCase(current.country);
    }
    private void await(Card original,boolean cleared)throws Exception {
        long start=access.now();
        do{if(matches(original,read(1<<original.slot).get(0),cleared))return;access.pause();}while(access.now()-start<SETTLE_TIMEOUT);
        throw new IOException(cleared?"尚未确认旧配置已清除，请停止其他改卡工具后重新检查":"原始国家码尚未回读，请重新检查后重试");
    }
}
