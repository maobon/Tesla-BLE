package com.teslamotors.protocol.keystore;

import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.teslamotors.protocol.util.JUtils;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;

public class KeyStoreUtils {

    private static final String TAG = "KeystoreUtils";

    private final static KeyStoreUtils S_KEY_STORE_UTILS = new KeyStoreUtils();
    public byte[] x963PublicKey;
    public byte[] sharedKey;

    private KeyStoreUtils() {
    }

    public static KeyStoreUtils getInstance() {
        return S_KEY_STORE_UTILS;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    public byte[] getKeyPair(Context context) throws KeyStoreException,
            CertificateException, IOException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, NoSuchProviderException,
            UnrecoverableKeyException {

        String pkgName = context.getPackageName();

        // The key pair can also be obtained from the Android Keystore any time as follows:
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        PrivateKey privateKey = (PrivateKey) keyStore.getKey(pkgName, null);
        if (privateKey == null) {
            Log.d(TAG, "getKeyPair: first invoke keystore, keypair is null");
            generate(pkgName);
        }

        privateKey = (PrivateKey) keyStore.getKey(pkgName, null);
        PublicKey publicKey = keyStore.getCertificate(pkgName).getPublicKey();

        // x9.63 format public key 0x04 开头 验证正确
        byte[] pub = toUncompressedPoint((ECPublicKey) publicKey);
        // byte[] pri = privateKey.getEncoded(); 安全风险 禁止导出

        this.x963PublicKey = pub;
        return pub;
    }

    // x9.62 convert to x9.63
    private byte[] toUncompressedPoint(final ECPublicKey publicKey) {

        int keySizeBytes = (publicKey.getParams().getOrder().bitLength() + Byte.SIZE - 1) / Byte.SIZE;

        final byte[] uncompressedPoint = new byte[1 + 2 * keySizeBytes];
        int offset = 0;
        uncompressedPoint[offset++] = 0x04;

        final byte[] x = publicKey.getW().getAffineX().toByteArray();
        if (x.length <= keySizeBytes) {
            System.arraycopy(x, 0, uncompressedPoint, offset + keySizeBytes - x.length, x.length);
        } else if (x.length == keySizeBytes + 1 && x[0] == 0) {
            System.arraycopy(x, 1, uncompressedPoint, offset, keySizeBytes);
        } else {
            throw new IllegalStateException("x value is too large");
        }
        offset += keySizeBytes;

        final byte[] y = publicKey.getW().getAffineY().toByteArray();
        if (y.length <= keySizeBytes) {
            System.arraycopy(y, 0, uncompressedPoint, offset + keySizeBytes - y.length, y.length);
        } else if (y.length == keySizeBytes + 1 && y[0] == 0) {
            System.arraycopy(y, 1, uncompressedPoint, offset, keySizeBytes);
        } else {
            throw new IllegalStateException("y value is too large");
        }

        return uncompressedPoint;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void generate(String alias) throws NoSuchAlgorithmException,
            NoSuchProviderException, InvalidAlgorithmParameterException {

        // 生成密钥对
        // NIST P-256 EC key pair for signing/verification using ECDSA
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");

        keyPairGenerator.initialize(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_AGREE_KEY) // KeyProperties.PURPOSE_AGREE_KEY
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .build());

        keyPairGenerator.generateKeyPair();
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    public byte[] getSharedKey(Context context, PublicKey serverEphemeralPublicKey) throws NoSuchAlgorithmException, NoSuchProviderException, KeyStoreException, CertificateException, IOException, UnrecoverableKeyException, InvalidKeyException {

        // Exchange public keys with server. A new ephemeral key MUST be used for every message.
        // PublicKey serverEphemeralPublicKey; // Ephemeral key received from server.

        // Create a shared secret based on our private key and the other party's public key.
        KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH", "AndroidKeyStore");

        String pkgName = context.getPackageName();

        // The key pair can also be obtained from the Android Keystore any time as follows:
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        // Derive an AES secret from our private key and the vehicle's public key
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(pkgName, null);
        if (privateKey == null) {
            throw new IOException("not found private key in Android KeyStore");
        }

        // 秘钥协商 ... ECDH
        keyAgreement.init(privateKey);
        keyAgreement.doPhase(serverEphemeralPublicKey, true);
        SecretKey aesSecret = keyAgreement.generateSecret("AES");

        byte[] aesSecretHash = MessageDigest.getInstance("SHA1")
                .digest(aesSecret.getEncoded());

        // tesla 需要前16位
        sharedKey = new byte[16];
        System.arraycopy(aesSecretHash, 0, sharedKey, 0, 16);

        return sharedKey;
    }

    public byte[] getKeyId() throws NoSuchAlgorithmException {
        if (x963PublicKey == null) throw new RuntimeException("x963PublicKey variable is null");

        MessageDigest messageDigest = MessageDigest.getInstance("SHA1");
        byte[] digest = messageDigest.digest(this.x963PublicKey);

        byte[] res = new byte[4];
        System.arraycopy(digest, 0, res, 0, 4);

        Log.d(TAG, "getKeyId: " + JUtils.bytesToHex(res));
        return res;
    }

    public ECParameterSpec getECParameterSpec(Context context) throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        ECPublicKey publicKey = (ECPublicKey) keyStore.getCertificate(context.getPackageName()).getPublicKey();
        return publicKey.getParams();
    }

}
