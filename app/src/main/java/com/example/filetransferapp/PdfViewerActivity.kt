package com.example.filetransferapp

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class PdfViewerActivity : AppCompatActivity() {

    private lateinit var pdfImage: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var backBtn: ImageButton
    private lateinit var title: TextView
    private lateinit var nextBtn: ImageButton
    private lateinit var prevBtn: ImageButton

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var currentIndex = 0

    private var localPath = ""
    private var totalPages = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        pdfImage = findViewById(R.id.pdfImage)
        progress = findViewById(R.id.progressPdf)
        backBtn = findViewById(R.id.btnBackPdf)
        title = findViewById(R.id.tvPdfTitle)
        nextBtn = findViewById(R.id.btnNextPdf)
        prevBtn = findViewById(R.id.btnPrevPdf)

        // FIXED: Correct extra key
        localPath = intent.getStringExtra("local_path") ?: ""

        if (localPath.isEmpty()) {
            Toast.makeText(this, "Invalid PDF path", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        title.text = File(localPath).name
        backBtn.setOnClickListener { finish() }

        setupButtons()
        openPdf()
    }

    private fun openPdf() {
        try {
            val file = File(localPath)
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

            renderer = PdfRenderer(fileDescriptor!!)
            totalPages = renderer!!.pageCount

            renderPage(0)

        } catch (e: Exception) {
            Toast.makeText(this, "Failed to open PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun renderPage(index: Int) {
        progress.visibility = ProgressBar.VISIBLE

        currentPage?.close()
        currentPage = renderer!!.openPage(index)

        val page = currentPage!!
        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        pdfImage.setImageBitmap(bitmap)
        currentIndex = index

        progress.visibility = ProgressBar.GONE
    }

    private fun setupButtons() {
        prevBtn.setOnClickListener {
            if (currentIndex > 0) renderPage(currentIndex - 1)
        }

        nextBtn.setOnClickListener {
            if (renderer != null && currentIndex < renderer!!.pageCount - 1)
                renderPage(currentIndex + 1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentPage?.close()
        renderer?.close()
        fileDescriptor?.close()
    }
}
