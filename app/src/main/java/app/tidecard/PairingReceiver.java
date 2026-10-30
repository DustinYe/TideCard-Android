package app.tidecard;
import android.app.RemoteInput;
import android.content.*;
import android.os.Bundle;
public class PairingReceiver extends BroadcastReceiver {
    static final String OPEN="app.tidecard.OPEN_PAIRING_INPUT",REPLY="app.tidecard.PAIRING_REPLY";
    static final String OPEN_MANUAL="app.tidecard.OPEN_MANUAL_PAIRING";
    static final String MANUAL="app.tidecard.MANUAL_PAIRING",CONNECTION="app.tidecard.MANUAL_CONNECTION",RETRY="app.tidecard.RETRY_DISCOVERY";
    @Override public void onReceive(Context c,Intent i){
        String session=i.getStringExtra("session");if(session==null)return;
        if(OPEN.equals(i.getAction()))ActivationService.receive(session,null,true);
        else if(OPEN_MANUAL.equals(i.getAction()))ActivationService.openManual(session);
        else if(MANUAL.equals(i.getAction())||CONNECTION.equals(i.getAction())){Bundle input=RemoteInput.getResultsFromIntent(i);boolean connect=CONNECTION.equals(i.getAction());CharSequence value=input==null?null:input.getCharSequence(connect?"connectPort":"manualPair");if(value!=null)ActivationService.receiveManual(session,value.toString(),connect);}
        else if(RETRY.equals(i.getAction()))ActivationService.retry(session);
        else if(REPLY.equals(i.getAction())){Bundle input=RemoteInput.getResultsFromIntent(i);CharSequence value=input==null?null:input.getCharSequence("pairCode");if(value!=null)ActivationService.receive(session,value.toString(),false);}
    }
}
