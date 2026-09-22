package com.example.filetransferapp

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import kotlin.concurrent.thread

class AudioPlayerActivity : AppCompatActivity() {

    private lateinit var playBtn: ImageButton
    private lateinit var prevBtn: ImageButton
    private lateinit var nextBtn: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrent: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvTitle: TextView
    private lateinit var progress: ProgressBar

    private var mediaPlayer: MediaPlayer? = null
    private var localFile: File? = null
    private var userIsSeeking = false

    private val handler = Handler(Looper.getMainLooper())

    private val updateRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                if (!userIsSeeking) {
                    seekBar.progress = it.currentPosition
                    tvCurrent.text = formatTime(it.currentPosition)
                }
            }
            handler.postDelayed(this, 300)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_player)

        playBtn = findViewById(R.id.btnPlay)
        prevBtn = findViewById(R.id.btnPrev)
        nextBtn = findViewById(R.id.btnNext)
        seekBar = findViewById(R.id.seekBar)
        tvCurrent = findViewById(R.id.tvCurrent)
        tvDuration = findViewById(R.id.tvDuration)
        tvTitle = findViewById(R.id.tvAudioTitle)
        progress = findViewById(R.id.progressAudio)

        val filePath = intent.getStringExtra("file_path")
        val isLocal = intent.getBooleanExtra("is_local", false)

        if (filePath == null) {
            Toast.makeText(this, "Audio path missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tvTitle.text = File(filePath).name

        progress.visibility = View.VISIBLE
        playBtn.isEnabled = false
        seekBar.isEnabled = false

        if (isLocal) loadLocal(File(filePath))
        else loadRemote(filePath)

        setupControls()
    }

    private fun loadLocal(file: File) {
        localFile = file
        initPlayer(file)
    }

    private fun loadRemote(remotePath: String) {
        thread {
            try {
                val fileName = remotePath.substringAfterLast("/")
                val tmp = File(cacheDir, "aud_${System.currentTimeMillis()}_$fileName")

                SessionManager.ensureSftp().get(remotePath, tmp.absolutePath)

                localFile = tmp

                runOnUiThread { initPlayer(tmp) }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Audio load failed: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun initPlayer(file: File) {
        try {
            mediaPlayer = MediaPlayer().apply {

                setDataSource(file.absolutePath)

                setOnPreparedListener { mp ->
                    progress.visibility = View.GONE

                    seekBar.max = mp.duration
                    tvDuration.text = formatTime(mp.duration)
                    tvCurrent.text = "00:00"

                    seekBar.isEnabled = true
                    playBtn.isEnabled = true

                    mp.start()
                    playBtn.setImageResource(android.R.drawable.ic_media_pause)

                    handler.post(updateRunnable)
                }

                setOnCompletionListener {
                    playBtn.setImageResource(android.R.drawable.ic_media_play)
                    seekBar.progress = 0
                    tvCurrent.text = "00:00"
                }

                prepareAsync()
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Player error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupControls() {
        playBtn.setOnClickListener {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    playBtn.setImageResource(android.R.drawable.ic_media_play)
                } else {
                    it.start()
                    playBtn.setImageResource(android.R.drawable.ic_media_pause)
                }
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(sb: SeekBar) {
                userIsSeeking = true
            }

            override fun onStopTrackingTouch(sb: SeekBar) {
                userIsSeeking = false
                mediaPlayer?.seekTo(sb.progress)
            }

            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) tvCurrent.text = formatTime(p)
            }
        })

        prevBtn.setOnClickListener {
            Toast.makeText(this, "No playlist yet", Toast.LENGTH_SHORT).show()
        }

        nextBtn.setOnClickListener {
            Toast.makeText(this, "No playlist yet", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatTime(ms: Int): String {
        val seconds = ms / 1000
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        mediaPlayer?.release()
        localFile?.delete()
    }
}
