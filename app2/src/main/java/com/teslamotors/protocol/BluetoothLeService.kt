package com.teslamotors.protocol

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import androidx.annotation.RequiresApi
import com.teslamotors.protocol.ble.BluetoothUtil
import com.teslamotors.protocol.ble.ConnectionStateListener
import com.teslamotors.protocol.ble.GattCallback
import com.teslamotors.protocol.ble.GattUtil
import com.teslamotors.protocol.ble.beacon.TeslaBeacon
import com.teslamotors.protocol.keystore.KeyStoreUtils
import com.teslamotors.protocol.msg.action.AuthRequest
import com.teslamotors.protocol.msg.action.ClosuresRequest
import com.teslamotors.protocol.msg.key.AddKeyToWhiteListRequest
import com.teslamotors.protocol.msg.key.AddKeyVehicleResponse
import com.teslamotors.protocol.msg.key.EphemeralKeyRequest
import com.teslamotors.protocol.msg.key.EphemeralKeyVehicleResponse
import com.teslamotors.protocol.util.ACTION_AUTHENTICATING
import com.teslamotors.protocol.util.ACTION_CLIENT_MESSENGER
import com.teslamotors.protocol.util.ACTION_CLOSURES_REQUESTING
import com.teslamotors.protocol.util.ACTION_CONNECTING
import com.teslamotors.protocol.util.ACTION_CONNECTING_RESP
import com.teslamotors.protocol.util.ACTION_EPHEMERAL_KEY_REQUESTING
import com.teslamotors.protocol.util.ACTION_KEY_TO_WHITELIST_ADDING
import com.teslamotors.protocol.util.ACTION_TOAST
import com.teslamotors.protocol.util.JUtils
import com.teslamotors.protocol.util.Operations.AUTHENTICATING
import com.teslamotors.protocol.util.Operations.CLOSURES_REQUESTING
import com.teslamotors.protocol.util.Operations.EPHEMERAL_KEY_REQUESTING
import com.teslamotors.protocol.util.Operations.KEY_TO_WHITELIST_ADDING
import com.teslamotors.protocol.util.TESLA_BLUETOOTH_BEACON_LOCAL_NAME
import com.teslamotors.protocol.util.TESLA_RX_CHARACTERISTIC_DESCRIPTOR_UUID
import com.teslamotors.protocol.util.countAutoIncrement
import com.teslamotors.protocol.util.sendMessage


@Suppress("all")
class BluetoothLeService : Service() {

    private var mScanning = false

    // real control instance
    private lateinit var mGatt: GattUtil
    private val mBluetoothUtil = BluetoothUtil(this@BluetoothLeService)

    private lateinit var txCharacteristic: BluetoothGattCharacteristic
    private lateinit var rxCharacteristic: BluetoothGattCharacteristic

    // bluetooth gatt callback result: onConnectionStateChange ...
    private lateinit var mGattCallback: GattCallback

    private val keyStoreUtils: KeyStoreUtils = KeyStoreUtils.getInstance()
    private lateinit var x963FormatPublicKey: ByteArray

    private lateinit var cMessenger: Messenger

    private val mServiceHandler by lazy {
        ServiceHandler(this@BluetoothLeService)
    }


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate() {
        super.onCreate()

        initKeystore()

        mGattCallback = GattCallback(object : ConnectionStateListener {
            override fun onConnected(gatt: BluetoothGatt) {
                mGatt = GattUtil(gatt)

                // auto next step discovery services
                mGatt.discoveryServices()
            }

            override fun onGetCharacteristics(
                tx: BluetoothGattCharacteristic, rx: BluetoothGattCharacteristic
            ) {
                txCharacteristic = tx
                rxCharacteristic = rx

                // xiaomi using for test
                // mGatt.enableNotifications(rx, XIAOMI_ENV_SENSOR_CCC_DESCRIPTOR_UUID)

                // tesla enable notification
                mGatt.enableNotifications(rx, TESLA_RX_CHARACTERISTIC_DESCRIPTOR_UUID)

                // connect process completed .... !!
                sendMessage(
                    cMessenger,
                    ACTION_CONNECTING_RESP,
                    "vehicle connected successful"
                )
            }

            // response to Ac ....
            override fun onVehicleResponse(message: vcsec.FromVCSECMessage?) {
                when (mGatt.opera) {
                    KEY_TO_WHITELIST_ADDING -> {
                        val ret = with(message!!) {
                            AddKeyVehicleResponse().perform(this, keyStoreUtils.keyId)
                        }
                        Log.d(
                            TAG,
                            "onVehicleResponse: KEY_TO_WHITELIST_ADDING -> keystore process result: $ret"
                        )
                    }

                    EPHEMERAL_KEY_REQUESTING -> {
                        val sharedKey = with(message!!) {
                            EphemeralKeyVehicleResponse().perform(this@BluetoothLeService, this)
                        }
                        Log.d(
                            TAG,
                            "onVehicleResponse: EPHEMERAL_KEY_REQUESTING -> get shared key: $sharedKey"
                        )
                    }

                    AUTHENTICATING -> {

                    }

                    CLOSURES_REQUESTING -> {

                    }

                    else -> {}
                }
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun initKeystore() {
        x963FormatPublicKey = keyStoreUtils.getKeyPair(this)
        Log.d(TAG, "initKeystore: x963FormatPublicKey=${JUtils.bytesToHex(x963FormatPublicKey)}")
    }

    private val mScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            Log.d(TAG, "onScanResult: find a Tesla")
            Log.i(TAG, "BLE scan result: bytes=${JUtils.bytesToHex(result.scanRecord?.bytes)}")

            // val manufacturerData: SparseArray<ByteArray>? = result.scanRecord?.manufacturerSpecificData
            // manufacturerData?.forEach { key, value ->
            //     if (key == 76) { /*0x004c is apple company id*/ }
            // }

            // normal ble device is ok
            // Log.d(TAG, "connecting!!! ---> : ${device.name} + ${device.address}")
            // if (mScanning) {
            //     stopBleScan()
            // }
            // connectTargetDevice(device)

            // Tesla iBeacon protocol
            result.scanRecord?.advertisingDataMap?.get(9).let {
                Log.i(TAG, "onScanResult: completeLocalName=${java.lang.String(it)}")

                if (TESLA_BLUETOOTH_BEACON_LOCAL_NAME == it.toString()) {
                    Log.d(TAG, "onScanResult: === FIND MY CAR ===")
                    sendMessage(cMessenger, ACTION_TOAST, "Find my car")

                    if (mScanning) stopBleScan()
                    connectTargetDevice(result.device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "onScanFailed: scan callback err code: $errorCode")
            mScanning = false
        }
    }

    private fun connectTargetDevice(bluetoothDevice: BluetoothDevice) {
        bluetoothDevice.connectGatt(
            this, false, mGattCallback, BluetoothDevice.TRANSPORT_LE
        )
    }

    internal class ServiceHandler(
        private val service: BluetoothLeService
    ) : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                ACTION_CLIENT_MESSENGER -> {
                    service.cMessenger = msg.replyTo
                }

                ACTION_CONNECTING -> service.startBleScan()
                ACTION_KEY_TO_WHITELIST_ADDING -> service.addKey()
                ACTION_EPHEMERAL_KEY_REQUESTING -> service.requestEphemeralKey()
                ACTION_AUTHENTICATING -> service.authenticate()
                ACTION_CLOSURES_REQUESTING -> service.openPassengerDoor()
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return Messenger(mServiceHandler).binder
    }

    // step1 ....................
    // scan and connect .... every time ....
    private fun startBleScan() {
        Log.i(TAG, "startBleScan: ")

        if (mScanning) {
            stopBleScan()
        } else {
            mScanning = true

            // xiaomi using for test
            // val scanFilter = ScanFilter.Builder().setDeviceName(XIAOMI_MIJIA_SENSOR_NAME).build()

            // tesla
            val filters = mutableListOf<ScanFilter>().apply {
                add(
                    ScanFilter.Builder()
                        .setManufacturerData(
                            TeslaBeacon.MANUFACTURER_ID,
                            TeslaBeacon.getManufactureData(),
                            TeslaBeacon.getManufactureDataMask()
                        )
                        .build()
                )
            }

            mBluetoothUtil.mScanner.startScan(filters, getScanSettings(), mScanCallback)
            countdownTime(15 * 1000)
        }
    }

    private fun getScanSettings(): ScanSettings {
        return ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setReportDelay(0)
            .build()
    }

    private fun countdownTime(ms: Long) {
        Log.d(TAG, "countdownTime: after 15s count down time end")
        Handler(mainLooper).postDelayed({
            stopBleScan(true)
        }, ms)
    }

    private fun stopBleScan(sendAction: Boolean = false) {
        Log.i(TAG, "stopBleScan: ")
        if (mScanning) {
            mScanning = false
            if (sendAction) {
                sendMessage(cMessenger, ACTION_CONNECTING_RESP)
            }
            mBluetoothUtil.mScanner.stopScan(mScanCallback)
        }
    }

    // -----------------------------------------------
    // step2 ....................
    private fun addKey() {
        Log.i(TAG, "addKey: ")
        if (x963FormatPublicKey.isEmpty()) {
            Log.e(TAG, "addKey: x963FormatPublicKey is null")
            return
        }

        val requestMsg = AddKeyToWhiteListRequest().perform(x963FormatPublicKey)
        mGatt.writeCharacteristic(
            txCharacteristic, requestMsg, KEY_TO_WHITELIST_ADDING
        )
    }

    // step3 ....................
    private fun requestEphemeralKey() {
        Log.i(TAG, "requestEphemeralKey: ")
        val requestMsg = EphemeralKeyRequest().perform(keyStoreUtils.keyId)
        mGatt.writeCharacteristic(txCharacteristic, requestMsg, EPHEMERAL_KEY_REQUESTING)
    }

    // step 4 ...............................
    private fun authenticate() {
        Log.i(TAG, "authenticate: ")
        val sharedKey: ByteArray = keyStoreUtils.sharedKey
        val requestMsg = AuthRequest().perform(this, sharedKey, countAutoIncrement())
        mGatt.writeCharacteristic(txCharacteristic, requestMsg, AUTHENTICATING)
    }

    // -----------------------------------------------
    // real control
    private fun openPassengerDoor() {
        Log.i(TAG, "openPassengerDoor: ")
        val sharedKey: ByteArray = keyStoreUtils.sharedKey
        val requestMsg = ClosuresRequest().perform(this, sharedKey, countAutoIncrement())
        mGatt.writeCharacteristic(txCharacteristic, requestMsg, CLOSURES_REQUESTING)
    }

    private companion object {
        private const val TAG = "BluetoothLeService"
    }

    fun isIBeacon(packetData: ByteArray): Boolean {
        var startByte = 2
        while (startByte <= 5) {
            if (packetData[startByte + 2].toInt() and 0xff == 0x02 && packetData[startByte + 3].toInt() and 0xff == 0x15) {
                // debug result: startByte = 5
                return true
            }
            startByte++
        }
        return false
    }
}