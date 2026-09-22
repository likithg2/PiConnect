package com.example.filetransferapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpProgressMonitor
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

class Receive : AppCompatActivity() {

    private lateinit var connectionStatus: TextView
    private lateinit var receivedFileName: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var receiveBtn: Button
    private lateinit var configureWifiBtn: Button
    private lateinit var backBtn: Button

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receive)
        supportActionBar?.hide()

        connectionStatus = findViewById(R.id.connectionStatus)
        receivedFileName = findViewById(R.id.receivedFileName)
        progressBar = findViewById(R.id.progressBar)
        receiveBtn = findViewById(R.id.receiveBtn)
        configureWifiBtn = findViewById(R.id.configureWifiBtn)
        backBtn = findViewById(R.id.back)

        initUI()
        requestPermissions()

        configureWifiBtn.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
        }

        receiveBtn.setOnClickListener { downloadAllFiles() }
        backBtn.setOnClickListener { finish() }
    }

    private fun initUI() {
        progressBar.progress = 0
        receiveBtn.isEnabled = false
        receiveBtn.alpha = 0.3f
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
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 44)
        }
    }

    // ✅ FIXED: repair SSH instead of just checking
    private fun refreshStatus() {
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
                    receiveBtn.isEnabled = false
                    receiveBtn.alpha = 0.3f
                } else {
                    connectionStatus.text = "✅ Connected to Pi"
                    receiveBtn.isEnabled = true
                    receiveBtn.alpha = 1f
                }
            }
        }
    }

    private fun downloadAllFiles() {
        progressBar.progress = 0
        receivedFileName.text = ""

        thread {
            try {
                // ✅ FIX: auto-reconnect before download
                SessionManager.connectIfNeeded()

                val sftp = SessionManager.ensureSftp()
                val remoteDir = "/home/${SessionManager.session!!.userName}/incoming"

                val rawList = sftp.ls(remoteDir)
                val fileList = mutableListOf<ChannelSftp.LsEntry>()

                for (item in rawList) {
                    val entry = item as ChannelSftp.LsEntry
                    if (!entry.filename.startsWith(".") && !entry.attrs.isDir) {
                        fileList.add(entry)
                    }
                }

                if (fileList.isEmpty()) {
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "No files in incoming folder",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@thread
                }

                val phoneRoot = File(Environment.getExternalStorageDirectory(), "URead")
                phoneRoot.mkdirs()

                for ((i, entry) in fileList.withIndex()) {

                    val fileName = entry.filename
                    val ext = fileName.substringAfterLast(".", "").lowercase()

                    val subDir = when (ext) {
                        "jpg", "jpeg", "png", "gif", "bmp", "webp" -> "Pictures"
                        "mp3", "wav", "aac", "flac", "ogg" -> "Audio"
                        "mp4", "mkv", "avi", "mov" -> "Videos"
                        "txt", "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx" -> "Documents"
                        else -> "Others"
                    }

                    val targetDir = File(phoneRoot, subDir)
                    targetDir.mkdirs()

                    val localFile = File(targetDir, fileName)
                    val output = FileOutputStream(localFile)

                    val remotePath = "$remoteDir/$fileName"
                    val size = entry.attrs.size

                    val monitor = object : SftpProgressMonitor {
                        var transferred = 0L

                        override fun init(op: Int, src: String?, dest: String?, max: Long) {}

                        override fun count(bytes: Long): Boolean {
                            transferred += bytes
                            val percent = ((transferred * 100) / size).toInt()
                            runOnUiThread { progressBar.progress = percent }
                            return true
                        }

                        override fun end() {}
                    }

                    runOnUiThread {
                        receivedFileName.text =
                            "Downloading: $fileName (${i + 1}/${fileList.size})"
                    }

                    sftp.get(remotePath, output, monitor)
                }

                runOnUiThread {
                    receivedFileName.text = "All files downloaded to URead/"
                    Toast.makeText(this, "Download complete!", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
