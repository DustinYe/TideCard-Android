/* SPDX-License-Identifier: Apache-2.0
 * TideCard adaptation, 2026: local-only Java implementation and application integration.
 * Protocol / pairing reference: RikkaApps/Shizuku v13.6.0 (RikkaApps contributors).
 * See NOTICE and THIRD_PARTY.md for source links and modification details.
 */
package app.tidecard.adb;

import android.security.keystore.*;
import android.content.Context;
import android.util.Base64;
import java.math.BigInteger;
import java.net.Socket;
import java.nio.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.*;
import java.io.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.util.*;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;
import org.conscrypt.Conscrypt;

/** Per-install software RSA key, encrypted at rest by AndroidKeyStore AES-GCM. */
public final class AdbIdentity {
    private final PrivateKey privateKey;
    private final X509Certificate certificate;
    private final RSAPublicKey publicKey;
    private final SSLContext context;
    public AdbIdentity(Context app)throws Exception{
        String alias="tidecard_adb_wrap";KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(alias)){
            KeyGenerator generator=KeyGenerator.getInstance("AES","AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());generator.generateKey();
        }
        android.content.SharedPreferences preferences=app.getSharedPreferences("adb_identity",Context.MODE_PRIVATE);SecretKey wrap=(SecretKey)store.getKey(alias,null);String saved=preferences.getString("encrypted_rsa",null);PrivateKey rsa;
        byte[] aad="TideCard local adb key v2".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if(saved!=null){byte[] blob=Base64.decode(saved,Base64.NO_WRAP);if(blob.length<29)throw new GeneralSecurityException("本机配对密钥损坏");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,wrap,new GCMParameterSpec(128,blob,0,12));cipher.updateAAD(aad);byte[] plain=cipher.doFinal(blob,12,blob.length-12);try{rsa=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(plain));}finally{Arrays.fill(plain,(byte)0);}}
        else {KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(new RSAKeyGenParameterSpec(2048,RSAKeyGenParameterSpec.F4));rsa=generator.generateKeyPair().getPrivate();Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,wrap);cipher.updateAAD(aad);byte[] plain=rsa.getEncoded();byte[] encrypted;try{encrypted=cipher.doFinal(plain);}finally{Arrays.fill(plain,(byte)0);}byte[] blob=new byte[12+encrypted.length];System.arraycopy(cipher.getIV(),0,blob,0,12);System.arraycopy(encrypted,0,blob,12,encrypted.length);if(!preferences.edit().putString("encrypted_rsa",Base64.encodeToString(blob,Base64.NO_WRAP)).commit())throw new IOException("无法保存配对密钥");}
        privateKey=rsa;publicKey=(RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(((RSAPrivateKey)rsa).getModulus(),RSAKeyGenParameterSpec.F4));certificate=certificate(rsa,publicKey);certificate.verify(publicKey);
        X509ExtendedKeyManager keys=new X509ExtendedKeyManager(){
            public String chooseClientAlias(String[] k,Principal[] i,Socket s){return "adb";}
            public String chooseEngineClientAlias(String[] k,Principal[] i,SSLEngine e){return "adb";}
            public X509Certificate[] getCertificateChain(String a){return new X509Certificate[]{certificate};}
            public PrivateKey getPrivateKey(String a){return privateKey;}
            public String[] getClientAliases(String k,Principal[] i){return new String[]{"adb"};}
            public String[] getServerAliases(String k,Principal[] i){return null;}
            public String chooseServerAlias(String k,Principal[] i,Socket s){return null;}
        };
        // ADB authenticates the local endpoint through pairing, not public Web PKI.
        X509TrustManager trust=new X509TrustManager(){public void checkClientTrusted(X509Certificate[] c,String a){}public void checkServerTrusted(X509Certificate[] c,String a){}public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}};
        context=SSLContext.getInstance("TLSv1.3",Conscrypt.newProvider());context.init(new KeyManager[]{keys},new TrustManager[]{trust},new SecureRandom());
    }
    private static byte[] der(int tag,byte[]... parts)throws IOException{ByteArrayOutputStream body=new ByteArrayOutputStream();for(byte[] p:parts)body.write(p);byte[] value=body.toByteArray();ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(tag);if(value.length<128)out.write(value.length);else if(value.length<256){out.write(0x81);out.write(value.length);}else{out.write(0x82);out.write(value.length>>8);out.write(value.length);}out.write(value);return out.toByteArray();}
    private static X509Certificate certificate(PrivateKey key,PublicKey publicKey)throws Exception{
        byte[] algorithm=der(0x30,new byte[]{6,9,0x2a,(byte)0x86,0x48,(byte)0x86,(byte)0xf7,0x0d,1,1,0x0b},new byte[]{5,0});byte[] name=new X500Principal("CN=TideCard ADB").getEncoded();
        java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyMMddHHmmss'Z'",Locale.US);f.setTimeZone(TimeZone.getTimeZone("UTC"));byte[] validity=der(0x30,der(0x17,f.format(new Date(System.currentTimeMillis()-86400000L)).getBytes(java.nio.charset.StandardCharsets.US_ASCII)),der(0x17,f.format(new Date(System.currentTimeMillis()+315360000000L)).getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        byte[] tbs=der(0x30,der(2,new byte[]{1}),algorithm,name,validity,name,publicKey.getEncoded());Signature signature=Signature.getInstance("SHA256withRSA");signature.initSign(key);signature.update(tbs);byte[] certificate=der(0x30,tbs,algorithm,der(3,new byte[]{0},signature.sign()));return (X509Certificate)java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(certificate));
    }
    public SSLSocket tls(Socket socket,int port)throws Exception{SSLSocket tls=(SSLSocket)context.getSocketFactory().createSocket(socket,"127.0.0.1",port,true);tls.setSoTimeout(15000);tls.startHandshake();return tls;}
    private static void little(ByteBuffer b,BigInteger n){for(int i=0;i<64;i++){b.putInt(n.intValue());n=n.shiftRight(32);}}
    public byte[] publicAdb(){
        BigInteger n=publicKey.getModulus(),r32=BigInteger.ONE.shiftLeft(32);ByteBuffer b=ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(64);b.putInt(n.mod(r32).modInverse(r32).negate().intValue());little(b,n);little(b,BigInteger.ONE.shiftLeft(4096).mod(n));b.putInt(publicKey.getPublicExponent().intValue());
        return (Base64.encodeToString(b.array(),Base64.NO_WRAP)+" tidecard@android\0").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    public byte[] sign(byte[] token)throws Exception{
        byte[] prefix={0x30,0x21,0x30,0x09,0x06,0x05,0x2b,0x0e,0x03,0x02,0x1a,0x05,0x00,0x04,0x14};
        Signature signature=Signature.getInstance("NONEwithRSA");signature.initSign(privateKey);signature.update(prefix);signature.update(token);return signature.sign();
    }
}
