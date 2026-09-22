package com.example.filetransferapp

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class FilePreviewActivity : AppCompatActivity() {

    private lateinit var text: TextView
    private lateinit var deleteBtn: Button
    private lateinit var downloadBtn: Button

    private lateinit var localPath: String
    private lateinit var remotePath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_preview)

        text = findViewById(R.id.previewText)
        deleteBtn = findViewById(R.id.deleteBtn)
        downloadBtn = findViewById(R.id.downloadBtn)

        localPath = intent.getStringExtra("local_path") ?: ""
        remotePath = intent.getStringExtra("remote_path") ?: ""

        if (localPath.isEmpty()) {
            Toast.makeText(this, "Invalid file", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val file = File(localPath)
        if (!file.exists()) {
            Toast.makeText(this, "File does not exist", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Unified preview logic (same as LocalFilePreviewActivity)
        text.text = readDocumentPreview(file)

        deleteBtn.setOnClickListener { deleteRemote() }
        downloadBtn.setOnClickListener {
            Toast.makeText(this, "Saved at: $localPath", Toast.LENGTH_LONG).show()
        }
    }

    private fun readDocumentPreview(file: File): String {
        return try {
            when (file.extension.lowercase()) {

                "txt" -> file.readText()

                "doc", "docx", "rtf" ->
                    extractTextFromBinary(file)

                else ->
                    file.readText()
            }
        } catch (e: Exception) {
            "Unable to preview this file.\n\n${e.message}"
        }
    }

    // Light extraction for doc/docx/rtf
    private fun extractTextFromBinary(file: File): String {
        return try {
            val raw = String(file.readBytes())
            // Filter out garbage chars
            raw.replace(Regex("[^\\x09\\x0A\\x0D\\x20-\\x7E]"), " ")
        } catch (e: Exception) {
            "Cannot display this document type."
        }
    }

    private fun deleteRemote() {
        Thread {
            try {
                SessionManager.delete(remotePath, false)

                runOnUiThread {
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                    finish()
                }

            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, e.message, Toast.LENGTH_LONG).show() }
            }
        }.start()
    }
}
