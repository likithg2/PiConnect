package com.example.filetransferapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpProgressMonitor
import kotlin.concurrent.thread

class Send1 : AppCompatActivity() {

    private lateinit var connectionStatus: TextView
    private lateinit var fileNameText: TextView
    private lateinit var uploadStatusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var selectFileBtn: Button
    private lateinit var sendBtn: Button
    private lateinit var backBtn: Button
    private lateinit var configureWifiBtn: Button

    private var selectedFiles: List<Uri> = emptyList()
    private var isUploading = false
    private var statusLockedUntil = 0L

    private val handler = Handler(Looper.getMainLooper())

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 2000)
        }
    }

    private val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (!uris.isNullOrEmpty()) {
                selectedFiles = uris
                fileNameText.text = "Selected ${uris.size} files"
                sendBtn.isEnabled = true
                sendBtn.alpha = 1f
            } else {
                selectedFiles = emptyList()
                fileNameText.text = "No files selected"
                sendBtn.isEnabled = false
                sendBtn.alpha = 0.3f
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send1)
        supportActionBar?.hide()

        connectionStatus = findViewById(R.id.connectionStatus)
        fileNameText = findViewById(R.id.fileName)
        uploadStatusText = findViewById(R.id.uploadStatusText)
        progressBar = findViewById(R.id.progressBar)
        selectFileBtn = findViewById(R.id.selectFileBtn)
        sendBtn = findViewById(R.id.sendBtn)
        backBtn = findViewById(R.id.back)
        configureWifiBtn = findViewById(R.id.configureWifiBtn)

        initUI()
        requestPermissions()

        selectFileBtn.setOnClickListener {
            try {
                // ✅ AUTO-RECONNECT INSTEAD OF FAILING
                SessionManager.connectIfNeeded()
                pickFileLauncher.launch("*/*")
            } catch (e: Exception) {
                Toast.makeText(this, "Not connected to Raspberry Pi", Toast.LENGTH_SHORT).show()
            }
        }

        sendBtn.setOnClickListener {
            if (selectedFiles.isNotEmpty()) uploadMultipleFiles(selectedFiles)
        }

        configureWifiBtn.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
        }

        backBtn.setOnClickListener { finish() }
    }

    private fun initUI() {
        progressBar.progress = 0
        uploadStatusText.text = "Progress: 0%"
        fileNameText.text = "No files selected"
        selectFileBtn.alpha = 0.3f
        selectFileBtn.isEnabled = false
        sendBtn.alpha = 0.3f
        sendBtn.isEnabled = false
        configureWifiBtn.alpha = 0.3f
        configureWifiBtn.isEnabled = false
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun requestPermissions() {
        val needed = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty())
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 44)
    }

    // ✅ FIXED: REPAIR CONNECTION INSTEAD OF JUST CHECKING
    private fun refreshStatus() {
        val now = System.currentTimeMillis()
        if (now < statusLockedUntil || isUploading) return

        thread {
            val connected = try {
                SessionManager.connectIfNeeded()
                true
            } catch (_: Exception) {
                false
            }

            runOnUiThread {
                if (!connected) {
                    connectionStatus.text = "❌ Not connected"
                    configureWifiBtn.alpha = 1f
                    configureWifiBtn.isEnabled = true
                    selectFileBtn.alpha = 0.3f
                    selectFileBtn.isEnabled = false
                } else {
                    connectionStatus.text = "✅ Connected to Pi"
                    configureWifiBtn.alpha = 0.3f
                    configureWifiBtn.isEnabled = false
                    selectFileBtn.alpha = 1f
                    selectFileBtn.isEnabled = true
                }
            }
        }
    }

    private fun uploadMultipleFiles(uris: List<Uri>) {
        isUploading = true
        progressBar.progress = 0
        uploadStatusText.text = "Progress: 0%"

        thread {
            try {
                val sftp = SessionManager.ensureSftp()
                val total = uris.size
                var index = 0

                for (u in uris) {
                    index++
                    val fileName = getFileName(u)

                    runOnUiThread {
                        connectionStatus.text = "⬆ Uploading $fileName ($index/$total)"
                        fileNameText.text = "Sending: $fileName"
                        statusLockedUntil = System.currentTimeMillis() + 1200
                    }

                    uploadOne(sftp, u)

                    runOnUiThread {
                        connectionStatus.text = "📤 Sent $fileName"
                        uploadStatusText.text = "Transfer Successful"
                        statusLockedUntil = System.currentTimeMillis() + 1500
                    }
                }

                runOnUiThread {
                    isUploading = false
                    progressBar.progress = 100
                    uploadStatusText.text = "All Files Sent"
                    connectionStatus.text = "✅ All files sent!"
                    statusLockedUntil = System.currentTimeMillis() + 2500
                }

            } catch (e: Exception) {
                runOnUiThread {
                    isUploading = false
                    connectionStatus.text = "❌ Upload failed"
                    uploadStatusText.text = "Failed"
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun uploadOne(sftp: ChannelSftp, uri: Uri) {
        val input = contentResolver.openInputStream(uri)
            ?: throw Exception("Unable to open file")

        val fileName = getFileName(uri)
        val remote = "/home/${SessionManager.session!!.userName}/incoming/$fileName"

        val fileSize = contentResolver.openFileDescriptor(uri, "r")?.statSize ?: 0L

        val monitor = object : SftpProgressMonitor {
            var transferred = 0L

            override fun init(op: Int, src: String?, dest: String?, max: Long) {}

            override fun count(bytes: Long): Boolean {
                transferred += bytes
                val percent = ((transferred * 100) / fileSize).toInt()

                runOnUiThread {
                    progressBar.progress = percent
                    uploadStatusText.text = "Progress: $percent%"
                }
                return true
            }

            override fun end() {}
        }

        sftp.put(input, remote, monitor, ChannelSftp.OVERWRITE)
    }

    private fun getFileName(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && it.moveToFirst()) return it.getString(index)
        }
        return uri.lastPathSegment?.substringAfterLast("/") ?: "file"
    }
}
