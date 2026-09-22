package com.example.filetransferapp

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var txt: TextView
    private lateinit var connectionStatus: TextView

    private lateinit var sendBtn: Button
    private lateinit var receiveBtn: Button
    private lateinit var keyboardBtn: Button
    private lateinit var filesBtn: Button
    private lateinit var configureBtn: Button
    private lateinit var connectBtn: Button
    private lateinit var receivedFilesBtn: Button

    private var isOnline = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        initViews()
        setupButtons()

        // 🔥 SINGLE SOURCE OF TRUTH
        syncWithSession()
    }

    override fun onResume() {
        super.onResume()
        syncWithSession()
    }

    private fun initViews() {
        txt = findViewById(R.id.textView2)
        connectionStatus = findViewById(R.id.connectionStatus)

        sendBtn = findViewById(R.id.button1)
        receiveBtn = findViewById(R.id.button2)
        keyboardBtn = findViewById(R.id.button3)
        filesBtn = findViewById(R.id.button4)
        configureBtn = findViewById(R.id.button5)
        connectBtn = findViewById(R.id.button6)
        receivedFilesBtn = findViewById(R.id.button7)
    }

    // --------------------------------------------------
    // 🔑 SESSION SYNC (THIS FIXES YOUR ISSUE)
    // --------------------------------------------------
    private fun syncWithSession() {
        if (SessionManager.isSshConnected()) {
            setOnlineState()
        } else {
            setOfflineState()
        }
    }

    private fun setupButtons() {

        connectBtn.setOnClickListener {
            playSound(R.raw.m3)

            if (SessionManager.isSshConnected()) {
                setOnlineState()
                return@setOnClickListener
            }

            // No active session → go to setup
            startActivity(Intent(this, setup::class.java))
        }

        configureBtn.setOnClickListener {
            playSound(R.raw.m7)
            startActivity(Intent(this, setup::class.java))
        }

        sendBtn.setOnClickListener {
            if (!isOnline) {
                showOfflineMessage()
            } else {
                playSound(R.raw.m1)
                startActivity(Intent(this, Send1::class.java))
            }
        }

        receiveBtn.setOnClickListener {
            if (!isOnline) {
                showOfflineMessage()
            } else {
                playSound(R.raw.m2)
                startActivity(Intent(this, Receive::class.java))
            }
        }

        filesBtn.setOnClickListener {
            if (!isOnline) {
                showOfflineMessage()
            } else {
                playSound(R.raw.m6)
                startActivity(Intent(this, FilesActivity::class.java))
            }
        }

        keyboardBtn.setOnClickListener {
            playSound(R.raw.m5)
            startActivity(Intent(this, Keyboard::class.java))
        }

        receivedFilesBtn.setOnClickListener {
            playSound(R.raw.m4)
            startActivity(Intent(this, LocalFilesActivity::class.java))
        }
    }

    private fun playSound(soundId: Int) {
        try {
            val player = MediaPlayer.create(this, soundId)
            player.setOnCompletionListener { it.release() }
            player.start()
        } catch (_: Exception) {}
    }

    private fun showOfflineMessage() {
        Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show()
        setOfflineState()
    }

    private fun setOnlineState() {
        isOnline = true

        sendBtn.alpha = 1f
        receiveBtn.alpha = 1f
        filesBtn.alpha = 1f

        txt.text = "Status: Online"
        connectionStatus.text = "Connected to: ${SessionManager.session?.host}"
        connectionStatus.setBackgroundColor(getColor(R.color.light_green))
    }

    private fun setOfflineState() {
        isOnline = false

        sendBtn.alpha = 0.4f
        receiveBtn.alpha = 0.4f
        filesBtn.alpha = 0.4f

        txt.text = "Status: Offline"
        connectionStatus.text = "Not connected"
        connectionStatus.setBackgroundColor(
            ContextCompat.getColor(this, R.color.light_coral)
        )
    }
}
