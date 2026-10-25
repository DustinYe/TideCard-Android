package app.tidecard;
import android.app.RemoteInput;
import android.content.*;
import android.os.Bundle;
public class PairingReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){Bundle results=RemoteInput.getResultsFromIntent(i);if(results==null)return;CharSequence code=results.getCharSequence("pairCode");if(code==null)return;c.startForegroundService(new Intent(c,ActivationService.class).setAction(ActivationService.PAIR).putExtra("code",code.toString()));}
}
