package com.teslamotors.protocol

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.teslamotors.protocol.ble.BluetoothUtil
import com.teslamotors.protocol.databinding.ActivityMainBinding
import com.teslamotors.protocol.ui.DialogUtil
import com.teslamotors.protocol.ui.DialogUtil.PERMISSION_REQUEST_CODE
import com.teslamotors.protocol.util.ACTION_AUTHENTICATING_RESP
import com.teslamotors.protocol.util.ACTION_CLIENT_MESSENGER
import com.teslamotors.protocol.util.ACTION_CLOSURES_REQUESTING_RESP
import com.teslamotors.protocol.util.ACTION_CONNECTING
import com.teslamotors.protocol.util.ACTION_CONNECTING_RESP
import com.teslamotors.protocol.util.ACTION_EPHEMERAL_KEY_REQUESTING_RESP
import com.teslamotors.protocol.util.ACTION_KEY_TO_WHITELIST_ADDING
import com.teslamotors.protocol.util.ACTION_KEY_TO_WHITELIST_ADDING_RESP
import com.teslamotors.protocol.util.ACTION_OVERLAY_CONTROLLER_SHOW
import com.teslamotors.protocol.util.ACTION_TOAST
import com.teslamotors.protocol.util.SERVICE_ACTION_STOP
import com.teslamotors.protocol.util.STATUS_CODE_ERR
import com.teslamotors.protocol.util.STATUS_CODE_OK
import com.teslamotors.protocol.util.createToast
import com.teslamotors.protocol.util.enableLocation
import com.teslamotors.protocol.util.hasPermission
import com.teslamotors.protocol.util.hasRequiredBluetoothPermissions
import com.teslamotors.protocol.util.isLocationEnable
import com.teslamotors.protocol.util.requestRelevantRuntimePermissions
import com.teslamotors.protocol.util.sendMessage
import com.teslamotors.protocol.util.useSharedKey

class MainActivity : AppCompatActivity() {

    private lateinit var rootView: ActivityMainBinding
    private lateinit var mBluetoothUtil: BluetoothUtil

    private var sMessenger: Messenger? = null
    private var mServiceConnImpl: ServiceConnection = BluetoothServiceLeConn()

    private val mClientHandler by lazy {
        ClientHandler(this@MainActivity)
    }

    private var mBound = false

    inner class BluetoothServiceLeConn : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mBound = true
            sMessenger = Messenger(service)

            Message.obtain().apply {
                what = ACTION_CLIENT_MESSENGER
                replyTo = Messenger(mClientHandler)
                sMessenger!!.send(this)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mBound = false
            sMessenger = null
        }
    }

    internal class ClientHandler(
        private val activity: MainActivity
    ) : Handler(Looper.getMainLooper()) {

        @RequiresApi(Build.VERSION_CODES.O)
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                ACTION_TOAST -> {
                    activity.createToast(activity, msg.obj as String)
                }

                // Bluetooth Connection Status ....
                ACTION_CONNECTING_RESP -> {
                    activity.apply {
                        rootView.btnTest1.isEnabled = true

                        if (msg.arg1 == STATUS_CODE_OK) {
                            if (useSharedKey()?.isNotEmpty() == true) {
                                sendMessage(sMessenger, ACTION_OVERLAY_CONTROLLER_SHOW)
                            }

                        } else {
                            val desc = msg.obj as String
                            if (!TextUtils.isEmpty(desc)) {
                                rootView.tvReceivedData.text = desc
                            }
                        }
                    }
                }

                ACTION_KEY_TO_WHITELIST_ADDING_RESP -> {}
                ACTION_EPHEMERAL_KEY_REQUESTING_RESP -> {}

                ACTION_AUTHENTICATING_RESP, ACTION_CLOSURES_REQUESTING_RESP -> {
                    val hint = msg.obj as String
                    if (!TextUtils.isEmpty(hint)) {
                        activity.createToast(activity, hint)
                    }

                    when (msg.arg1) {
                        STATUS_CODE_OK -> {
                            // change some ui
                            // show overlay controller
                            sendMessage(activity.sMessenger, ACTION_OVERLAY_CONTROLLER_SHOW)
                        }

                        STATUS_CODE_ERR -> {

                        }
                    }
                }
            }
        }
    }

    @SuppressLint("NewApi")
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rootView = ActivityMainBinding.inflate(layoutInflater)
        setContentView(rootView.root)

        mBluetoothUtil = BluetoothUtil(this@MainActivity)

        // -------------------------
        // scan and connect to vehicle
        rootView.btnTest1.setOnClickListener {

            if (!hasRequiredBluetoothPermissions()) {
                requestRelevantRuntimePermissions(
                    ::requestLocationPermission, ::requestBluetoothPermissions
                )
            } else {
                checkSwitchStatus {
                    it.isEnabled = false
                    rootView.tvReceivedData.text = ""
                    sendMessage(sMessenger, ACTION_CONNECTING)
                }
            }
        }

        // --------------------------------------
        rootView.btnTest1.setOnLongClickListener {
            // foreground service will be destroyed
            stopBluetoothLeService()
            finish()
            true
        }

        // ---------------------------------
        // ADD KEY
        // 1. add key to white list
        // 2. request ephemeral key
        // 3. authenticate
        rootView.btnTest2.setOnClickListener {
            sendMessage(sMessenger, ACTION_KEY_TO_WHITELIST_ADDING)
        }

        // ---------------------------------
        // real time display vehicle sending data
        BluetoothLeService.printCheckData.observe(this@MainActivity) { data ->
            Log.d(TAG, "AC print check data=$data")
            rootView.tvReceivedData.text = data
        }
    }

    override fun onResume() {
        super.onResume()
        startService()

        bindService(
            Intent(this@MainActivity, BluetoothLeService::class.java),
            mServiceConnImpl,
            Context.BIND_AUTO_CREATE
        )
    }

    @SuppressLint("NewApi")
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != PERMISSION_REQUEST_CODE) return

        val containsPermanentDenial = permissions.zip(grantResults.toTypedArray()).any {
            it.second == PackageManager.PERMISSION_DENIED && !ActivityCompat.shouldShowRequestPermissionRationale(
                this, it.first
            )
        }

        val containsDenial = grantResults.any {
            it == PackageManager.PERMISSION_DENIED
        }

        val allGranted = grantResults.all {
            it == PackageManager.PERMISSION_GRANTED
        }

        when {
            containsPermanentDenial -> {
                // TODO: Handle permanent denial (e.g., show AlertDialog with justification)
                // Note: The user will need to navigate to App Settings and manually grant
                // permissions that were permanently denied
            }

            containsDenial -> {
                requestRelevantRuntimePermissions(
                    ::requestLocationPermission, ::requestBluetoothPermissions
                )
            }

            allGranted && hasRequiredBluetoothPermissions() -> {
                // todo onExpected
                checkSwitchStatus {
                    rootView.btnTest1.isEnabled = false
                    sendMessage(sMessenger, ACTION_CONNECTING)
                }
            }

            else -> {
                // Unexpected scenario encountered when handling permissions
                // recreate()
            }
        }
    }

    /**
     * Prompts the user to enable Bluetooth via a system dialog.
     *
     * For Android 12+, [Manifest.permission.BLUETOOTH_CONNECT] is required to use
     * the [BluetoothAdapter.ACTION_REQUEST_ENABLE] intent.
     */
    private fun promptEnableBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            // Insufficient permission to prompt for Bluetooth enabling
            Log.e(TAG, "Insufficient permission to prompt for Bluetooth enabling")
            return
        }

        // request enable bluetooth
        if (!mBluetoothUtil.isBluetoothEnable()) {
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                mBluetoothEnablingResult.launch(this)
            }
        }
    }

    private val mBluetoothEnablingResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            // start scan
            checkSwitchStatus {
                rootView.btnTest1.isEnabled = false
                sendMessage(sMessenger, ACTION_CONNECTING)
            }
        } else {
            promptEnableBluetooth()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestLocationPermission() {
        DialogUtil.showReqPermissions(DialogUtil.PermissionType.Location, this)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestBluetoothPermissions() {
        DialogUtil.showReqPermissions(DialogUtil.PermissionType.Bluetooth, this)
    }

    // method to ask user to grant the Overlay permission
    private fun checkOverlayPermission(onProcedure: (() -> Unit)?) {
        if (!Settings.canDrawOverlays(this)) {
            // send user to the device settings
            val myIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            startActivity(myIntent)
            return
        }

        onProcedure?.invoke()
    }

    // method for starting the service
    private fun startService() {
        // check if the user has already granted the Draw over other apps permission
        if (Settings.canDrawOverlays(this)) {
            // start the service based on the android version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(Intent(this, BluetoothLeService::class.java))
            } else {
                startService(Intent(this, BluetoothLeService::class.java))
            }
        }
    }

    private fun stopBluetoothLeService() {
        Intent(this@MainActivity, BluetoothLeService::class.java).apply {
            action = SERVICE_ACTION_STOP
            startService(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (mBound) unbindService(mServiceConnImpl)
    }

    private companion object {
        private const val TAG = "MainActivity"
    }

    /**
     * before process expect procedure check switch status
     *
     * @param onProcedure expect procedure. null will only check switch status
     */
    private fun checkSwitchStatus(onProcedure: (() -> Unit)? = null) {
        if (!mBluetoothUtil.isBluetoothEnable()) {
            promptEnableBluetooth()
            return
        }
        if (!isLocationEnable()) {
            enableLocation(this@MainActivity)
            return
        }

        checkOverlayPermission {
            onProcedure?.invoke()
        }
    }
}