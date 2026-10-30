package app.tidecard;

import java.io.IOException;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CarrierCleanupTest {
    static final String C=CarrierRecovery.COUNTRY,F=CarrierRecovery.FLAG,N=CarrierRecovery.NAME;
    static final Map<String,String> BASE=new HashMap<>();
    static{BASE.put(C,"");BASE.put(F,"false");BASE.put(N,"China Mobile");}
    static String raw(Map<String,String> values){StringBuilder out=new StringBuilder();new TreeMap<>(values).forEach((k,v)->out.append(k).append(" = ").append(v).append('\n'));return out.toString();}
    static CarrierCleanup.Card card(int slot,String temporary,String persistent,String country){return new CarrierCleanup.Card(slot,40+slot,temporary,persistent,country,"cn",BASE);}
    static CarrierCleanup.Card absent(int slot){return new CarrierCleanup.Card(slot,-1,"","","","",Collections.emptyMap());}
    static class Fake implements CarrierCleanup.Access {
        final Map<Integer,CarrierCleanup.Card> cards=new HashMap<>();
        final List<String> writes=new ArrayList<>();
        long time;boolean stuckPrime,stuckReset,denyReset;Runnable afterPrime,afterReset;
        Fake(){cards.put(0,card(0,C+" = 717","null","717"));cards.put(1,absent(1));}
        public List<CarrierCleanup.Card> read(int mask){List<CarrierCleanup.Card> out=new ArrayList<>();for(int slot=0;slot<2;slot++)if((mask&(1<<slot))!=0)out.add(cards.get(slot));return out;}
        public void prime(CarrierCleanup.Card c){writes.add("prime:"+c.slot);if(!stuckPrime)cards.put(c.slot,card(c.slot,raw(c.primeValues()),c.persistent,c.originalCountry));if(afterPrime!=null)afterPrime.run();}
        public void reset(CarrierCleanup.Card c)throws Exception{writes.add("reset:"+c.slot);if(denyReset)throw new IOException("system denied reset");if(!stuckReset)cards.put(c.slot,card(c.slot,"null","null",c.originalCountry));if(afterReset!=null)afterReset.run();}
        public long now(){return time;}
        public void pause(){time+=200;}
    }
    interface Action{void run()throws Exception;}
    static Exception fails(Action action)throws Exception{try{action.run();fail("expected refusal");return null;}catch(AssertionError e){throw e;}catch(Exception e){return e;}}
    @Test public void explicitCleanupAcceptsPartialInvalidCountryButAutomaticAdoptionStillRejectsIt()throws Exception{
        Fake f=new Fake();CarrierCleanup c=new CarrierCleanup(f);CarrierCleanup.Preview p=c.preview(3);
        assertEquals(1,p.count());assertTrue(f.writes.isEmpty());assertFalse(CarrierRecovery.compatible(f.cards.get(0).temporary));
        assertEquals(1,c.clear(p.token));assertEquals(Arrays.asList("prime:0","reset:0"),f.writes);assertEquals("cn",f.cards.get(0).country);assertTrue(CarrierRecovery.empty(f.cards.get(0).temporary));
    }
    @Test public void clearsPersistentAndTemporaryRegionFieldsTogether()throws Exception{
        Fake f=new Fake();f.cards.put(0,card(0,N+" = Custom",C+" = 717\n"+F+" = true","717"));CarrierCleanup c=new CarrierCleanup(f);
        c.clear(c.preview(1).token);assertTrue(CarrierRecovery.empty(f.cards.get(0).persistent));assertTrue(CarrierRecovery.empty(f.cards.get(0).temporary));
    }
    @Test public void aForeignFieldOnSecondSelectedCardPreventsAllWrites()throws Exception{
        Fake f=new Fake();f.cards.put(1,card(1,C+" = jp\napn_string = private","null","jp"));CarrierCleanup c=new CarrierCleanup(f);
        assertTrue(fails(()->c.preview(3)).getMessage().contains("卡 2"));assertTrue(f.writes.isEmpty());
    }
    @Test public void persistentImsOrApnFieldsAreProtected()throws Exception{
        for(String field:new String[]{"carrier_volte_available_bool = true","apn_string = private","wifi_calling_bool = false"}){
            Fake f=new Fake();f.cards.put(0,card(0,"null",C+" = jp\n"+field,"jp"));CarrierCleanup c=new CarrierCleanup(f);fails(()->c.preview(1));assertTrue(f.writes.isEmpty());
        }
    }
    @Test public void duplicateOrUnparseableFieldsAreProtected()throws Exception{
        for(String raw:new String[]{C+" = jp\n"+C+" = us",C+" = jp\nunrecognized", "PersistableBundle[{"+C+"=jp}]"}){
            Fake f=new Fake();f.cards.put(0,card(0,raw,"null","jp"));CarrierCleanup c=new CarrierCleanup(f);fails(()->c.preview(1));assertTrue(f.writes.isEmpty());
        }
    }
    @Test public void unselectedForeignFieldsRemainUntouched()throws Exception{
        Fake f=new Fake();CarrierCleanup.Card other=card(1,"apn_string = private","null","cn");f.cards.put(1,other);CarrierCleanup c=new CarrierCleanup(f);
        c.clear(c.preview(1).token);assertSame(other,f.cards.get(1));assertEquals(Arrays.asList("prime:0","reset:0"),f.writes);
    }
    @Test public void noResidueNeedsNoWriteAndProvidesNoUsablePlan()throws Exception{
        Fake f=new Fake();f.cards.put(0,card(0,"null","","cn"));CarrierCleanup c=new CarrierCleanup(f);CarrierCleanup.Preview p=c.preview(3);
        assertEquals(0,p.count());fails(()->c.clear(p.token));assertTrue(f.writes.isEmpty());
    }
    @Test public void repairsStickyReadbackEvenAfterOverrideIsAlreadyEmpty()throws Exception{
        Fake f=new Fake();f.cards.put(0,card(0,"null","null","717"));CarrierCleanup c=new CarrierCleanup(f);assertEquals(1,c.clear(c.preview(1).token));assertEquals("cn",f.cards.get(0).country);
    }
    @Test public void absentOnlySelectionIsRejected()throws Exception{Fake f=new Fake();CarrierCleanup c=new CarrierCleanup(f);fails(()->c.preview(2));assertTrue(f.writes.isEmpty());}
    @Test public void invalidMasksAreRejected()throws Exception{for(int mask:new int[]{-1,0,4,99}){CarrierCleanup c=new CarrierCleanup(new Fake());fails(()->c.preview(mask));}}
    @Test public void expiredAndReplayedPlansNeverWrite()throws Exception{
        Fake f=new Fake();CarrierCleanup c=new CarrierCleanup(f);CarrierCleanup.Preview p=c.preview(1);f.time+=CarrierCleanup.PLAN_TTL+1;fails(()->c.clear(p.token));assertTrue(f.writes.isEmpty());
        String token=c.preview(1).token;c.clear(token);int count=f.writes.size();fails(()->c.clear(token));assertEquals(count,f.writes.size());
    }
    @Test public void changedSimOrConfigurationInvalidatesWholePlan()throws Exception{
        for(int which=0;which<4;which++){
            Fake f=new Fake();f.cards.put(1,card(1,C+" = us","null","us"));CarrierCleanup c=new CarrierCleanup(f);String token=c.preview(3).token;
            CarrierCleanup.Card old=f.cards.get(1);
            if(which==0)f.cards.put(1,absent(1));
            if(which==1)f.cards.put(1,new CarrierCleanup.Card(1,99,old.temporary,old.persistent,old.country,old.originalCountry,old.baseline));
            if(which==2)f.cards.put(1,card(1,C+" = kr","null","kr"));
            if(which==3)f.cards.put(1,card(1,old.temporary,N+" = Changed",old.country));
            fails(()->c.clear(token));assertTrue(f.writes.isEmpty());
        }
    }
    @Test public void aNewPreviewInvalidatesTheOldOne()throws Exception{
        Fake f=new Fake();CarrierCleanup c=new CarrierCleanup(f);String old=c.preview(1).token;c.preview(3);fails(()->c.clear(old));assertTrue(f.writes.isEmpty());
    }
    @Test public void unknownOriginalCountryOrIncompleteBaselineIsRejected()throws Exception{
        for(String iso:new String[]{"","717","c","CN"}){
            Fake f=new Fake();f.cards.put(0,new CarrierCleanup.Card(0,40,C+" = 717","null","717",iso,BASE));CarrierCleanup c=new CarrierCleanup(f);fails(()->c.preview(1));assertTrue(f.writes.isEmpty());
        }
        Fake f=new Fake();f.cards.put(0,new CarrierCleanup.Card(0,40,C+" = 717","null","717","cn",Collections.singletonMap(C,"")));CarrierCleanup c=new CarrierCleanup(f);fails(()->c.preview(1));
    }
    @Test public void foreignFieldInsertedAfterPrimingPreventsNullReset()throws Exception{
        Fake f=new Fake();f.afterPrime=()->{CarrierCleanup.Card v=f.cards.get(0);f.cards.put(0,card(0,v.temporary+"apn_string = private","null","cn"));};CarrierCleanup c=new CarrierCleanup(f);
        Exception e=fails(()->c.clear(c.preview(1).token));assertTrue(e instanceof CarrierCleanup.Failure);assertEquals(Collections.singletonList("prime:0"),f.writes);assertTrue(f.cards.get(0).temporary.contains("apn_string"));
    }
    @Test public void delayedOrDeniedSystemWritesAreNeverReportedAsSuccess()throws Exception{
        for(int which=0;which<3;which++){
            Fake f=new Fake();f.stuckPrime=which==0;f.stuckReset=which==1;f.denyReset=which==2;CarrierCleanup c=new CarrierCleanup(f);
            CarrierCleanup.Failure e=(CarrierCleanup.Failure)fails(()->c.clear(c.preview(1).token));assertEquals(0,e.completedMask);assertEquals(1,e.startedMask);
            if(which==0)assertEquals(Collections.singletonList("prime:0"),f.writes);
        }
    }
    @Test public void reportsPartialCompletionAndProtectsSecondCard()throws Exception{
        Fake f=new Fake();f.cards.put(1,card(1,C+" = us","null","us"));f.afterReset=()->f.cards.put(1,card(1,"apn_string = private","null","us"));CarrierCleanup c=new CarrierCleanup(f);
        CarrierCleanup.Failure e=(CarrierCleanup.Failure)fails(()->c.clear(c.preview(3).token));assertEquals(1,e.completedMask);assertEquals(Arrays.asList("prime:0","reset:0"),f.writes);
    }
    @Test public void successfulBothSlotCleanupIsIdempotent()throws Exception{
        Fake f=new Fake();f.cards.put(1,card(1,F+" = broken\n"+N+" = Any name",C+" = ??","us"));CarrierCleanup c=new CarrierCleanup(f);
        assertEquals(3,c.clear(c.preview(3).token));assertEquals(0,c.preview(3).count());assertEquals(Arrays.asList("prime:0","reset:0","prime:1","reset:1"),f.writes);
    }
    @Test public void persistentWritesRemainForbiddenExceptNullReset()throws Exception{
        CarrierWritePolicy.checkPersistentReset(false,false);CarrierWritePolicy.checkPersistentReset(true,false);CarrierWritePolicy.checkPersistentReset(false,true);
        assertTrue(fails(()->CarrierWritePolicy.checkPersistentReset(true,true)) instanceof SecurityException);
    }
    @Test public void temporaryOnlyCleanupNeverRequestsPersistentPermission(){
        assertFalse(card(0,C+" = 717","null","717").resetPersistent());
        assertFalse(card(0,C+" = 717","  \n","717").resetPersistent());
        assertTrue(card(0,"null",C+" = 717","717").resetPersistent());
    }
}
