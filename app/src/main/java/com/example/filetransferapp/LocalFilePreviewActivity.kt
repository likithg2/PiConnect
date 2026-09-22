package com.example.filetransferapp

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class LocalFilePreviewActivity : AppCompatActivity() {

    private lateinit var backBtn: ImageButton
    private lateinit var titleText: TextView
    private lateinit var searchBox: EditText
    private lateinit var sortBtn: ImageButton
    private lateinit var pathText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var fileList: androidx.recyclerview.widget.RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_files)
        supportActionBar?.hide()

        backBtn = findViewById(R.id.backBtn)
        titleText = findViewById(R.id.titleText)
        searchBox = findViewById(R.id.searchBox)
        sortBtn = findViewById(R.id.sortBtn)
        pathText = findViewById(R.id.pathText)
        progressBar = findViewById(R.id.globalProgress)
        fileList = findViewById(R.id.fileList)

        val localPath = intent.getStringExtra("local_path") ?: ""
        if (localPath.isEmpty()) {
            Toast.makeText(this, "Invalid file", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val file = File(localPath)
        if (!file.exists()) {
            Toast.makeText(this, "File missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Make layout act like a preview screen
        titleText.text = file.name
        searchBox.visibility = View.GONE
        sortBtn.visibility = View.GONE
        pathText.visibility = View.GONE
        progressBar.visibility = View.GONE
        fileList.visibility = View.GONE

        // Create preview text
        val previewText = TextView(this).apply {
            textSize = 16f
            setTextColor(resources.getColor(R.color.app_text_primary))
            setPadding(16, 16, 16, 16)
            text = readTextForPreview(file)
        }

        // Add the preview text into root layout
        val root = findViewById<LinearLayout>(android.R.id.content)
            .getChildAt(0) as LinearLayout

        root.addView(previewText, root.childCount)

        backBtn.setOnClickListener { finish() }
    }

    private fun readTextForPreview(file: File): String {
        return try {
            when (file.extension.lowercase()) {

                "txt" -> file.readText()

                "doc", "docx", "rtf" ->
                    String(file.readBytes()) // best possible without heavy libraries

                else -> file.readText()
            }
        } catch (e: Exception) {
            "Unable to preview this file."
        }
    }
}
