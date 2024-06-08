package com.teslamotors.protocol.msg.key

import android.util.Log
import com.teslamotors.protocol.Utils
import com.teslamotors.protocol.universal_message
import com.teslamotors.protocol.vcsec.FromVCSECMessage

class AddKeyVehicleResponse {

    companion object {
        private const val TAG = "AddKeyVehicleResponse"
    }

    fun perform(fromVCSECMessage: FromVCSECMessage, publicKeySHA1: ByteArray): Boolean {

        when (fromVCSECMessage.commandStatus.whitelistOperationStatus.operationStatus) {

            universal_message.OperationStatus_E.OPERATIONSTATUS_OK -> {
                Log.d(TAG, "perform: ADD_KEY operation status OK")

                // from tesla vehicle
                val whitelistInfo = fromVCSECMessage.whitelistInfo

                // 不能这样 如果有多个会因为位置相互覆盖结果
                // whitelistInfo?.whitelistEntriesList?.forEach {
                //    val pubKeySha1 = Utils.bytesToHex(it.publicKeySHA1.toByteArray())
                //    addKeyCompleted = pubKeySha1.equals(publicKeySHA1)
                // }

                val whitelistInfoEntriesListHexs = mutableListOf<String>()
                whitelistInfo.whitelistEntriesList.forEach {
                    whitelistInfoEntriesListHexs.add(Utils.bytesToHex(it.publicKeySHA1.toByteArray()))
                }

                val isOk = whitelistInfoEntriesListHexs.contains(Utils.bytesToHex(publicKeySHA1))

                Log.d(TAG, "perform: ADD Key to Vehicle result: $isOk")
                return isOk
            }

            else -> return false
        }
    }
}