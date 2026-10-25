/* SPDX-License-Identifier: Apache-2.0
 * TideCard adaptation, 2026: local-only Java implementation and application integration.
 * Protocol / pairing reference: RikkaApps/Shizuku v13.6.0 (RikkaApps contributors).
 * See NOTICE and THIRD_PARTY.md for source links and modification details.
 */
package moe.shizuku.manager.adb;

/** JNI ABI adapter for Shizuku 13.6.0's Apache-2.0 ADB pairing library. */
public final class PairingContext implements AutoCloseable {
    static {System.loadLibrary("adb");}
    private long pointer;
    public PairingContext(byte[] password){pointer=nativeConstructor(true,password);if(pointer==0)throw new IllegalStateException("无法创建配对会话");}
    public byte[] message(){return nativeMsg(pointer);}
    public boolean initialize(byte[] theirs){return nativeInitCipher(pointer,theirs);}
    public byte[] encrypt(byte[] in){return nativeEncrypt(pointer,in);}
    public byte[] decrypt(byte[] in){return nativeDecrypt(pointer,in);}
    public void close(){if(pointer!=0){nativeDestroy(pointer);pointer=0;}}
    private static native long nativeConstructor(boolean client,byte[] password);
    private native byte[] nativeMsg(long pointer);
    private native boolean nativeInitCipher(long pointer,byte[] theirs);
    private native byte[] nativeEncrypt(long pointer,byte[] in);
    private native byte[] nativeDecrypt(long pointer,byte[] in);
    private native void nativeDestroy(long pointer);
}
