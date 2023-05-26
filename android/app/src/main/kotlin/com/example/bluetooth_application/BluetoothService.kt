package com.example.bluetooth_application

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import androidx.core.app.ActivityCompat

class BluetoothService : Service() {

    companion object {
        private const val TAG = "BluetoothScanService"
    }

    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mBluetoothManager: BluetoothManager? = null
    private lateinit var bondedDevicesArray: Array<BluetoothDevice>
    private var sendReceive: BluetoothDataService.SendReceive? = null
    private lateinit var mBluetoothScanner: BluetoothLeScanner
    private val scannedDevicesArrayBLE = mutableListOf<ScanResult>()

    private val bleScanner by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBluetoothAdapter?.bluetoothLeScanner
        } else {
            TODO("VERSION.SDK_INT < LOLLIPOP")
        }
    }

    private val scanCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val indexQuery =
                    scannedDevicesArrayBLE.indexOfFirst { it.device.address == result.device.address }
                if (indexQuery != -1) { // A scan result already exists with the same address
                    scannedDevicesArrayBLE[indexQuery] = result
                } else {
                    with(result.device) {
                        Log.e(
                            TAG,
                            "BLE ----> Scanned BLE device! Name: ${name ?: "Unnamed"}, address: $address"
                        )
                    }
                    scannedDevicesArrayBLE.add(result)
                }

            }
        }
    } else {
        TODO("VERSION.SDK_INT < LOLLIPOP")
    }



    private val gattCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                val deviceAddress = gatt.device.address

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.w(TAG, "Successfully connected to $deviceAddress")
                        // TODO: Store a reference to BluetoothGatt
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.w(
                            TAG,
                            "Successfully disconnected from $deviceAddress"
                        )
                        gatt.close()
                    }
                } else {
                    Log.w(
                        TAG,
                        "Error $status encountered for $deviceAddress! Disconnecting..."
                    )
                    gatt.close()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
            }
        }
    } else {
        TODO("VERSION.SDK_INT < JELLY_BEAN_MR2")
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            mBluetoothManager =
                applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            mBluetoothAdapter = mBluetoothManager!!.adapter
        }

        val filter = IntentFilter()
        filter.addAction(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(receiver, filter)
    }


    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        enableBluetooth()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private var receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_OFF -> {
                        Log.e(TAG, "Bluetooth off")
                        enableBluetooth()
                    }

                    BluetoothAdapter.STATE_TURNING_OFF -> {
                        Log.e(TAG, "Bluetooth turning off")
                    }

                    BluetoothAdapter.STATE_ON -> {
                        Log.e(TAG, "Bluetooth On")
                        //   getPairedDevices()
                        initBLE()
                    }

                    BluetoothAdapter.STATE_TURNING_ON -> {
                        Log.e(TAG, "Bluetooth turning on")
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableBluetooth() {
        if (!mBluetoothAdapter!!.isEnabled) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            applicationContext.startActivity(intent)
        } else {
            //getPairedDevices()
            initBLE()
        }
    }

    @SuppressLint("MissingPermission")
    private fun initBLE() {
        val scanSettings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
        } else {
            TODO("VERSION.SDK_INT < LOLLIPOP")
        }
        Log.e(TAG, "DISCOVER--> START SCANNING")
        bleScanner?.startScan(null, scanSettings, scanCallback)

        Handler(Looper.getMainLooper()).postDelayed({
            bleScanner?.stopScan(scanCallback)
            for (i in scannedDevicesArrayBLE.indices){
                if (scannedDevicesArrayBLE[i].device.address == "F5:51:98:6A:26:E9"){
                    with(scannedDevicesArrayBLE[i].device){
                        connectGatt(this@BluetoothService, false, gattCallback)
                    }
                }
            }

            Log.e(TAG, "DISCOVER--> STOP SCANNING")
        }, 20000)
    }


    @SuppressLint("MissingPermission")
    fun getPairedDevices() {
        bondedDevicesArray = mBluetoothAdapter!!.bondedDevices.toTypedArray()
        for (device in bondedDevicesArray) {
            Log.e(
                TAG,
                "DISCOVER--> Paired device: ${device.name} + ${device.address} + ${device.bondState}"
            )
        }
        discoverNewDevice()
    }

    @SuppressLint("MissingPermission")
    private fun discoverNewDevice() {
        val discoverDeviceReceiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context?, intent: Intent?) {
                var action = ""
                if (intent != null) {
                    action = intent.action.toString()
                }
                when (action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        Log.e(TAG, "DISCOVER--> ACTION_STATE_CHANGED")

                    }

                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                        Log.e(TAG, "DISCOVER--> ACTION_DISCOVERY_STARTED")

                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.e(TAG, "DISCOVER--> ACTION_DISCOVERY_FINISHED")

                    }

                    BluetoothDevice.ACTION_FOUND -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent?.getParcelableExtra(
                                BluetoothDevice.EXTRA_DEVICE,
                                BluetoothDevice::class.java
                            )
                        } else {
                            intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        if (device != null) {
                            when (device.bondState) {
                                BluetoothDevice.BOND_NONE -> {
                                    Log.e(
                                        TAG,
                                        "DISCOVER--> Scanned Device: ${device.name} + ${device.address}  --> NONE"
                                    )
                                }

                                BluetoothDevice.BOND_BONDING -> {
                                    Log.e(
                                        TAG,
                                        "DISCOVER--> Scanned Device: ${device.name} + ${device.address}  --> BONDING"
                                    )

                                }

                                BluetoothDevice.BOND_BONDED -> {
                                    Log.e(
                                        TAG,
                                        "DISCOVER--> Scanned Device: ${device.name} + ${device.address}   --> BONDED"
                                    )
                                }
                            }
                            if (device.address == "20:34:FB:43:5C:68") {
                                // device.createBond()
                            }
                        }
                    }
                }
            }
        }

        val filter = IntentFilter()
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(discoverDeviceReceiver, filter)
        mBluetoothAdapter!!.startDiscovery()
    }


    fun createServer() {
        val serverClass =
            BluetoothDataService(adapter = mBluetoothAdapter!!, mHandler = mHandler).ServerClass()
        serverClass.start()
        Log.e(TAG, "DISCOVER--> createServer")
    }

    fun createClient() {
        val clientClass =
            BluetoothDataService(adapter = mBluetoothAdapter!!, mHandler = mHandler).ClientClass(
                bondedDevicesArray[0]
            )
        Log.e(TAG, "DISCOVER--> STATE_CONNECTING")
        clientClass.start()
        Log.e(TAG, "DISCOVER--> createClient")
    }

    fun sendString(message: String) {
        sendReceive?.write(message.toByteArray())
    }

    private val mHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            when (message.what) {
                Constants.STATE_LISTENING -> Log.e(TAG, "DISCOVER--> STATE_LISTENING")
                Constants.STATE_CONNECTING -> Log.e(TAG, "DISCOVER--> STATE_CONNECTING")
                Constants.STATE_CONNECTED -> Log.e(TAG, "DISCOVER--> STATE_CONNECTED")
                Constants.STATE_CONNECTION_FAILED -> Log.e(
                    TAG,
                    "DISCOVER--> STATE_CONNECTION_FAILED"
                )

                Constants.STATE_MESSAGE_RECEIVED -> {
                    val readBuff = message.obj as ByteArray
                    val tempMsg = String(readBuff, 0, message.arg1)
                    Log.e(TAG, "DISCOVER--> received Message --> $tempMsg")
                }
            }
        }
    }
}