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
import com.teslamotors.protocol.ble.GattUtil
import com.teslamotors.protocol.ble.BluetoothUtil
import com.teslamotors.protocol.ble.ConnectionStateListener
import com.teslamotors.protocol.ble.GattCallback
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
import com.teslamotors.protocol.util.ACTION_EPHEMERAL_KEY_REQUESTING
import com.teslamotors.protocol.util.ACTION_KEY_TO_WHITELIST_ADDING
import com.teslamotors.protocol.util.JUtils
import com.teslamotors.protocol.util.Operations.AUTHENTICATING
import com.teslamotors.protocol.util.Operations.CLOSURES_REQUESTING
import com.teslamotors.protocol.util.Operations.EPHEMERAL_KEY_REQUESTING
import com.teslamotors.protocol.util.Operations.KEY_TO_WHITELIST_ADDING
import com.teslamotors.protocol.util.TESLA_BLUETOOTH_NAME
import com.teslamotors.protocol.util.TESLA_RX_CHARACTERISTIC_DESCRIPTOR_UUID
import com.teslamotors.protocol.util.countAutoIncrement

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
                tx: BluetoothGattCharacteristic,
                rx: BluetoothGattCharacteristic
            ) {
                txCharacteristic = tx
                rxCharacteristic = rx

                // enable notification
                mGatt.enableNotifications(rx, TESLA_RX_CHARACTERISTIC_DESCRIPTOR_UUID)
            }

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

    @Suppress("all")
    private val mScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            Log.d(TAG, "connecting!!! ---> : ${device.name} + ${device.address}")

            if (mScanning) {
                stopBleScan()
            }

            // todo ... core connect to the target ...
            connectTargetDevice(device)
        }

        override fun onScanFailed(errorCode: Int) {
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
    private fun startBleScan() {
        if (mScanning) {
            stopBleScan()
        } else {
            mScanning = true

            val scanFilter = ScanFilter.Builder()
                .setDeviceName(TESLA_BLUETOOTH_NAME)
                .build()

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            val filters = mutableListOf<ScanFilter>().apply {
                add(scanFilter)
            }
            mBluetoothUtil.mScanner.startScan(filters, scanSettings, mScanCallback)
        }
    }

    private fun stopBleScan() {
        mScanning = false
        mBluetoothUtil.mScanner.stopScan(mScanCallback)
    }

    // step2 ....................
    private fun addKey() {
        if (x963FormatPublicKey.isEmpty()) {
            Log.e(TAG, "addKey: x963FormatPublicKey is null")
            return
        }

        val requestMsg = AddKeyToWhiteListRequest().perform(x963FormatPublicKey)
        mGatt.writeCharacteristic(
            txCharacteristic,
            requestMsg,
            KEY_TO_WHITELIST_ADDING
        )
    }

    // step3 ....................
    private fun requestEphemeralKey() {
        val requestMsg = EphemeralKeyRequest().perform(keyStoreUtils.keyId)
        mGatt.writeCharacteristic(txCharacteristic, requestMsg, EPHEMERAL_KEY_REQUESTING)
    }

    // step 4 ...............................
    private fun authenticate() {
        val sharedKey: ByteArray = keyStoreUtils.sharedKey
        val requestMsg = AuthRequest().perform(this, sharedKey, countAutoIncrement())
        mGatt.writeCharacteristic(txCharacteristic, requestMsg, AUTHENTICATING)
    }

    // real control
    private fun openPassengerDoor() {
        val sharedKey: ByteArray = keyStoreUtils.sharedKey
        val requestMsg = ClosuresRequest().perform(this, sharedKey, countAutoIncrement())
        mGatt.writeCharacteristic(txCharacteristic, requestMsg, CLOSURES_REQUESTING)
    }

    private companion object {
        private const val TAG = "BluetoothLeService"
    }
}