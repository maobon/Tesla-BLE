package com.teslamotors.protocol.msg.key

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.teslamotors.protocol.keystore.EccCurvy
import com.teslamotors.protocol.keystore.KeyStoreUtils
import com.teslamotors.protocol.util.JUtils
import com.teslamotors.protocol.util.to4ByteArray
import com.teslamotors.protocol.vcsec.FromVCSECMessage
import java.security.interfaces.ECPublicKey
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


class EphemeralKeyVehicleResponse {

    companion object {
        private const val TAG = "EphemeralKeyVehicleResp"
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun perform(context: Context, fromVCSECMessage: FromVCSECMessage): ByteArray {

        val publicKey = fromVCSECMessage.sessionInfo.epoch // vehicle's ephemeral key

        // retrieve public key ... ??
        val ecParameterSpec = KeyStoreUtils.getInstance().getECParameterSpec(context)

        val ecPublicKey: ECPublicKey = EccCurvy
            .fromUncompressedPoint(publicKey.toByteArray(), ecParameterSpec)

        val sharedKey: ByteArray = KeyStoreUtils.getInstance()
            .getSharedKey(context, ecPublicKey) // sharedKey

        Log.d(TAG, "perform: sharedKey ==>> ${JUtils.bytesToHex(sharedKey)}")

        return sharedKey
    }

    // Initialize an AES encryptor in GCM mode and encrypt the message using it
    fun encrypt(plain: ByteArray, sharedKey: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey: SecretKey = SecretKeySpec(sharedKey, "AES")

        val counter = 23 // 必须要保存 每次+1
        val nonce = counter.to4ByteArray()

        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(nonce))
        val encryptedBytes = cipher.doFinal(plain)

        return encryptedBytes
    }

}