package app.tidecard;

/** Pure decisions shared by the Binder backend and regression checks. */
public final class CarrierWritePolicy {
    private CarrierWritePolicy(){}
    public static void checkPersistentReset(boolean hasConfig,boolean persistent){if(persistent&&hasConfig)throw new SecurityException("Only an explicit null reset may be persistent");}
    public static boolean shellDenied(String message){return message!=null&&message.contains("overrideConfig cannot be invoked by shell");}
    public static boolean canDiscardBackup(boolean definitelyDenied,int modifiedMask,boolean originalUnchanged){return definitelyDenied&&modifiedMask==0&&originalUnchanged;}
    public static final class Gate {
        private final String nonce;private final int uid,pid;private boolean begun,closed;
        public Gate(String nonce,int uid,int pid){if(nonce==null||nonce.length()<20||uid<10000||pid<=0)throw new IllegalArgumentException("Invalid compatibility request");this.nonce=nonce;this.uid=uid;this.pid=pid;}
        public synchronized boolean accepts(String value,int uid,int pid){return !closed&&this.uid==uid&&this.pid==pid&&nonce.equals(value);}
        public synchronized boolean begin(String value,int uid,int pid){if(begun||!accepts(value,uid,pid))return false;begun=true;return true;}
        public synchronized void close(){closed=true;}
        public synchronized boolean begun(){return begun;}
    }
}
