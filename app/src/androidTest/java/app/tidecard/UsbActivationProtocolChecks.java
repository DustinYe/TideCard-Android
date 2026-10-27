package app.tidecard;

/** Host checks only; an authorized USB device is still required to validate the provider. */
public final class UsbActivationProtocolChecks {
    private static void rejects(Runnable action){try{action.run();throw new AssertionError("unexpected acceptance");}catch(SecurityException|IllegalArgumentException expected){}}
    public static void main(String[] args){
        UsbActivationProtocol.requireShell(2000);
        for(int uid:new int[]{0,1000,10000,10123})rejects(()->UsbActivationProtocol.requireShell(uid));
        String token="AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        String record=UsbActivationProtocol.prepare(8,10123,token,"L2RhdGEvYXBwL2Jhc2UuYXBr");
        if(!record.startsWith("TIDEUSB1|8|10123|"))throw new AssertionError(record);
        rejects(()->UsbActivationProtocol.prepare(8,110123,token,"L2RhdGE="));
        rejects(()->UsbActivationProtocol.prepare(8,10123,"bad; command","L2RhdGE="));
        if(!"TIDEUSBSTATUS1|8|0".equals(UsbActivationProtocol.status(8,false))||!"TIDEUSBSTATUS1|8|1".equals(UsbActivationProtocol.status(8,true)))throw new AssertionError("readiness");
        System.out.println("PASS USB shell-only identity, primary-user guard and readiness protocol");
    }
}
