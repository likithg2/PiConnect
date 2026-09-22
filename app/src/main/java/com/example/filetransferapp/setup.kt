package com.example.filetransferapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.*
import kotlin.concurrent.thread

class setup : AppCompatActivity() {

    private val TAG = "SetupActivity"

    // UI
    private lateinit var ipInput: EditText
    private lateinit var userInput: EditText
    private lateinit var passInput: EditText
    private lateinit var rememberCheck: CheckBox
    private lateinit var configureBtn: Button
    private lateinit var connectBtn: Button
    private lateinit var discoverBtn: Button
    private lateinit var knownBtn: Button
    private lateinit var homeBtn: Button
    private lateinit var statusText: TextView
    private lateinit var outputText: TextView
    private lateinit var togglePassBtn: ImageButton
    private lateinit var rootScroll:ScrollView
    private lateinit var wifiManager: WifiManager

    private var targetSSID: String? = null
    private var scanDialog: AlertDialog? = null

    // Broadcast receiver
    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateWiFiStateMessage()
        }
    }

    // Permissions for scanning WiFi list
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted =
                result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        result[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (granted) ensureLocationEnabled { loadFilteredWiFiList() }
            else Toast.makeText(this, "Location permission required.", Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        supportActionBar?.hide()

        bindViews()
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        registerReceiver(wifiReceiver, IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION))
        registerReceiver(wifiReceiver, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))

        loadSavedCredentials()
        updateConnectButtonState()
        updateWiFiStateMessage()

        configureBtn.setOnClickListener {
            ensureWifiEnabled { requestWiFiListPermission() }
        }

        discoverBtn.setOnClickListener {
            ensureWifiEnabled { discoverDynamic() }
        }

        knownBtn.setOnClickListener { showKnownDevices() }

        connectBtn.setOnClickListener { attemptSSHConnection() }

        homeBtn.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        togglePassBtn.setOnClickListener { togglePasswordVisibility() }

        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = updateConnectButtonState()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        ipInput.addTextChangedListener(watcher)
        userInput.addTextChangedListener(watcher)
        passInput.addTextChangedListener(watcher)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(wifiReceiver) } catch (_: Exception) {}
    }

    private fun bindViews() {
        ipInput = findViewById(R.id.ipInput)
        userInput = findViewById(R.id.userInput)
        passInput = findViewById(R.id.passInput)
        rememberCheck = findViewById(R.id.rememberCheck)
        configureBtn = findViewById(R.id.configureBtn)
        connectBtn = findViewById(R.id.connectBtn)
        discoverBtn = findViewById(R.id.discoverBtn)
        knownBtn = findViewById(R.id.disconnectBtn)
        homeBtn = findViewById(R.id.homeBtn)
        statusText = findViewById(R.id.statusText)
        outputText = findViewById(R.id.outputText)
        togglePassBtn = findViewById(R.id.togglePassBtn)
        rootScroll = findViewById(R.id.rootScroll)
    }

    // ---------------------------------------------------
    // ENCRYPTED PREFS
    // ---------------------------------------------------
    private fun getPrefs(): SharedPreferences {
        return try {
            val mk = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "pi_credentials", mk, applicationContext,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            getSharedPreferences("pi_credentials", MODE_PRIVATE)
        }
    }

    private fun loadSavedCredentials() {
        val prefs = getPrefs()
        ipInput.setText(prefs.getString("last_ip", ""))
        userInput.setText(prefs.getString("last_user", ""))
        passInput.setText(prefs.getString("last_pass", ""))
        rememberCheck.isChecked = prefs.getBoolean("remember", false)
    }

    private fun saveCredentials(ip: String, user: String, pass: String) {
        val prefs = getPrefs()
        prefs.edit().apply {
            putString("last_ip", ip)
            putString("last_user", user)
            putString("last_pass", pass)
            putBoolean("remember", rememberCheck.isChecked)

            val set = prefs.getStringSet("known_devices", mutableSetOf())!!.toMutableSet()
            set.add("$ip|$user|$pass")
            putStringSet("known_devices", set)

            apply()
        }
    }

    private fun showKnownDevices() {
        val prefs = getPrefs()
        val entries = prefs.getStringSet("known_devices", emptySet())!!.toList()

        if (entries.isEmpty()) {
            Toast.makeText(this, "No known devices", Toast.LENGTH_LONG).show()
            return
        }

        val names = entries.map {
            val parts = it.split("|")
            "${parts[0]} (${parts[1]})"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Known Devices")
            .setItems(names) { _, index ->
                val data = entries[index].split("|")
                ipInput.setText(data[0])
                userInput.setText(data[1])
                passInput.setText(data[2])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------------------------------------------
    // BUTTON UI HANDLING
    // ---------------------------------------------------
    private fun updateConnectButtonState() {
        val ok = ipInput.text.isNotBlank() &&
                userInput.text.isNotBlank() &&
                passInput.text.isNotBlank()
        connectBtn.isEnabled = ok
    }

    private fun togglePasswordVisibility() {
        val hidden = passInput.transformationMethod is android.text.method.PasswordTransformationMethod

        if (hidden) {
            passInput.transformationMethod = null
            togglePassBtn.setImageResource(R.drawable.ic_visibility)
        } else {
            passInput.transformationMethod =
                android.text.method.PasswordTransformationMethod.getInstance()
            togglePassBtn.setImageResource(R.drawable.ic_visibility_off)
        }

        passInput.setSelection(passInput.text.length)
    }

    // ---------------------------------------------------
    // WIFI ENABLE SYSTEM
    // ---------------------------------------------------
    private fun ensureWifiEnabled(onReady: () -> Unit) {
        val wifi = wifiManager

        if (!wifi.isWifiEnabled) {
            AlertDialog.Builder(this)
                .setTitle("Enable Wi-Fi")
                .setMessage("Wi-Fi must be ON to scan devices.")
                .setPositiveButton("Turn On") { _, _ ->
                    try {
                        wifi.isWifiEnabled = true
                    } catch (_: Exception) {}

                    // Open WiFi settings immediately
                    openWiFiSettings()

                    // Delay to avoid instant call before system enables it
                    rootScroll.postDelayed({ onReady() }, 1500)
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        // If WiFi already ON
        onReady()
    }

    private fun ensureLocationEnabled(onReady: () -> Unit) {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gps = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val net = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (gps || net) {
            onReady()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Enable Location")
            .setMessage("Location must be ON to scan Wi-Fi.")
            .setPositiveButton("Enable") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------------------------------------------
    // WIFI LIST
    // ---------------------------------------------------
    private fun requestWiFiListPermission() {
        val fine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine != PackageManager.PERMISSION_GRANTED &&
            coarse != PackageManager.PERMISSION_GRANTED) {

            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )

        } else {
            ensureLocationEnabled { loadFilteredWiFiList() }
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadFilteredWiFiList() {
        thread {
            try {
                wifiManager.startScan()
                Thread.sleep(700)

                val results = wifiManager.scanResults ?: emptyList()

                val filtered = results
                    .map { it.SSID }
                    .filter { it.isNotBlank() }
                    .filter {
                        val s = it.lowercase()
                        "pi" in s || "rasp" in s || "ap" in s|| "berry" in s
                    }
                    .distinct()

                runOnUiThread { showWiFiPicker(filtered) }

            } catch (e: Exception) {
                Log.e(TAG, "WiFi scan error: ${e.message}")
            }
        }
    }

    private fun showWiFiPicker(list: List<String>) {
        if (list.isEmpty()) {
            Toast.makeText(this, "No Pi hotspot found.", Toast.LENGTH_LONG).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Select Pi Hotspot")
            .setItems(list.toTypedArray()) { _, index ->
                targetSSID = list[index]
                openWiFiSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun updateWiFiStateMessage() {
        val ssid = wifiManager.connectionInfo?.ssid?.replace("\"", "")
        if (ssid == null) {
            statusText.text = "Connect to Pi hotspot"
            return
        }

        val s = ssid.lowercase()
        statusText.text =
            if ("pi" in s || "rasp" in s || "ap" in s)
                "Connected to $ssid"
            else
                "Connect to Pi hotspot"
    }

    private fun openWiFiSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            } else {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
    }

    // ---------------------------------------------------
    // DEVICE DISCOVERY
    // ---------------------------------------------------
    @SuppressLint("MissingPermission")
    private fun getMyIp(): String? {
        return try {
            val ip = wifiManager.connectionInfo.ipAddress
            String.format(
                "%d.%d.%d.%d",
                (ip and 0xff),
                (ip shr 8 and 0xff),
                (ip shr 16 and 0xff),
                (ip shr 24 and 0xff)
            )
        } catch (_: Exception) { null }
    }

    private fun getSubnetPrefix(ip: String): String {
        val p = ip.split(".")
        return "${p[0]}.${p[1]}.${p[2]}."
    }

    private fun discoverDynamic() {
        val ssid = wifiManager.connectionInfo?.ssid?.replace("\"", "")?.lowercase()

        if (ssid == null || !("pi" in ssid || "rasp" in ssid || "ap" in ssid)) {
            Toast.makeText(this, "Connect to Pi hotspot first.", Toast.LENGTH_LONG).show()
            return
        }

        scanSubnet { list ->
            if (list.isEmpty()) {
                Toast.makeText(this, "No devices found.", Toast.LENGTH_LONG).show()
            } else {
                showDevicePicker(list)
            }
        }
    }

    private fun scanSubnet(onResult: (List<String>) -> Unit) {
        showScanDialog()

        thread {
            val found = mutableListOf<String>()
            val myIp = getMyIp() ?: run {
                runOnUiThread { hideScanDialog() }
                return@thread
            }

            val subnet = getSubnetPrefix(myIp)

            for (i in 1..254) {
                val target = "$subnet$i"

                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(target, 22), 120)
                    socket.close()
                    found.add(target)
                } catch (_: Exception) {}

                if (i % 20 == 0) {
                    runOnUiThread {
                        scanDialog?.setMessage("Scanning…")
                    }
                }
            }

            runOnUiThread {
                hideScanDialog()
                onResult(found)
            }
        }
    }
    // ---------------------------------------------------
    // SCAN DIALOG
    // ---------------------------------------------------
    private fun showScanDialog() {
        scanDialog = AlertDialog.Builder(this)
            .setTitle("Scanning…")
            .setMessage("Searching for devices")
            .setCancelable(false)
            .create()

        scanDialog?.show()
    }

    private fun hideScanDialog() {
        try { scanDialog?.dismiss() } catch (_: Exception) {}
        scanDialog = null
    }

    private fun showDevicePicker(devices: List<String>) {
        val names = devices.map { "Device – $it" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Devices Found")
            .setItems(names) { _, index ->
                ipInput.setText(devices[index])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------------------------------------------
    // SSH CONNECTION
    // ---------------------------------------------------
    private fun attemptSSHConnection() {
        val ip = ipInput.text.toString().trim()
        val user = userInput.text.toString().trim()
        val pass = passInput.text.toString()

        if (ip.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Enter IP, username and password.", Toast.LENGTH_SHORT).show()
            return
        }

        connectBtn.isEnabled = false
        statusText.text = "Connecting to $ip..."

        thread {
            try {
                // 🔴 THIS is the FIRST and ONLY place
                // where initial SSH connection happens
                SessionManager.connect(ip, user, pass)

                if (!SessionManager.isSshConnected()) {
                    runOnUiThread {
                        statusText.text = "SSH connection failed"
                        connectBtn.isEnabled = true
                    }
                    return@thread
                }

                // sanity check (forces real connection)
                SessionManager.runCommand("echo connected")

                runOnUiThread {
                    statusText.text = "SSH Connected"
                    saveCredentials(ip, user, pass)

                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "SSH failed"
                    outputText.text = e.message
                    connectBtn.isEnabled = true
                }
            }
        }
    }

    private fun runSSH(ip: String, user: String, pass: String, cmd: String): String {
        val jsch = JSch()
        val session: Session = jsch.getSession(user, ip, 22)

        session.setPassword(pass)
        session.setConfig(Properties().apply { put("StrictHostKeyChecking", "no") })
        session.timeout = 8000
        session.connect()

        val channel = session.openChannel("exec") as ChannelExec
        val output = ByteArrayOutputStream()

        channel.setCommand(cmd)
        channel.outputStream = output
        channel.connect()

        while (!channel.isClosed) Thread.sleep(20)

        val result = output.toString().trim()
        channel.disconnect()
        session.disconnect()

        return result
    }
}
