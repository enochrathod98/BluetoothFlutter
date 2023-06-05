package com.example.bluetooth_application

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.util.Log
import androidx.annotation.RequiresApi
import java.lang.Exception
import java.util.UUID

class BluetoothService : Service() {

    companion object {
        private const val TAG = "BluetoothScanService"
    }

    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mBluetoothManager: BluetoothManager? = null
    private lateinit var bondedDevicesArray: Array<BluetoothDevice>
    private var sendReceive: BluetoothDataService.SendReceive? = null
    private lateinit var mBluetoothScanner: BluetoothLeScanner
    lateinit var mBluetoothGatt: BluetoothGatt
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
                        mBluetoothGatt.discoverServices()
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
                with(gatt) {
                    Log.w(
                        TAG,
                        "onServicesDiscovered--> Discovered ${this!!.services.size} services for ${device.address}"
                    )
                    printGattTable(gatt) // See implementation just above this section
                    // Consider connection setup as complete here
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                super.onCharacteristicRead(gatt, characteristic, value, status)
                with(characteristic) {
                    when (status) {
                        BluetoothGatt.GATT_SUCCESS -> {
                            val batteryLevel = characteristic.getIntValue(
                                BluetoothGattCharacteristic.FORMAT_UINT8,
                                0
                            );
                            Log.e(TAG, "battery level: $batteryLevel");
                        }

                        BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                            Log.e(TAG, "Read not permitted for $uuid!")
                        }

                        else -> {
                            Log.e(
                                TAG,
                                "Characteristic read failed for $uuid, error: $status"
                            )
                        }
                    }
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                super.onCharacteristicWrite(gatt, characteristic, status)
                with(characteristic) {
                    when (status) {
                        BluetoothGatt.GATT_SUCCESS -> {
                            Log.i("BluetoothGattCallback", "Wrote to characteristic ${this!!.uuid} | value: ${value.toHexString()}")
                        }
                        BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> {
                            Log.e("BluetoothGattCallback", "Write exceeded connection ATT MTU!")
                        }
                        BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                            Log.e("BluetoothGattCallback", "Write not permitted for ${this!!.uuid}!")
                        }
                        else -> {
                            Log.e("BluetoothGattCallback", "Characteristic write failed for ${this!!.uuid}, error: $status")
                        }
                    }
                }
            }
        }
    } else {
        TODO("VERSION.SDK_INT < JELLY_BEAN_MR2")
    }

    fun ByteArray.toHexString(): String =
        joinToString(separator = " ", prefix = "0x") {
            String.format("%02X", it)
        }

    private fun BluetoothGatt.printGattTable(gatt: BluetoothGatt?) {
        if (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                services.isEmpty()
            } else {
                TODO("VERSION.SDK_INT < JELLY_BEAN_MR2")
            }
        ) {
            Log.i(
                TAG,
                "No service and characteristic available, call discoverServices() first?"
            )
            return
        }
        services.forEach { service ->
            val characteristicsTable = service.characteristics.joinToString(
                separator = "\n|--",
                prefix = "|--"
            ) { it.uuid.toString() }
            Log.i(
                TAG,
                "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
            )

            //         attributes.put("00001801-0000-1000-8000-00805f9b34fb", "Generic Attribute");
            //        attributes.put("00002a05-0000-1000-8000-00805f9b34fb", "Service Changed");

            //        attributes.put("00002a00-0000-1000-8000-00805f9b34fb", "Device Name");
            //        attributes.put("00002a01-0000-1000-8000-00805f9b34fb", "Appearance");
            //        attributes.put("00002aa6-0000-1000-8000-00805f9b34fb", "Central Address Resolution");

            //

            if (service.uuid.toString() == "00001800-0000-1000-8000-00805f9b34fb") {
                readBatteryLevel(gatt)
            }
        }
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
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private fun readBatteryLevel(gatt: BluetoothGatt?) {
       // val batteryServiceUuid: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val batteryServiceUuid: UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
      //  val batteryLevelCharUuid: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        val batteryLevelCharUuid: UUID = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")

        val batteryService = mBluetoothGatt.getService(batteryServiceUuid)
        val batteryLevel = batteryService?.getCharacteristic(batteryLevelCharUuid)



        if (batteryLevel!!.isReadable()) {
            try {
                mBluetoothGatt.readCharacteristic(batteryLevel)
            } catch (ex: Exception) {
                ex.printStackTrace()
                Log.e(TAG, "Exception --> ${ex.printStackTrace()}")
            }
        }
  /*      val payload = "Hello"
        writeCharacteristic(batteryLevel!!, payload.toByteArray())*/
    }

    private fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, payload: ByteArray) {
        val writeType = when {
            characteristic.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.isWritableWithoutResponse() -> {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                Log.e(TAG, "writeCharacteristic")
            }

            else -> {
                error("Characteristic ${characteristic.uuid} cannot be written to")
            }
        }

        mBluetoothGatt?.let { gatt ->
            characteristic.writeType = writeType
            characteristic.value = payload
            gatt.writeCharacteristic(characteristic)
        } ?: error("Not connected to a BLE device!")
    }

    fun BluetoothGattCharacteristic.isReadable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

    fun BluetoothGattCharacteristic.isWritable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

    fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

    @SuppressLint("NewApi")
    fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
        return properties and property != 0
    }

    @SuppressLint("InlinedApi")
    fun BluetoothGattDescriptor.isReadable(): Boolean =
        containsPermission(BluetoothGattDescriptor.PERMISSION_READ) ||
                containsPermission(BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED) ||
                containsPermission(BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED_MITM)

    @SuppressLint("InlinedApi")
    fun BluetoothGattDescriptor.isWritable(): Boolean =
        containsPermission(BluetoothGattDescriptor.PERMISSION_WRITE) ||
                containsPermission(BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED) ||
                containsPermission(BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED_MITM) ||
                containsPermission(BluetoothGattDescriptor.PERMISSION_WRITE_SIGNED) ||
                containsPermission(BluetoothGattDescriptor.PERMISSION_WRITE_SIGNED_MITM)

    private fun BluetoothGattDescriptor.containsPermission(permission: Int): Boolean =
        permissions and permission != 0


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
            // F5:51:98:6A:26:E9 Endure
            //40:91:51:A6:8D:FE Muru
            bleScanner?.stopScan(scanCallback)
            for (i in scannedDevicesArrayBLE.indices) {
                if (scannedDevicesArrayBLE[i].device.address == "40:91:51:A6:8D:FE") {
                    with(scannedDevicesArrayBLE[i].device) {
                        Log.e(TAG, "DISCOVER--> Connecting..")
                        mBluetoothGatt = connectGatt(this@BluetoothService, false, gattCallback)
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