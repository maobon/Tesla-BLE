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
import androidx.lifecycle.MutableLiveData
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
import com.teslamotors.protocol.util.STATUS_CODE_ERR
import com.teslamotors.protocol.util.STATUS_CODE_OK
import com.teslamotors.protocol.util.TESLA_BLUETOOTH_BEACON_LOCAL_NAME
import com.teslamotors.protocol.util.TESLA_MSG_OPERATION_STATUS_ERR
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
                displayDataAppendOnAc("Vehicle connected successful !!")

                sendMessage(
                    cMessenger,
                    ACTION_CONNECTING_RESP,
                    "vehicle connected successful",
                    STATUS_CODE_OK
                )
            }

            // todo core ... received from vehicle
            // response to Ac ....
            override fun onVehicleResponse(vcsecMsg: vcsec.FromVCSECMessage?) {
                displayDataAppendOnAc(vcsecMsg.toString())

                // use to judge what action is running .
                when (mGatt.opera) {
                    KEY_TO_WHITELIST_ADDING -> {
                        val isSucc = with(vcsecMsg!!) {
                            AddKeyVehicleResponse().perform(this, keyStoreUtils.keyId)
                        }
                        Log.d(TAG, "KEY_TO_WHITELIST_ADDING -> keystore process result: $isSucc")

                        // auto process next procedure .
                        // request to ephemeral key
                        if (isSucc) requestEphemeralKey()
                    }

                    EPHEMERAL_KEY_REQUESTING -> {
                        val sharedKey = with(vcsecMsg!!) {
                            EphemeralKeyVehicleResponse().perform(this@BluetoothLeService, this)
                        }
                        Log.d(TAG, "EPHEMERAL_KEY_REQUESTING -> get shared key: $sharedKey")

                        // auto process next procedure .
                        // authenticate . null requesting message to verify
                        if (sharedKey.isNotEmpty()) authenticate()
                    }

                    AUTHENTICATING, CLOSURES_REQUESTING -> {
                        Log.d(TAG, "AUTHENTICATING or CLOSURES_REQUESTING")
                        checkVehicleResponseMessageStatus(vcsecMsg!!) {
                            sendMessage(
                                cMessenger,
                                ACTION_CLOSURES_REQUESTING_RESP,
                                "Processing Successfully",
                                STATUS_CODE_OK
                            )
                        }
                    }

                    else -> {}
                }
            }

            override fun onError(type: GattErrorType, statusCode: Int?, desc: String?) {
                val str = "BluetoothLeService onError: ${type.name} desc:$desc"
                Log.e(TAG, str)
                displayDataAppendOnAc(str)
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
                if (localName.isNotEmpty()) Log.i(TAG, "onScanResult: completeLocalName=$localName")

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
            val err = "onScanFailed: scan callback err code: $errorCode"
            Log.e(TAG, err)
            if (mScanning) {
                stopBleScan()
            }
            displayDataAppendOnAc(err)
        }
    }

    private fun connectTargetDevice(bluetoothDevice: BluetoothDevice) {
        bluetoothDevice.connectGatt(
            this, false, mGattCallback, BluetoothDevice.TRANSPORT_LE
        )
    }

    /**
     * Service Handler
     * handle messages from Activity
     */
    internal class ServiceHandler(
        private val service: BluetoothLeService
    ) : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            service.apply {
                when (msg.what) {
                    ACTION_CLIENT_MESSENGER -> cMessenger = msg.replyTo

                    ACTION_CONNECTING -> startBleScan()
                    ACTION_OVERLAY_CONTROLLER_SHOW -> openOverlay()

                    ACTION_KEY_TO_WHITELIST_ADDING -> addKey()
                    ACTION_EPHEMERAL_KEY_REQUESTING -> requestEphemeralKey()
                    ACTION_AUTHENTICATING -> authenticate()

                    ACTION_CLOSURES_REQUESTING -> openPassengerDoor(if (msg.obj == null) false else msg.obj as Boolean)
                }
            }
        }
    }

    /**
     * use to stop foreground service
     *
     * @param intent
     * @param flags
     * @param startId
     * @return
     */
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

    /**
     * 1. Bluetooth Scan and Connect to vehicle
     * every time need do first
     */
    private fun startBleScan() {
        Log.i(TAG, "startBleScan: ")

        if (mScanning) {
            stopBleScan()
        } else {
            mScanning = true
            cleanDisplayCheckData()

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
        return ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setReportDelay(0).build()
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
                sendMessage(
                    cMessenger,
                    ACTION_CONNECTING_RESP,
                    "15s scanning process end, can not find my car!!"
                )
            }
        }
    }

    /**
     * add key to vehicle white list
     * step 1
     */
    private fun addKey() {
        Log.i(TAG, "addKey: ")
        if (x963FormatPublicKey.isEmpty()) {
            Log.e(TAG, "addKey: x963FormatPublicKey is null")
            return
        }

        val requestMsg = AddKeyToWhiteListRequest().perform(x963FormatPublicKey)

        var hint = "Tap Card"
        try {
            mGatt.writeCharacteristic(
                txCharacteristic, requestMsg, KEY_TO_WHITELIST_ADDING
            )
        } catch (e: UninitializedPropertyAccessException) {
            hint = "vehicle not connected. auto reconnect to vehicle."
            Log.e(TAG, hint)
            startBleScan()
        } catch (e1: Exception) {
            hint = e1.toString()
        }
        sendMessage(cMessenger, ACTION_TOAST, hint)
    }

    /**
     * request ephemeral key to vehicle
     * step 2
     */
    private fun requestEphemeralKey() {
        Log.i(TAG, "requestEphemeralKey: ")
        val requestMsg = EphemeralKeyRequest().perform(keyStoreUtils.keyId)
        mGatt.writeCharacteristic(txCharacteristic, requestMsg, EPHEMERAL_KEY_REQUESTING)
    }

    /**
     * authenticate
     * step 3
     * the last step in add key to white list procedure
     */
    private fun authenticate() {
        Log.i(TAG, "authenticate: ")
        // val sharedKey: ByteArray = keyStoreUtils.sharedKey
        val sharedKey: ByteArray? = useSharedKey()

        val requestMsg = AuthRequest().perform(this, sharedKey!!, countAutoIncrement())
        mGatt.writeCharacteristic(txCharacteristic, requestMsg, AUTHENTICATING)
    }

    /**
     * 2. Closures control request
     * real control. After add key to white list successful, next time only first to connect vehicle
     *
     * @param isFront
     */
    private fun openPassengerDoor(isFront: Boolean = false) {
        Log.i(TAG, "openPassengerDoor: ")
        // val sharedKey: ByteArray = keyStoreUtils.sharedKey
        val sharedKey: ByteArray = useSharedKey() ?: return

        val requestMsg = ClosuresRequest().perform(this, sharedKey, countAutoIncrement(), isFront)
        mGatt.writeCharacteristic(txCharacteristic, requestMsg, CLOSURES_REQUESTING)
    }

    /**
     * vehicle response data analysis
     *
     * @param resp vcsec.FromVCSECMessage
     * @param onExpected vehicle response process successful
     */
    private fun checkVehicleResponseMessageStatus(
        resp: vcsec.FromVCSECMessage, onExpected: () -> Unit
    ) = with(resp) {

        // authentication_request struct
        // Tesla response heart beat package
        resp.authenticationRequest?.let { request ->
            val token = request.sessionInfo.token
            sendMessage(
                cMessenger, ACTION_TOAST, "I am alive"
            )
            return@with
        }

        // Tesla MCU real response
        val errorDesc = commandStatus.operationStatus.name
        if (errorDesc == TESLA_MSG_OPERATION_STATUS_ERR) {
            val desc = commandStatus.signedMessageStatus.signedMessageInformation.name
            displayDataAppendOnAc("Received From Tesla: OPERATIONSTATUS_ERROR $desc")
            sendMessage(cMessenger, ACTION_CLOSURES_REQUESTING_RESP, desc, STATUS_CODE_ERR)
            Log.e(TAG, "Received From Tesla: OPERATIONSTATUS_ERROR $desc")

        } else {
            val respCounter = commandStatus.signedMessageStatus.counter
            if (respCounter > 100) {
                // successful response from vehicle
                onExpected.invoke()

            } else {
                Log.e(TAG, "checkVehicleResponseMessageStatus: command counter error")
                displayDataAppendOnAc("command counter error")

                sendMessage(
                    cMessenger, ACTION_CLOSURES_REQUESTING_RESP, "counter error", STATUS_CODE_ERR
                )
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

    companion object {
        private const val TAG = "BluetoothLeService"

        val printCheckData by lazy {
            MutableLiveData<String>().apply {
                value = ""
            }
        }
    }

    /**
     * display received message from Tesla on AC
     *
     * @param data
     */
    fun displayDataAppendOnAc(data: String) {
        val builder: StringBuilder = StringBuilder(printCheckData.value!!)
        builder.append(data)
        printCheckData.postValue(builder.toString())
    }

    /**
     * clean check data
     */
    private fun cleanDisplayCheckData() {
        printCheckData.postValue("")
    }
}