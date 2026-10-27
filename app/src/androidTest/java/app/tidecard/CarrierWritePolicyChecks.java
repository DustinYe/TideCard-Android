package app.tidecard;

/** Host-JDK checks of callback authentication and conservative failure decisions, not a device write test. */
public final class CarrierWritePolicyChecks {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        String token="one-time-compatibility-request-token";
        CarrierWritePolicy.Gate gate=new CarrierWritePolicy.Gate(token,10501,1234);
        require(!gate.begin(token,2000,1234),"shell must not impersonate the app callback");
        require(!gate.begin(token,10501,1235),"restarted app process must not receive old request");
        require(!gate.begin("wrong-token",10501,1234),"wrong nonce accepted");
        require(gate.begin(token,10501,1234),"valid callback rejected");
        require(!gate.begin(token,10501,1234),"duplicate begin accepted");
        gate.close();require(!gate.accepts(token,10501,1234),"late completion accepted");
        CarrierWritePolicy.Gate expired=new CarrierWritePolicy.Gate(token,10501,1234);expired.close();require(!expired.begin(token,10501,1234),"expired operation began a write");
        require(CarrierWritePolicy.canDiscardBackup(true,0,true),"clean rejection should stop without a fake session");
        require(!CarrierWritePolicy.canDiscardBackup(true,1,true),"partial write lost backup");
        require(!CarrierWritePolicy.canDiscardBackup(true,0,false),"unverified state lost backup");
        require(!CarrierWritePolicy.canDiscardBackup(false,0,true),"timeout/unknown write lost backup");
        require(CarrierWritePolicy.shellDenied("overrideConfig cannot be invoked by shell"),"reported error not recognized");
        require(!CarrierWritePolicy.shellDenied("persistent=true only can be invoked by system app"),"unrelated permission rejection incorrectly selected fallback");
        require(!CarrierWritePolicy.shellDenied(null),"missing error misclassified");
        System.out.println("PASS callback UID/PID/nonce binding, duplicate/late rejection and conservative partial/unknown-write handling");
    }
}
