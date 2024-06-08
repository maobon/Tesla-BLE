package com.teslamotors.protocol.msg.action

import android.content.Context
import android.util.Log
import com.google.protobuf.ByteString
import com.google.protobuf.kotlin.toByteString
import com.teslamotors.protocol.util.JUtils
import com.teslamotors.protocol.keystore.KeyStoreUtils
import com.teslamotors.protocol.vcsec
import com.teslamotors.protocol.vcsec.AuthenticationResponse
import com.teslamotors.protocol.vcsec.UnsignedMessage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


class AuthRequest {

    companion object {
        private const val TAG = "AuthRequest"
    }

    // 在进行正式控制前的 最后一次校验.
    // 细节: 组装一条空信息 加密并签名 发给车. 车那边收到后校验你的操作
    // 通过 即所有流程跑通. 钥匙将处于活动状态 Active
    //
    // 车授权给你权限 可以进行其他操作

    fun perform(context: Context, sharedKey: ByteArray, counter: Int): ByteArray {

        // template
        val unsignedMessage = UnsignedMessage.newBuilder().setAuthenticationResponse(
            AuthenticationResponse.newBuilder()
                .setAuthenticationLevel(vcsec.AuthenticationLevel_E.AUTHENTICATION_LEVEL_NONE)
                .build()
        ).build()

        val unsignedMessageByteS: ByteString = unsignedMessage.toByteString()
        val unsignedMsgBytes = unsignedMessageByteS.toByteArray() // len=2

        // val counter = 23 // 每次 +1

        // 加密 ...
        val encryptedMsgWithTag: ByteArray = encrypt(unsignedMsgBytes, sharedKey, counter) // len=18

        // You should separate the encrypted/signed message into 2 variables,
        // encryptedMsg (from byte 0 to encryptedMsgWithTag.length - 16),

        val encryptedMsg = ByteArray(encryptedMsgWithTag.size - 16)
        System.arraycopy(encryptedMsgWithTag, 0, encryptedMsg, 0, encryptedMsgWithTag.size - 16)

        // msgSignature (from byte encryptedMsgWithTag.length - 16 to encryptedMsgWithTag.length).

        val msgSignature = ByteArray(encryptedMsgWithTag.size - encryptedMsg.size)
        System.arraycopy(
            encryptedMsgWithTag, encryptedMsgWithTag.size - 16, msgSignature, 0, msgSignature.size
        )

        val keyId = KeyStoreUtils.getInstance().keyId

        val signedMessage =
            vcsec.SignedMessage.newBuilder().setProtobufMessageAsBytes(encryptedMsg.toByteString())
                .setSignature(msgSignature.toByteString()) // ???
                .setCounter(counter).setKeyId(keyId.toByteString()).build()

        val toVCSECMessage =
            vcsec.ToVCSECMessage.newBuilder().setSignedMessage(signedMessage).build()

        val toVCSECMessageByteS = toVCSECMessage.toByteString() // finalized msg

        val bytes = toVCSECMessageByteS.toByteArray()
        val length = JUtils.integerToTwoBytes(bytes.size)
        val request = length + bytes

        Log.d(TAG, "auth request: ${JUtils.bytesToHex(request)}")

        return request
    }


    // Initialize an AES encryptor in GCM mode and encrypt the message using it
    private fun encrypt(plain: ByteArray, sharedKey: ByteArray, counter: Int): ByteArray {

        val aes = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey: SecretKey = SecretKeySpec(sharedKey, "AES")

        // nonce ...
        val nonce = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(counter).array();
        val iv = IvParameterSpec(nonce)

        aes.init(Cipher.ENCRYPT_MODE, secretKey, iv)

        aes.update(plain)

        val encryptedBytes = aes.doFinal()

        return encryptedBytes
    }

}