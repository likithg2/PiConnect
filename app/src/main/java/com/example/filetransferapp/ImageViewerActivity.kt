package com.example.filetransferapp

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import java.io.File

class ImageViewerActivity : AppCompatActivity() {

    private lateinit var image: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var back: ImageButton
    private lateinit var title: TextView

    private var localPath = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        image = findViewById(R.id.ivImage)
        progress = findViewById(R.id.progressImage)
        back = findViewById(R.id.btnBackImage)
        title = findViewById(R.id.tvImageTitle)

        back.setOnClickListener { finish() }

        localPath = intent.getStringExtra("local_path") ?: ""

        if (localPath.isEmpty()) {
            Toast.makeText(this, "Invalid image", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        title.text = File(localPath).name

        loadImage()
    }

    private fun loadImage() {
        progress.visibility = View.VISIBLE

        Glide.with(this)
            .load(File(localPath))
            .into(image)

        image.visibility = View.VISIBLE
        progress.visibility = View.GONE
    }
}
