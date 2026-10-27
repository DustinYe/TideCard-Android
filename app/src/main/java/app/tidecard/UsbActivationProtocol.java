package app.tidecard;

/** A fixed record for the trusted USB shell. It never accepts a command from a caller. */
final class UsbActivationProtocol {
    static void requireShell(int uid){if(uid!=2000)throw new SecurityException("USB activation requires authorized ADB shell");}
    static String prepare(int version,int owner,String token,String path64){
        if(version<1||owner<10000||owner>=100000||token==null||!token.matches("[A-Za-z0-9_-]{43}")||path64==null||!path64.matches("[A-Za-z0-9+/]+={0,2}"))throw new IllegalArgumentException("Unsupported USB activation identity");
        return "TIDEUSB1|"+version+"|"+owner+"|"+token+"|"+path64;
    }
    static String status(int version,boolean ready){return "TIDEUSBSTATUS1|"+version+"|"+(ready?"1":"0");}
    private UsbActivationProtocol(){}
}
