package app.tidecard;
import android.app.RemoteInput;
import android.content.*;
import android.os.Bundle;
public class PairingReceiver extends BroadcastReceiver {
    static final String OPEN="app.tidecard.OPEN_PAIRING_INPUT",REPLY="app.tidecard.PAIRING_REPLY";
    @Override public void onReceive(Context c,Intent i){
        String session=i.getStringExtra("session");if(session==null)return;
        if(OPEN.equals(i.getAction()))ActivationService.receive(session,null,true);
        else if(REPLY.equals(i.getAction())){Bundle input=RemoteInput.getResultsFromIntent(i);CharSequence value=input==null?null:input.getCharSequence("pairCode");if(value!=null)ActivationService.receive(session,value.toString(),false);}
    }
}
