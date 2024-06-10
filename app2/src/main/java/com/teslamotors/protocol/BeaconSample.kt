package com.teslamotors.protocol

import com.teslamotors.protocol.ble.beacon.BeaconUtil
import com.teslamotors.protocol.util.JUtils

fun main() {

    // tesla iBeacon
    // uuid: 74278BDA-B644-4520-8F0C-720EAF059935 // constant value

    // base your vin
    // local name: Saadbad01e89502c1C

    // TAG + LEN + VAL
    var strHex = "02" + "01" + "06" +
            "1A" + "FF" + "4C00021574278BDAB64445208F0C720EAF05993501000E18C5" + // UUID major minor
            "03" + "02" + "2211" + // incomplete 16-bit service UUIDs
            "13" + "09" + "536161646261643031653839353032633143" // len=36 complete local name

    // strHex my car
    // 0201061AFF4C00021574278BDAB64445208F0C720EAF05993501000E18C5030222111309536161646261643031653839353032633143

    // test ....
    // strHex = "0201061AFF4C00021574278BDAB64445208F0C720EAF0599350100EF1FC5030222111309536265663739376532323562376138636343"
    // strHex = "0201061AFF4C00021574278BDAB64445208F0C720EAF05993501003245C5030222111309536266316536666162373839356634633943"

    val bytes = JUtils.hexToBytes(strHex)

    val beaconUtil = BeaconUtil.getInstance()
    if(beaconUtil.findBeaconPattern(bytes)){
        println(beaconUtil.uuid)
        println(beaconUtil.completeLocalName)
    }
}