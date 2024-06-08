package com.teslamotors.protocol.msg.key

import android.util.Log
import com.google.protobuf.kotlin.toByteString
import com.teslamotors.protocol.util.JUtils
import com.teslamotors.protocol.keyIdentifier
import com.teslamotors.protocol.vcsec
import com.teslamotors.protocol.vcsec.InformationRequest
import com.teslamotors.protocol.vcsec.ToVCSECMessage
import com.teslamotors.protocol.vcsec.UnsignedMessage

class EphemeralKeyRequest {

    companion object {
        private const val TAG = "EphemeralKeyRequest"
    }

    fun perform(keyId: ByteArray): ByteArray {

        val unsignedMessage = UnsignedMessage.newBuilder().setInformationRequest(
            InformationRequest.newBuilder()
                .setInformationRequestType(vcsec.InformationRequestType.INFORMATION_REQUEST_TYPE_GET_EPHEMERAL_PUBLIC_KEY)
                .setKeyId(
                    keyIdentifier {
                        publicKeySHA1 = keyId.toByteString()
                    }.toBuilder().build()
                ).build()
        ).build()

        val toVCSECMessage = ToVCSECMessage.newBuilder()
            .setUnsignedMessage(unsignedMessage)
            .build()

        val toVCSECMessageByteS = toVCSECMessage.toByteString() // finalized msg
        // ----------------

        // Bytes
        val bytes = toVCSECMessageByteS.toByteArray()
        val length = JUtils.integerToTwoBytes(bytes.size)
        val request = length + bytes

        Log.d(TAG, "get ephemeral key request: ${JUtils.bytesToHex(request)}")

        return request
    }
}