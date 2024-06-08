package com.teslamotors.protocol

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class SampleActivity : AppCompatActivity() {

    companion object {

        private const val TAG = "SampleActivity"

        val value_1 = Utils.hexToBytes("002F82012C080512060A042486826412060A0482")
        val value_2 = Utils.hexToBytes("3DC50112060A04042175F812060A040A003EA312")
        val value_3 = Utils.hexToBytes("060A047E6BE879181F")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 只能干跑了 .............

//        val value_1 = Utils.hexToBytes("001F1A1D12160A1413FED67A94F3DABA74C58831")
//        val value_2 = Utils.hexToBytes("23E8D0D02034A1211802220101")

//        val value_1 =
//            Utils.hexToBytes("002F82012C080512060A042486826412060A0482")
//        val value_2 =
//            Utils.hexToBytes("3DC50112060A04042175F812060A040A003EA312")
//        val value_3 =
//            Utils.hexToBytes("060A047E6BE879181F")


//        var fromVCSECMessage: vcsec.FromVCSECMessage? = null

//        fromVCSECMessage = MessageUtil.autoChaCha(value_1)
//        fromVCSECMessage = MessageUtil.autoChaCha(value_2)
//        fromVCSECMessage = MessageUtil.autoChaCha(value_3)
//        fromVCSECMessage = MessageUtil.autoChaCha(value_4)

//        fromVCSECMessage?.let {
//            Log.d(MainActivity.TAG, "fromVCSECMessage: ##\n $it")
//        }

        var bytes = MessageSlicer.joint(value_1)
        bytes = MessageSlicer.joint(value_2)
        bytes = MessageSlicer.joint(value_3)

        val fromVCSECMessage: vcsec.FromVCSECMessage? = bytes?.let {
            Log.d(TAG, "onCreate: parsing")
            vcsec.FromVCSECMessage.parseFrom(it)
        }


        Log.d(TAG, "onCreate: ....")


//        val info = fromVCSECMessage?.whitelistInfo
//        info?.whitelistEntriesList?.forEach {
//            val pubKeySha1 = Utils.bytesToHex(it.publicKeySHA1.toByteArray())
//            Log.d(TAG, "onCreate: pubKeySha1=$pubKeySha1")
//        }

        // ...
//        val value_1 = Utils.hexToBytes("004512431A4104B55E30F05B4CEFC0748EE2DF72")
//        val value_2 = Utils.hexToBytes("024678959127FFD934D36AE4AE125E679BA01836")
//        val value_3 = Utils.hexToBytes("2A6F53156A1DDEFDA29007E518E40D585488149E")
//        val value_4 = Utils.hexToBytes("844109FE4EA4A3A4747E7B")
//
//
//        var fromVCSECMessage: vcsec.FromVCSECMessage? = null
//
//        fromVCSECMessage = MessageUtil.autoChaCha(value_1)
//        fromVCSECMessage = MessageUtil.autoChaCha(value_2)
//        fromVCSECMessage = MessageUtil.autoChaCha(value_3)
//        fromVCSECMessage = MessageUtil.autoChaCha(value_4)
//
//        fromVCSECMessage?.apply {
//            Log.d(MainActivity.TAG, "uuuuu: ##\n $this")
//
//            // val bbb = it.sessionInfo.epoch.toByteArray()
//            EphemeralKeyVehicleResponse().perform(this@MainActivity, this)
//
//        }
        // ...

        // 全新的 逻辑 ....
//        val publicKey = KeyStoreUtils.getInstance().x963PublicKey.toByteString()
//
//
//        val nonce = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(counter).array();
//
//        val gcmData =
//            signatures.AES_GCM_Personalized_Signature_Data.newBuilder()
//                .setNonce(nonce.toByteString())
//                .setCounter(counter)
//                .setEpoch(ByteArray(0).toByteString())
//                .build()
//
//
//        val routableMessage = universal_message.RoutableMessage.newBuilder().setSignatureData(
//            signatureData {
//                aESGCMPersonalizedData = gcmData
//                signerIdentity = signerIdentity.toBuilder().setPublicKey(publicKey).build()
//            }.toBuilder().build()
//        ).build()

        // 全新的 ....
    }
}