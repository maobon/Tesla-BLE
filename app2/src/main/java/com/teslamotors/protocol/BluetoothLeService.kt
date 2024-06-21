package com.teslamotors.protocol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import androidx.core.app.NotificationCompat
import com.teslamotors.protocol.ble.BluetoothUtil
import com.teslamotors.protocol.ble.ConnectionStateListener
import com.teslamotors.protocol.ble.GattCallback
import com.teslamotors.protocol.ble.GattErrorType
import com.teslamotors.protocol.ble.GattUtil
import com.teslamotors.protocol.ble.TeslaBeaconFactory
import com.teslamotors.protocol.keystore.KeyStoreUtils
import com.teslamotors.protocol.msg.Operations.AUTHENTICATING
import com.teslamotors.protocol.msg.Operations.CLOSURES_REQUESTING
import com.teslamotors.protocol.msg.Operations.EPHEMERAL_KEY_REQUESTING
import com.teslamotors.protocol.msg.Operations.KEY_TO_WHITELIST_ADDING
import com.teslamotors.protocol.msg.action.AuthRequest
import com.teslamotors.protocol.msg.action.ClosuresRequest
import com.teslamotors.protocol.msg.key.AddKeyToWhiteListRequest
import com.teslamotors.protocol.msg.key.AddKeyVehicleResponse
import com.teslamotors.protocol.msg.key.EphemeralKeyRequest
import com.teslamotors.protocol.msg.key.EphemeralKeyVehicleResponse
import com.teslamotors.protocol.ui.OverlayController
import com.teslamotors.protocol.ui.PartClickListener
import com.teslamotors.protocol.util.ACTION_AUTHENTICATING
import com.teslamotors.protocol.util.ACTION_AUTHENTICATING_RESP
import com.teslamotors.protocol.util.ACTION_CLIENT_MESSENGER
import com.teslamotors.protocol.util.ACTION_CLOSURES_REQUESTING
import com.teslamotors.protocol.util.ACTION_CLOSURES_REQUESTING_RESP
import com.teslamotors.protocol.util.ACTION_CONNECTING
import com.teslamotors.protocol.util.ACTION_CONNECTING_RESP
import com.teslamotors.protocol.util.ACTION_EPHEMERAL_KEY_REQUESTING
import com.teslamotors.protocol.util.ACTION_KEY_TO_WHITELIST_ADDING
import com.teslamotors.protocol.util.ACTION_OVERLAY_CONTROLLER_SHOW
import com.teslamotors.protocol.util.ACTION_TOAST
import com.teslamotors.protocol.util.CHANNEL_ID
import com.teslamotors.protocol.util.CHANNEL_NAME
import com.teslamotors.protocol.util.JUtils
import com.teslamotors.protocol.util.SERVICE_ACTION_STOP
import com.teslamotors.protocol.util.STATUS_CODE_OK
import com.teslamotors.protocol.util.TESLA_BLUETOOTH_BEACON_LOCAL_NAME
import com.teslamotors.protocol.util.TESLA_RX_CHARACTERISTIC_DESCRIPTOR_UUID
import com.teslamotors.protocol.util.countAutoIncrement
import com.teslamotors.protocol.util.sendMessage
import com.teslamotors.protocol.util.useSharedKey


@Suppress("all")
class BluetoothLeService : Service() {

    private var mScanning = false

    private lateinit var mOverlayController: OverlayController

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
        elevatePrivileges()

        mGattCallback = GattCallback(object : ConnectionStateListener {
            override fun onConnected(gatt: BluetoothGatt) {
                Log.d(TAG, "onConnected: vehicle connected successful ...")

                mGatt = GattUtil(gatt, this)
                // auto next step discovery services
                mGatt.discoveryServices()
            }

            override fun onGetCharacteristics(
                tx: BluetoothGattCharacteristic, rx: BluetoothGattCharacteristic
            ) {
                txCharacteristic = tx
                rxCharacteristic = rx

                // tesla enable notification
                mGatt.enableNotifications(rx, TESLA_RX_CHARACTERISTIC_DESCRIPTOR_UUID)

                // connect process completed ....
                // todo .... connection established ....
                sendMessage(
                    cMessenger,
                    ACTION_CONNECTING_RESP,
                    "vehicle connected successful",
                    STATUS_CODE_OK
                )
            }

            // response to Ac ....
            override fun onVehicleResponse(vcsecMsg: vcsec.FromVCSECMessage?) {
                when (mGatt.opera) { // use to judge what action is running ...

                    KEY_TO_WHITELIST_ADDING -> {
                        val ret = with(vcsecMsg!!) {
                            AddKeyVehicleResponse().perform(this, keyStoreUtils.keyId)
                        }
                        Log.d(
                            TAG,
                            "onVehicleResponse: KEY_TO_WHITELIST_ADDING -> keystore process result: $ret"
                        )

                        // todo add new sequence ...
                        if (ret) {
                            requestEphemeralKey()
                        }
                    }

                    EPHEMERAL_KEY_REQUESTING -> {
                        val sharedKey = with(vcsecMsg!!) {
                            EphemeralKeyVehicleResponse().perform(this@BluetoothLeService, this)
                        }
                        Log.d(
                            TAG,
                            "onVehicleResponse: EPHEMERAL_KEY_REQUESTING -> get shared key: $sharedKey"
                        )

                        // todo .... add sequence ....
                        if (sharedKey.isNotEmpty()) {
                            authenticate()
                        }
                    }

                    AUTHENTICATING -> {
                        Log.d(TAG, "onVehicleResponse: AUTHENTICATING ...")
                        checkVehicleResponseMessageStatus(vcsecMsg!!) {
                            sendMessage(
                                cMessenger, ACTION_AUTHENTICATING_RESP, "Auth Successfully"
                            )
                        }
                    }

                    CLOSURES_REQUESTING -> {
                        Log.d(TAG, "onVehicleResponse: CLOSURES_REQUESTING ...")
                        checkVehicleResponseMessageStatus(vcsecMsg!!) {
                            sendMessage(
                                cMessenger, ACTION_TOAST, "Closures OK"
                            )
                        }
                    }

                    else -> {}
                }
            }

            override fun onError(type: GattErrorType, statusCode: Int?, desc: String?) {
                Log.e(TAG, "onError: AC received ERROR: ${type.name} desc:$desc")
            }

        })
    }

    private fun elevatePrivileges() {
        // create the custom or default notification based on the android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startBluetoothForegroundService()
        } else {
            startForeground(1, Notification())
        }

        val partClickListener = object : PartClickListener {
            override fun onTopClick() {
                Log.i(TAG, "onTopClick: ...")
                openPassengerDoor(true)
            }

            override fun onBottomClick() {
                Log.i(TAG, "onBottomClick: ...")
                openPassengerDoor(false)
            }
        }

        // create an instance of Window class and display the content on screen
        mOverlayController = OverlayController(this@BluetoothLeService, partClickListener)
    }

    // for android version >=O
    // we need to create custom notification stating foreground service is running
    @RequiresApi(Build.VERSION_CODES.O)
    private fun startBluetoothForegroundService() {

        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
        )

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notificationBuilder = NotificationCompat.Builder(this@BluetoothLeService, CHANNEL_ID)
        val notification =
            notificationBuilder.setOngoing(true).setContentTitle("Tesla Bluetooth Util")
                .setContentText("Control vehicle doors") // this is important, otherwise the notification will show the way
                .setSmallIcon(R.drawable.ic_bluetooth)
                .setPriority(NotificationManager.IMPORTANCE_HIGH)
                .setCategory(Notification.CATEGORY_SERVICE).build()

        startForeground(2, notification)
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

            // Tesla iBeacon protocol
            // get complete local name from advertising data
            result.scanRecord?.advertisingDataMap?.get(9).let {
                val localName = java.lang.String(it)
                Log.i(TAG, "onScanResult: completeLocalName=$localName")

                if (TESLA_BLUETOOTH_BEACON_LOCAL_NAME.equals(localName)) {
                    Log.d(TAG, "onScanResult: === FIND MY CAR ===")

                    if (mScanning) {
                        stopBleScan()
                    }

                    // connect Tesla vehicle
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

    /**
     * Service Handler
     */
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

                ACTION_CLOSURES_REQUESTING -> {
                    Log.d(TAG, "handleMessage: check thread=${Thread.currentThread().name}")
                    val isFront = if (msg.obj == null) false else msg.obj as Boolean
                    service.openPassengerDoor(isFront)
                }

                ACTION_OVERLAY_CONTROLLER_SHOW -> {
                    service.openOverlay()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (SERVICE_ACTION_STOP == intent?.action) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
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

            // create scan filter
            val filters = mutableListOf<ScanFilter>().apply {
                add(
                    ScanFilter.Builder().setManufacturerData(
                        TeslaBeaconFactory.MANUFACTURER_ID,
                        TeslaBeaconFactory.getManufactureData(),
                        TeslaBeaconFactory.getManufactureDataMask()
                    ).build()
                )
            }

            mBluetoothUtil.mScanner.startScan(filters, getScanSettings(), mScanCallback)
            countdownTime(15 * 1000L)
        }
    }

    private fun getScanSettings(): ScanSettings {
        return ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setReportDelay(0)
            .build()
    }

    private fun countdownTime(ms: Long) {
        Log.d(TAG, "after 15s bluetooth scan process end")
        Handler(mainLooper).postDelayed({
            stopBleScan(true)
        }, ms)
    }

    private fun stopBleScan(sendAction: Boolean = false) {
        if (mScanning) {
            Log.d(TAG, "stopBleScan: stop scan")
            mScanning = false
            mBluetoothUtil.mScanner.stopScan(mScanCallback)

            if (sendAction) {
                sendMessage(cMessenger, ACTION_CONNECTING_RESP)
            }
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

        sendMessage(cMessenger, ACTION_TOAST, "Tap Card")
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
        // val sharedKey: ByteArray = keyStoreUtils.sharedKey
        val sharedKey: ByteArray? = useSharedKey()

        val requestMsg = AuthRequest().perform(this, sharedKey!!, countAutoIncrement())
        mGatt.writeCharacteristic(txCharacteristic, requestMsg, AUTHENTICATING)
    }

    // -----------------------------------------------
    // real control
    private fun openPassengerDoor(isFront: Boolean = false) {
        Log.i(TAG, "openPassengerDoor: ")
        // val sharedKey: ByteArray = keyStoreUtils.sharedKey
        val sharedKey: ByteArray = useSharedKey() ?: return

        val requestMsg = ClosuresRequest().perform(this, sharedKey, countAutoIncrement(), isFront)
        mGatt.writeCharacteristic(txCharacteristic, requestMsg, CLOSURES_REQUESTING)
    }

    // real action judge is ERROR or SUCC
    private fun checkVehicleResponseMessageStatus(
        resp: vcsec.FromVCSECMessage, onExpected: () -> Unit
    ) = with(resp) {
        val errorDesc = commandStatus.operationStatus.name
        if (errorDesc == "OPERATIONSTATUS_ERROR") {
            val desc = commandStatus.signedMessageStatus.signedMessageInformation.name
            Log.d(TAG, "checkVehicleResponseMessageStatus:  .... error desc= $desc")
            sendMessage(cMessenger, ACTION_CLOSURES_REQUESTING_RESP, desc)

        } else {
            // succ ...
            val respCounter = commandStatus.signedMessageStatus.counter
            if (respCounter > 100) {
                onExpected.invoke()
            } else {
                Log.d(TAG, "checkVehicleResponseMessageStatus: counter error")
            }
        }
    }

    fun openOverlay() {
        mOverlayController.openOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        mOverlayController.closeOverlay()
    }

    private companion object {
        private const val TAG = "BluetoothLeService"
    }
}