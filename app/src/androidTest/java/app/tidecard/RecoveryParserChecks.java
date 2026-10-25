package app.tidecard;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

/** Plain Java checks; optional argument is a private real-device dump, never part of the source tree. */
public final class RecoveryParserChecks {
    private static final String C=CarrierRecovery.COUNTRY,F=CarrierRecovery.FLAG,N=CarrierRecovery.NAME;
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void invalidBaseline(String defaults,String platform)throws Exception{boolean rejected=false;try{CarrierRecovery.baseline(defaults,platform,"null");}catch(IOException expected){rejected=true;}require(rejected,"malformed baseline accepted");}
    public static void main(String[] args)throws Exception{
        String legacy=C+" = us\n"+F+" = true\n"+N+" = T-Mobile";
        require(CarrierRecovery.compatible(legacy),"old three-key session rejected");
        for(String raw:new String[]{legacy+"\napn_string = private",legacy+"\n"+C+" = jp",C+" = us",legacy.replace("T-Mobile","Unknown"),legacy.replace("true","false"),legacy+"\nunparseable"})require(!CarrierRecovery.compatible(raw),"foreign, partial or malformed override accepted");
        String defaults=C+" = \n"+F+" = false\n"+N+" = ";
        Map<String,String> base=CarrierRecovery.baseline(defaults,N+" = China Mobile","null");
        require(base.get(C).isEmpty()&&base.get(F).equals("false")&&base.get(N).equals("China Mobile"),"platform defaults not reconstructed");
        base=CarrierRecovery.baseline(defaults,C+" = cn\n"+N+" = Platform",C+" = hk\n"+N+" = Carrier");
        require(base.get(C).equals("hk")&&base.get(N).equals("Carrier"),"carrier configuration precedence wrong");
        invalidBaseline(C+" = cn", "null");invalidBaseline(defaults,F+" = not-a-boolean");invalidBaseline(defaults,C+" = invalid-country");
        String dump="Phone Id = 10\n    mOverrideConfigs : null\n\nPhone Id = 1\n    mOverrideConfigs : \n"+legacy+"\n\nPhone Id = 0\n    mOverrideConfigs : null\n\n";
        require(CarrierRecovery.section(dump,1,"mOverrideConfigs").equals(legacy),"slot boundaries mixed");
        require(CarrierRecovery.empty(CarrierRecovery.section(dump,0,"mOverrideConfigs")),"null layer parse failed");
        boolean rejected=false;try{CarrierRecovery.section(dump,1,"mConfigFromDefaultApp");}catch(IOException expected){rejected=true;}require(rejected,"missing source layer guessed");
        System.out.println("PASS known residual format, foreign/malformed rejection, baseline precedence and slot boundaries");
        if(args.length>0){String actual=new String(Files.readAllBytes(Paths.get(args[0])),java.nio.charset.StandardCharsets.UTF_8);for(int slot=0;slot<2;slot++){
            require(CarrierRecovery.compatible(CarrierRecovery.section(actual,slot,"mOverrideConfigs")),"actual residual not recognized");
            require(CarrierRecovery.empty(CarrierRecovery.section(actual,slot,"mPersistentOverrideConfigs")),"unexpected persistent override");
            Map<String,String> original=CarrierRecovery.baseline(CarrierRecovery.section(actual,slot,"Default Values from CarrierConfigManager"),CarrierRecovery.section(actual,slot,"mConfigFromDefaultApp"),CarrierRecovery.section(actual,slot,"mConfigFromCarrierApp"));
            require(!original.get(C).equals("us")&&original.get(F).equals("false")&&!original.get(N).equals("T-Mobile"),"residual leaked into original backup");
        }System.out.println("PASS real-device residual recognized and unmodified carrier layers separated");}
    }
}
