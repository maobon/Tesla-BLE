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
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.teslamotors.protocol.ble.BluetoothUtil
import com.teslamotors.protocol.databinding.ActivityMainBinding
import com.teslamotors.protocol.ui.DialogUtil
import com.teslamotors.protocol.ui.DialogUtil.PERMISSION_REQUEST_CODE
import com.teslamotors.protocol.util.ACTION_AUTHENTICATING
import com.teslamotors.protocol.util.ACTION_AUTHENTICATING_RESP
import com.teslamotors.protocol.util.ACTION_CLIENT_MESSENGER
import com.teslamotors.protocol.util.ACTION_CLOSURES_REQUESTING
import com.teslamotors.protocol.util.ACTION_CLOSURES_REQUESTING_RESP
import com.teslamotors.protocol.util.ACTION_CONNECTING
import com.teslamotors.protocol.util.ACTION_CONNECTING_RESP
import com.teslamotors.protocol.util.ACTION_EPHEMERAL_KEY_REQUESTING
import com.teslamotors.protocol.util.ACTION_EPHEMERAL_KEY_REQUESTING_RESP
import com.teslamotors.protocol.util.ACTION_KEY_TO_WHITELIST_ADDING
import com.teslamotors.protocol.util.ACTION_KEY_TO_WHITELIST_ADDING_RESP
import com.teslamotors.protocol.util.ACTION_TOAST
import com.teslamotors.protocol.util.createToast
import com.teslamotors.protocol.util.hasPermission
import com.teslamotors.protocol.util.hasRequiredBluetoothPermissions
import com.teslamotors.protocol.util.requestRelevantRuntimePermissions
import com.teslamotors.protocol.util.sendMessage
import com.teslamotors.protocol.util.useSharedKey

class MainActivity : AppCompatActivity() {

    private lateinit var rootView: ActivityMainBinding
    private lateinit var mBluetoothUtil: BluetoothUtil

    private var sMessenger: Messenger? = null
    private val mClientHandler by lazy {
        ClientHandler(this@MainActivity)
    }

    private var mServiceConnImpl: ServiceConnection = BluetoothServiceLeConn()

    inner class BluetoothServiceLeConn : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            sMessenger = Messenger(service)

            Message.obtain().apply {
                what = ACTION_CLIENT_MESSENGER
                replyTo = Messenger(mClientHandler)
                sMessenger!!.send(this)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
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

                ACTION_CONNECTING_RESP -> {
                    Log.d(TAG, "handleMessage: ACTION_CONNECTING_RESP ...")

                    // 1. scan and connect result
                    if (msg.obj != null) {
                        val status = msg.obj as String
                        if (!TextUtils.isEmpty(status))
                            activity.rootView.tvConnectStatus.text = status
                    }

                    // 2. release button status
                    activity.rootView.btnTest1.isEnabled = true

                    // 3 ... some ui control
                    if (activity.useSharedKey() != null) {
                        activity.rootView.btnTest5.visibility = View.VISIBLE
                    } else {
                        activity.rootView.btnTest2.visibility = View.VISIBLE
                    }

                    // 4. post notification
                    // activity.mNotificationUtils.postNotification()
                }

                ACTION_KEY_TO_WHITELIST_ADDING_RESP -> {

                }

                ACTION_EPHEMERAL_KEY_REQUESTING_RESP -> {

                }

                ACTION_AUTHENTICATING_RESP -> {
                    Log.d(TAG, "handleMessage: MainAc received authenticate result ...")
                    activity.createToast(activity, msg.obj as String)

                    // change some ui
                    activity.rootView.btnTest5.visibility = View.VISIBLE
                    activity.rootView.btnTest2.visibility = View.INVISIBLE
                }

                ACTION_CLOSURES_REQUESTING_RESP -> {

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

        // scan and connect to vehicle
        rootView.btnTest1.setOnClickListener {
            if (!hasRequiredBluetoothPermissions()) {
                requestRelevantRuntimePermissions(
                    ::requestLocationPermission, ::requestBluetoothPermissions
                )
            } else {
                it.isEnabled = false
                rootView.tvConnectStatus.text = ""
                sendMessage(sMessenger, ACTION_CONNECTING)
            }
        }

        // ---------------------------------
        // add key to white list
        rootView.btnTest2.setOnClickListener {
            sendMessage(sMessenger, ACTION_KEY_TO_WHITELIST_ADDING)
        }

        // request ephemeral key
        rootView.btnTest3.setOnClickListener {
            sendMessage(sMessenger, ACTION_EPHEMERAL_KEY_REQUESTING)
        }

        // authenticate
        rootView.btnTest4.setOnClickListener {
            sendMessage(sMessenger, ACTION_AUTHENTICATING)
        }

        // ---------------------------------
        // real control .....
        rootView.btnTest5.setOnClickListener {
            sendMessage(sMessenger, ACTION_CLOSURES_REQUESTING)
        }

        rootView.btnTest5.setOnLongClickListener { _ ->
            sendMessage(sMessenger, ACTION_CLOSURES_REQUESTING, true)
            true
        }
    }

    override fun onResume() {
        super.onResume()

        if (!mBluetoothUtil.isBluetoothEnable()) {
            promptEnableBluetooth()
        }

        checkOverlayPermission()
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
                // todo core method ...
                sendMessage(sMessenger, ACTION_CONNECTING)
                rootView.btnTest1.isEnabled = false
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
            Log.d(TAG, "Insufficient permission to prompt for Bluetooth enabling")
            return
        }

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
            // mBluetoothLeService?.startBleScan()
            sendMessage(sMessenger, ACTION_CONNECTING)
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
    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            // send user to the device settings
            val myIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            startActivity(myIntent)
        }
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

    override fun onDestroy() {
        super.onDestroy()
        unbindService(mServiceConnImpl)
    }

    private companion object {
        private const val TAG = "MainActivity"
    }
}