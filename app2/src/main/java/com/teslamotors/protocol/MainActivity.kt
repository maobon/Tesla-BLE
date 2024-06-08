package com.teslamotors.protocol

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.teslamotors.protocol.databinding.ActivityMain2Binding
import com.teslamotors.protocol.ui.DialogUtil
import com.teslamotors.protocol.util.createToast
import com.teslamotors.protocol.util.hasPermission
import com.teslamotors.protocol.util.hasRequiredBluetoothPermissions

class MainActivity : AppCompatActivity() {

    private val mPromptDialog: DialogUtil by lazy {
        DialogUtil(this@MainActivity)
    }

    private var mBluetoothLeService: BluetoothLeService? = null

    private val mBluetoothEnablingResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {

        if (it.resultCode == Activity.RESULT_OK) {

            // todo ....
            // Bluetooth is enabled, good to go
            createToast(this@MainActivity, "Bluetooth good 2 GO")
            mBluetoothLeService?.startBleScan()

        } else {
            // User dismissed or denied Bluetooth prompt
            // again and again until user enable bluetooth
            promptEnableBluetooth()
        }
    }

    inner class BluetoothServiceLeConn : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mBluetoothLeService = (service as BluetoothLeService.InnerBinder).getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mBluetoothLeService = null
        }
    }

    // -------------------------------------------------------------------------------------------

    private lateinit var rootView: ActivityMain2Binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rootView = ActivityMain2Binding.inflate(layoutInflater)
        setContentView(rootView.root)

        // scan and connect to vehicle
        rootView.btnTest1.setOnClickListener {
            if (!hasRequiredBluetoothPermissions()) {
                requestRelevantRuntimePermissions()
            } else {
                mBluetoothLeService?.startBleScan()
            }
        }

        // add key to white list
        rootView.btnTest2.setOnClickListener {
            mBluetoothLeService?.addKey()
        }

        // request ephemeral key
        rootView.btnTest3.setOnClickListener {
            mBluetoothLeService?.requestEphemeralKey()
        }

        // authenticate
        rootView.btnTest4.setOnClickListener {
            mBluetoothLeService?.authenticate()
        }

        // real control
        rootView.btnTest5.setOnClickListener {
            mBluetoothLeService?.openPassengerDoor()
        }
    }

    override fun onResume() {
        super.onResume()

        bindService(
            Intent(this@MainActivity, BluetoothLeService::class.java),
            BluetoothServiceLeConn(),
            Context.BIND_AUTO_CREATE
        )

        if (mBluetoothLeService?.isBluetoothEnable() != true) promptEnableBluetooth()
    }

    // -------------------------------------------------------------------------------------------
    private fun Activity.requestRelevantRuntimePermissions() {
        if (hasRequiredBluetoothPermissions()) return

        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> requestLocationPermission()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> requestBluetoothPermissions()
        }
    }

    private fun requestLocationPermission() = runOnUiThread {
        mPromptDialog.show(
            "Location permission required",
            "Starting from Android M (6.0), the system requires apps to be granted " + "location access in order to scan for BLE devices."
        ) { _, _ ->

            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                ), PERMISSION_REQUEST_CODE
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun requestBluetoothPermissions() = runOnUiThread {
        mPromptDialog.show(
            "Bluetooth permission required",
            "Starting from Android 12, the system requires apps to be granted " + "Bluetooth access in order to scan for and connect to BLE devices."
        ) { _, _ ->

            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT
                ), PERMISSION_REQUEST_CODE
            )
        }
    }

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
                requestRelevantRuntimePermissions()
            }

            allGranted && hasRequiredBluetoothPermissions() -> {
                // todo core method ...
                mBluetoothLeService?.startBleScan()
            }

            else -> {
                // Unexpected scenario encountered when handling permissions
                recreate()
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

        if (mBluetoothLeService?.isBluetoothEnable() != true) {
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                mBluetoothEnablingResult.launch(this)
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 100
    }
}