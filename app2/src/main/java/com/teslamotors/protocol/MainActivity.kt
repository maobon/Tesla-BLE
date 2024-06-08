package com.teslamotors.protocol

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.BluetoothLeScanner
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.teslamotors.protocol.ble.BluetoothUtil
import com.teslamotors.protocol.ble.ConnectCallback
import com.teslamotors.protocol.ble.GattCallback
import com.teslamotors.protocol.ble.Operations
import com.teslamotors.protocol.ble.ScannerCallback
import com.teslamotors.protocol.databinding.ActivityMainBinding
import com.teslamotors.protocol.keystore.KeyStoreUtils
import com.teslamotors.protocol.msg.action.AuthRequest
import com.teslamotors.protocol.msg.action.ClosuresRequest
import com.teslamotors.protocol.msg.key.AddKeyToWhiteListRequest
import com.teslamotors.protocol.msg.key.AddKeyVehicleResponse
import com.teslamotors.protocol.msg.key.EphemeralKeyRequest
import com.teslamotors.protocol.msg.key.EphemeralKeyVehicleResponse
import com.teslamotors.protocol.util.JUtils
import com.teslamotors.protocol.util.MessageUtil
import com.teslamotors.protocol.vcsec.FromVCSECMessage

class MainActivity : AppCompatActivity() {

    var operations: Operations = Operations.KEY_TO_WHITELIST_ADDING

    companion object {
        const val TAG = "MainActivity"
        const val PERMISSION_REQUEST_CODE = 100
    }

    private lateinit var rootView: ActivityMainBinding

    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private lateinit var scannerCallback: ScannerCallback

    // 主类
    private lateinit var bluetoothGatt: BluetoothGatt

    // gatt
    private lateinit var gattCallback: GattCallback
    // private lateinit var service: BluetoothGattService

    private lateinit var characteristicTx: BluetoothGattCharacteristic
    private lateinit var characteristicRx: BluetoothGattCharacteristic


    private val keyStoreUtils: KeyStoreUtils = KeyStoreUtils.getInstance()
    private lateinit var x963FormatPublicKey: ByteArray

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rootView = ActivityMainBinding.inflate(layoutInflater)
        setContentView(rootView.root)

        initBluetooth()
        initKeystore()

        rootView.btnConnectToVehicle.setOnClickListener {
            connectVehicle()
        }

        rootView.btnAddkeyToWhitelist.setOnClickListener {
            addKey()
        }

        rootView.btnRequestEphemeralKey.setOnClickListener {
            requestEphemeralKey()
        }

        rootView.btnAuth.setOnClickListener {
            authenticate()
        }

        // real control
        rootView.btnOpenPassengerDoor.setOnClickListener {
            openPassengerDoor()
        }
    }

    private fun initBluetooth() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        bluetoothLeScanner = BluetoothUtil.checkSupport(this)

        gattCallback = GattCallback(
            this@MainActivity,
            object : ConnectCallback() {
                override fun onGetCharacteristics(
                    characteristicTx: BluetoothGattCharacteristic,
                    characteristicRx: BluetoothGattCharacteristic
                ) {
                    this@MainActivity.characteristicTx = characteristicTx
                    this@MainActivity.characteristicRx = characteristicRx
                }

                // CORE - CORE - CORE
                override fun onResponseFromVehicle(data: ByteArray) {

                    // 细节处理 ....
                    // 不断拼接报文 如果未完成则返回为空
                    // 拼接报文完成 直接返回全部报文
                    val fromVCSECMessage: FromVCSECMessage? = MessageUtil.autoChaCha(data)
                    Log.d(TAG, "onResponseFromVehicle: hahahha + $fromVCSECMessage")

                    fromVCSECMessage?.let {

                        when (operations) {
                            Operations.KEY_TO_WHITELIST_ADDING -> {
                                val ret = AddKeyVehicleResponse().perform(it, keyStoreUtils.keyId)
                                Log.d(TAG, "onAddKeyResponseFromVehicle: $ret")
                            }

                            Operations.EPHEMERAL_KEY_REQUESTING -> {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
                                    return

                                EphemeralKeyVehicleResponse().perform(this@MainActivity, it)
                            }

                            else -> {

                            }
                        }


                    }
                }
            }
        )

        scannerCallback = ScannerCallback(
            this,
            bluetoothLeScanner,
            gattCallback,
            object : ConnectCallback() {
                override fun onConnected(bluetoothGatt: BluetoothGatt) {
                    Log.d(TAG, "onConnected: 连接成功")

                    this@MainActivity.bluetoothGatt = bluetoothGatt
                }
            }
        )

        if (!BluetoothUtil.isPermissionsGranted(this)) requestPermissions(
            BluetoothUtil.permissions.toTypedArray(), PERMISSION_REQUEST_CODE
        )
    }

    private fun initKeystore() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            return

        x963FormatPublicKey = keyStoreUtils.getKeyPair(this)
        Log.d(TAG, "initKeystore: x963FormatPublicKey=${JUtils.bytesToHex(x963FormatPublicKey)}")
    }

    // step1
    private fun connectVehicle() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            return

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        )
            return

        Thread {
            bluetoothLeScanner.startScan(scannerCallback)
        }.start()
    }

    // step2
    private fun addKey() {
        if (x963FormatPublicKey.isEmpty())
            return

        operations = Operations.KEY_TO_WHITELIST_ADDING

        val requestMsg = AddKeyToWhiteListRequest().perform(x963FormatPublicKey)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) return

            Thread {
                // send msg to vehicle

                bluetoothGatt.writeCharacteristic(
                    characteristicTx,
                    requestMsg,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                )

            }.start()
        }
    }

    // step3
    private fun requestEphemeralKey() {
        operations = Operations.EPHEMERAL_KEY_REQUESTING

        val requestMsg = EphemeralKeyRequest().perform(keyStoreUtils.keyId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) return

            Thread {
                // send msg to vehicle

                bluetoothGatt.writeCharacteristic(
                    characteristicTx,
                    requestMsg,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                )

            }.start()
        }
    }

    // step 4
    private fun authenticate() {
        operations = Operations.AUTHENTICATING

        // val counter = 23; // sharedPreference ... maintain ....
        // val counter = 24; // sharedPreference ... maintain ....
        val counter = 30; // sharedPreference ... maintain .... 25号用过了

        // val sharedKey: ByteArray = Utils.hexToBytes("169F508FCCAB72B3DEE2A30B4BFD6598")
        val sharedKey: ByteArray = keyStoreUtils.sharedKey

        val requestMsg = AuthRequest().perform(this, sharedKey, counter)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            Thread {
                // send msg to vehicle

                bluetoothGatt.writeCharacteristic(
                    characteristicTx,
                    requestMsg,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                )

            }.start()
        }
    }

    // step 5 real control
    private fun openPassengerDoor() {
        operations = Operations.CLOSURES_REQUESTING

        // val counter = 23; // sharedPreference ... maintain ....
        // val counter = 24; // sharedPreference ... maintain ....
        // val counter = 25; // sharedPreference ... maintain .... 25号用过了
        val counter = 31; // sharedPreference ... maintain ....

        // val sharedKey: ByteArray = Utils.hexToBytes("169F508FCCAB72B3DEE2A30B4BFD6598")
        val sharedKey: ByteArray = keyStoreUtils.sharedKey

        val requestMsg = ClosuresRequest().perform(this, sharedKey, counter)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            Thread {
                // send msg to vehicle

                bluetoothGatt.writeCharacteristic(
                    characteristicTx,
                    requestMsg,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                )

            }.start()
        }
    }


    // ..........
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {

            Log.d(TAG, "onRequestPermissionsResult: grantResults = " + grantResults.size)

            var granted = true
            grantResults.forEach {
                granted = (it == 0)
            }

            if (!granted) finish()
        }

    }

    override fun onDestroy() {
        super.onDestroy()

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        bluetoothGatt.disconnect()
    }

}