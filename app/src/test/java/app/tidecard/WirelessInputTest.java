package app.tidecard;
import org.junit.Test;
import static org.junit.Assert.*;

public class WirelessInputTest {
    @Test public void manualPairKeepsLeadingZeroCode(){
        for(String text:new String[]{"39461 001234","39461:001234","39461，001234"," 39461\n001234 "}){
            PairingInput.Pair input=PairingInput.pair(text);assertEquals(39461,input.port());assertEquals("001234",input.code());
        }
    }
    @Test public void rejectsAddressesExpiredFormatsAndInvalidPorts(){
        for(String text:new String[]{"","195615","0 123456","65536 123456","-1 123456","39461 12345","39461 1234567","127.0.0.1:39461 123456","39461 １２３４５６","39461 123456 extra"}){
            try{PairingInput.pair(text);fail(text);}catch(IllegalArgumentException expected){assertFalse(expected.getMessage().contains("123456"));}
        }
    }
    @Test public void connectionInputIsOnlyAPort(){
        assertEquals(1,PairingInput.port("1"));assertEquals(65535,PairingInput.port("65535"));assertEquals(38213,PairingInput.port(" 38213 "));
        for(String text:new String[]{"","0","65536","1.2.3.4:12345","38213 123456","-2"})try{PairingInput.port(text);fail(text);}catch(IllegalArgumentException expected){}
    }
    @Test public void timeoutAndLateResolveCannotConsumeNextTicket(){
        DiscoveryQueue<String> queue=new DiscoveryQueue<>();queue.offer("a","first");queue.offer("a","duplicate");queue.offer("b","second");
        var first=queue.next();assertEquals("first",first.value);assertNull(queue.next());queue.offer("a","duplicate-active");assertTrue(queue.finish(first));
        var second=queue.next();assertEquals("second",second.value);assertFalse(queue.finish(first));assertTrue(queue.finish(second));assertNull(queue.next());
    }
    @Test public void lostAndCloseDiscardPendingWork(){
        DiscoveryQueue<String> queue=new DiscoveryQueue<>();queue.offer("a","first");queue.offer("b","second");var first=queue.next();queue.lost("b");queue.clear();assertFalse(queue.finish(first));assertNull(queue.next());
        for(int n=0;n<100;n++)queue.offer("k"+n,"v"+n);int count=0;for(var t=queue.next();t!=null;t=queue.next()){count++;queue.finish(t);}assertEquals(64,count);
    }
}
