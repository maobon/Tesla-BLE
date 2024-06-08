package com.teslamotors.protocol.msg.key

import android.util.Log
import com.google.protobuf.ByteString
import com.teslamotors.protocol.keyMetadata
import com.teslamotors.protocol.permissionChange
import com.teslamotors.protocol.publicKey
import com.teslamotors.protocol.util.JUtils
import com.teslamotors.protocol.util.to2ByteArray
import com.teslamotors.protocol.vcsec
import com.teslamotors.protocol.whitelistOperation

class AddKeyToWhiteListRequest {

    companion object {
        const val TAG = "AddKeyToWhiteList"
    }

    fun perform(pubKey: ByteArray): ByteArray {

        val whitelistOperation = whitelistOperation {
            addKeyToWhitelistAndAddPermissions = permissionChange {
                key = publicKey { publicKeyRaw = ByteString.copyFrom(pubKey) }
                permission.add(vcsec.WhitelistKeyPermission_E.WHITELISTKEYPERMISSION_LOCAL_DRIVE)
                permission.add(vcsec.WhitelistKeyPermission_E.WHITELISTKEYPERMISSION_LOCAL_UNLOCK)
                permission.add(vcsec.WhitelistKeyPermission_E.WHITELISTKEYPERMISSION_REMOTE_DRIVE)
                permission.add(vcsec.WhitelistKeyPermission_E.WHITELISTKEYPERMISSION_REMOTE_UNLOCK)
            }.toBuilder().build()
            metadataForKey = keyMetadata {
                keyFormFactor = vcsec.KeyFormFactor.KEY_FORM_FACTOR_ANDROID_DEVICE
            }.toBuilder().build()
        }.toBuilder().build()

        // Whitelisting your key
        val unsignedMessage = vcsec.UnsignedMessage.newBuilder()
            .setWhitelistOperation(whitelistOperation)
            .build()

        val unsignedMessageByteS =
            unsignedMessage.toByteString() // print test ... prepare BLE message

        Log.d(TAG, "perform: byteStr: ${JUtils.bytesToHex(unsignedMessageByteS.toByteArray())}")

        val signedMessage = vcsec.SignedMessage.newBuilder()
            .setProtobufMessageAsBytes(unsignedMessageByteS)
            .setSignatureType(vcsec.SignatureType.SIGNATURE_TYPE_PRESENT_KEY)
            .build()

        val toVcsecMsg = vcsec.ToVCSECMessage.newBuilder()
            .setSignedMessage(signedMessage)
            .build()

        // ByteString
        val toVcsecMsgByteArr = toVcsecMsg.toByteString() // finalized msg

        // ---------------------------

        //... bytes
        val finalizedMsg = toVcsecMsgByteArr.toByteArray()
        val length = (finalizedMsg.size).to2ByteArray()

        // ....

        val requestBytes: ByteArray = length + finalizedMsg

        // convert success
        // val xxxx = Utils.read2BytesToInt(length)
        // Log.d(TAG, "perform: bytes len=$xxxx")

        // 打印检查
        Log.d(TAG, "add key to white list perform: ${JUtils.bytesToHex(requestBytes)}")

        return requestBytes
    }

}