package com.example.filetransferapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.filetransferapp.databinding.ActivityKeyboardBinding
import java.io.OutputStream
import java.util.UUID

class Keyboard : AppCompatActivity() {

    private lateinit var binding: ActivityKeyboardBinding

    private val bluetoothAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }
    private var btSocket: BluetoothSocket? = null
    private var btOutput: OutputStream? = null
    private var connectedDevice: BluetoothDevice? = null

    private val foundDevices = ArrayList<BluetoothDevice>()
    private lateinit var adapterList: android.widget.ArrayAdapter<String>

    private val prefs by lazy { getSharedPreferences("bt_prefs", Context.MODE_PRIVATE) }

    private val RFCOMM_UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val vibrator by lazy { getSystemService(VIBRATOR_SERVICE) as Vibrator }
    private val tone by lazy { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }


    /* -----------------------------------------------------
       UNIVERSAL SAFE WRAPPERS
       ----------------------------------------------------- */

    private inline fun <T> withConnectPermission(action: () -> T): T? {
        return if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else null
    }

    private inline fun <T> withScanPermission(action: () -> T): T? {
        return if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else null
    }


    /* -----------------------------------------------------
       ON CREATE
       ----------------------------------------------------- */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKeyboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ensureBluetoothOn()
        setupButtons()
        checkExistingConnection()
    }


    /* -----------------------------------------------------
       PERMISSION + BLUETOOTH ENABLE
       ----------------------------------------------------- */
    @SuppressLint("MissingPermission")
    private fun ensureBluetoothOn() {
        val adapter = bluetoothAdapter ?: return

        if (!adapter.isEnabled) {
            withConnectPermission { adapter.enable() }
                ?: openBluetoothSettings()
        }
    }

    private fun openBluetoothSettings() {
        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    private fun requestBtPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

        ActivityCompat.requestPermissions(this, perms, 2001)
    }


    /* -----------------------------------------------------
       CONNECT BUTTON LOGIC
       ----------------------------------------------------- */
    private fun connectSavedOrScan() {
        val savedMac = prefs.getString("saved_mac", null)

        if (savedMac == null) {
            startScan()
            return
        }

        val device = withConnectPermission {
            bluetoothAdapter?.getRemoteDevice(savedMac)
        }

        if (device == null) {
            startScan()
            return
        }

        tryConnect(device) { ok ->
            if (!ok) startScan()
        }
    }


    /* -----------------------------------------------------
       TRY CONNECT TO A DEVICE
       ----------------------------------------------------- */
    @SuppressLint("MissingPermission")
    private fun tryConnect(device: BluetoothDevice, callback: (Boolean) -> Unit) {

        Thread {

            withScanPermission { bluetoothAdapter?.cancelDiscovery() }

            val socket = withConnectPermission {
                device.createRfcommSocketToServiceRecord(RFCOMM_UUID)
            }

            val success = try {
                socket?.connect(); true
            } catch (e: Exception) {
                false
            }

            if (!success) {
                runOnUiThread { callback(false) }
                return@Thread
            }

            btSocket = socket
            btOutput = socket!!.outputStream
            connectedDevice = device

            prefs.edit()
                .putString("saved_mac", device.address)
                .putString("saved_name", device.name)
                .apply()

            runOnUiThread {
                checkExistingConnection()
                callback(true)
            }

        }.start()
    }


    /* -----------------------------------------------------
       START SCAN
       ----------------------------------------------------- */
    private fun startScan() {

        if (!checkScanPermission()) return

        foundDevices.clear()

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        registerReceiver(receiver, filter)

        withScanPermission { bluetoothAdapter?.startDiscovery() }

        showDevicePopup()
    }

    private fun checkScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (withScanPermission { } == null) {
                requestBtPermissions()
                false
            } else true
        } else {
            val ok = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!ok) requestBtPermissions()
            ok
        }
    }

    private fun showDevicePopup() {
        adapterList = android.widget.ArrayAdapter(this, android.R.layout.simple_list_item_1)

        AlertDialog.Builder(this)
            .setTitle("Select Raspberry Pi")
            .setSingleChoiceItems(adapterList, -1) { d, which ->
                tryConnect(foundDevices[which]) {}
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    /* -----------------------------------------------------
       RECEIVER
       ----------------------------------------------------- */
    @SuppressLint("MissingPermission")
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {

            when (intent?.action) {

                BluetoothDevice.ACTION_FOUND -> {

                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        ?: return

                    val name = withConnectPermission {
                        device.name?.lowercase()
                    } ?: return

                    val keys = listOf("pi", "rasp", "berry", "ap")
                    if (!keys.any { name.contains(it) }) return

                    foundDevices.add(device)
                    adapterList.add("${device.name} (${device.address})")
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (foundDevices.isEmpty()) {
                        Toast.makeText(this@Keyboard, "No Raspberry Pi found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }


    /* -----------------------------------------------------
       UI STATE
       ----------------------------------------------------- */
    @SuppressLint("MissingPermission")
    private fun checkExistingConnection() {
        val connected = btSocket != null && connectedDevice != null

        if (!connected) {
            binding.txtStatus.text = "Not Connected"
            setButtons(false)
            binding.btnConnect.isEnabled = true
            binding.btnDisconnect.isEnabled = false
            return
        }

        val name = withConnectPermission { connectedDevice?.name?.lowercase() }
        val keys = listOf("pi", "rasp", "berry", "ap")
        val isPi = name != null && keys.any { name.contains(it) }

        if (!isPi) {
            binding.txtStatus.text = "Connected to wrong device"
            setButtons(false)
            binding.btnConnect.isEnabled = true
            binding.btnDisconnect.isEnabled = true
            return
        }

        binding.txtStatus.text = "Connected to ${connectedDevice?.name}"
        setButtons(true)
        binding.btnConnect.isEnabled = false
        binding.btnDisconnect.isEnabled = true
    }


    /* -----------------------------------------------------
       DISCONNECT
       ----------------------------------------------------- */
    private fun disconnectBt() {
        try { btOutput?.close() } catch (_: Exception) {}
        try { btSocket?.close() } catch (_: Exception) {}

        btOutput = null
        btSocket = null
        connectedDevice = null

        checkExistingConnection()
    }


    /* -----------------------------------------------------
       SEND CHARACTER
       ----------------------------------------------------- */
    private fun send(msg: String) {
        if (btOutput == null) {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                btOutput?.write((msg + "\n").toByteArray())
            } catch (_: Exception) {}
        }.start()

        vibrator.vibrate(VibrationEffect.createOneShot(40, 120))
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
    }


    /* -----------------------------------------------------
       SMART CLICK LOGIC
       ----------------------------------------------------- */
    private fun smart(btn: android.widget.Button, one: String, two: String) {
        var last = 0L
        val time = 250L

        btn.setOnClickListener {
            val now = System.currentTimeMillis()

            if (now - last < time) {
                send(two)
                last = 0L
            } else {
                last = now
                btn.postDelayed({
                    if (System.currentTimeMillis() - last >= time) {
                        send(one)
                    }
                }, time)
            }
        }
    }


    /* -----------------------------------------------------
       SETUP ALL BUTTONS
       ----------------------------------------------------- */
    private fun setupButtons() {
        binding.btnConnect.setOnClickListener { connectSavedOrScan() }
        binding.btnDisconnect.setOnClickListener { disconnectBt() }
        binding.btnScan.setOnClickListener { startScan() }

        smart(binding.button1, "0", "A")
        smart(binding.button2, "1", "B")
        smart(binding.button3, "2", "C")
        smart(binding.button4, "3", "D")
        smart(binding.button5, "4", "E")
        smart(binding.button6, "5", "F")
        smart(binding.button7, "6", "G")
        smart(binding.button8, "7", "H")
        smart(binding.button9, "8", "I")
        smart(binding.button10, "9", "J")
    }


    private fun setButtons(enabled: Boolean) {
        listOf(
            binding.button1, binding.button2, binding.button3, binding.button4,
            binding.button5, binding.button6, binding.button7, binding.button8,
            binding.button9, binding.button10
        ).forEach { it.isEnabled = enabled }
    }


    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
    }
}
