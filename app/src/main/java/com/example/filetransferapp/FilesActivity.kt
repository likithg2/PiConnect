package com.example.filetransferapp

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.app.ActivityCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jcraft.jsch.ChannelSftp
import kotlin.concurrent.thread
import java.io.File

class FilesActivity : AppCompatActivity() {

    private lateinit var fileList: RecyclerView
    private lateinit var pathText: TextView
    private lateinit var backBtn: ImageButton
    private lateinit var sortBtn: ImageButton
    private lateinit var searchBox: EditText
    private lateinit var globalProgress: ProgressBar

    private lateinit var adapter: FileAdapter
    private var currentPath = "/"
    private var fullList: List<FileItem> = emptyList()

    private var actionMode: ActionMode? = null
    private var currentDownloads = 0
    private var totalDownloads = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_files)
        supportActionBar?.hide()

        askStoragePermission()

        fileList = findViewById(R.id.fileList)
        pathText = findViewById(R.id.pathText)
        backBtn = findViewById(R.id.backBtn)
        sortBtn = findViewById(R.id.sortBtn)
        searchBox = findViewById(R.id.searchBox)
        globalProgress = findViewById(R.id.globalProgress)

        val user = SessionManager.loggedInUser
        currentPath = "/home/$user"

        adapter = FileAdapter(
            mutableListOf(),
            object : FileAdapter.Listener {

                override fun onItemClick(item: FileItem, pos: Int) {
                    if (adapter.getSelectedFiles().isNotEmpty()) {
                        updateActionModeTitle()
                        return
                    }
                    openItem(item)
                }

                override fun onSelectionChanged(count: Int) {
                    if (count > 0) {
                        if (actionMode == null) {
                            actionMode = startSupportActionMode(actionModeCallback)
                        }
                        updateActionModeTitle()
                    } else {
                        actionMode?.finish()
                    }
                }
            }
        )

        fileList.layoutManager = LinearLayoutManager(this)
        fileList.adapter = adapter

        loadDirectory(currentPath)

        backBtn.setOnClickListener {
            if (currentPath == "/") {
                finish()
                return@setOnClickListener
            }
            val parent = currentPath.substringBeforeLast('/', "")
            currentPath = if (parent.isEmpty()) "/" else parent
            loadDirectory(currentPath)
        }

        searchBox.addTextChangedListener { t ->
            val q = t?.toString()?.lowercase() ?: ""
            adapter.updateList(
                if (q.isEmpty()) fullList else fullList.filter { it.name.lowercase().contains(q) }
            )
        }

        sortBtn.setOnClickListener {
            val popup = PopupMenu(this, sortBtn)
            popup.menuInflater.inflate(R.menu.menu_sort, popup.menu)

            popup.setOnMenuItemClickListener { menu ->
                fullList = when (menu.itemId) {
                    R.id.sort_name -> fullList.sortedBy { it.name.lowercase() }
                    R.id.sort_type -> fullList.sortedBy { it.extension }
                    R.id.sort_size -> fullList.sortedBy { it.name.length }
                    else -> fullList
                }
                adapter.updateList(fullList)
                true
            }
            popup.show()
        }
    }

    private fun askStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
            return
        }

        val perms = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        ActivityCompat.requestPermissions(this, perms, 999)
    }

    private fun loadDirectory(path: String) {
        pathText.text = path

        thread {
            try {
                val list = SessionManager.listDirectory(path)

                runOnUiThread {
                    fullList = list
                    adapter.updateList(list)
                    currentPath = path
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "List failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    private fun openItem(item: FileItem) {

        if (item.isDirectory) {
            loadDirectory(item.fullPath)
            return
        }

        val ext = item.extension.lowercase()

        thread {
            try {
                val local = File(cacheDir, item.name)
                SessionManager.downloadFile(item.fullPath, local)

                runOnUiThread {

                    when {
                        ext in listOf("jpg","jpeg","png","gif","bmp","webp") ->
                            startActivity(
                                Intent(this, ImageViewerActivity::class.java)
                                    .putExtra("local_path", local.absolutePath)
                            )

                        ext in listOf("mp3","wav","aac","ogg","m4a") ->
                            startActivity(
                                Intent(this, AudioPlayerActivity::class.java)
                                    .putExtra("file_path", local.absolutePath)
                                    .putExtra("file_name", item.name)
                                    .putExtra("is_local", true)
                            )

                        ext == "pdf" ->
                            startActivity(
                                Intent(this, PdfViewerActivity::class.java)
                                    .putExtra("local_path", local.absolutePath)
                                    .putExtra("file_name", item.name)
                            )

                        else ->
                            startActivity(
                                Intent(this, FilePreviewActivity::class.java)
                                    .putExtra("local_path", local.absolutePath)
                                    .putExtra("remote_path", item.fullPath)
                            )
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Open error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val actionModeCallback = object : ActionMode.Callback {

        override fun onCreateActionMode(mode: ActionMode?, menu: android.view.Menu?): Boolean {
            menuInflater.inflate(R.menu.menu_file_actions, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: android.view.Menu?) = false

        override fun onActionItemClicked(mode: ActionMode?, item: android.view.MenuItem?): Boolean {

            val selected = adapter.getSelectedFiles()

            when (item?.itemId) {

                R.id.action_delete -> {
                    deleteSelected(selected)
                    mode?.finish()
                    return true
                }

                R.id.action_rename -> {
                    if (selected.size == 1) renameDialog(selected.first())
                    else Toast.makeText(this@FilesActivity, "Select only 1 file", Toast.LENGTH_SHORT).show()
                    mode?.finish()
                    return true
                }

                R.id.action_move -> {
                    val i = Intent(this@FilesActivity, FolderPickerActivity::class.java)
                    startActivityForResult(i, 501)
                    return true
                }

                R.id.action_new_folder -> {
                    createFolderDialog()
                    return true
                }

                R.id.action_download -> {
                    if (selected.isNotEmpty()) {
                        startDownload(selected)
                    }
                    mode?.finish()
                    return true
                }
            }
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            adapter.clearSelection()
            actionMode = null
        }
    }

    private fun updateActionModeTitle() {
        actionMode?.title = "${adapter.getSelectedFiles().size} selected"
    }

    private fun deleteSelected(files: List<FileItem>) {
        thread {
            try {
                files.forEach {
                    SessionManager.delete(it.fullPath, it.isDirectory)
                }
                runOnUiThread {
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                    loadDirectory(currentPath)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun renameDialog(item: FileItem) {
        val et = EditText(this)
        et.setText(item.name)

        AlertDialog.Builder(this)
            .setTitle("Rename")
            .setView(et)
            .setPositiveButton("OK") { _, _ ->
                val newName = et.text.toString().trim()
                if (newName.isNotEmpty()) {
                    thread {
                        try {
                            val parent = item.fullPath.substringBeforeLast('/')
                            SessionManager.rename(item.fullPath, "$parent/$newName")
                            runOnUiThread { loadDirectory(currentPath) }

                        } catch (e: Exception) {
                            runOnUiThread {
                                Toast.makeText(this, "Rename failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createFolderDialog() {
        val et = EditText(this)
        et.hint = "Folder name"

        AlertDialog.Builder(this)
            .setTitle("Create New Folder")
            .setView(et)
            .setPositiveButton("Create") { _, _ ->
                val name = et.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton

                thread {
                    try {
                        val sftp = SessionManager.ensureSftp()
                        sftp.mkdir("$currentPath/$name")
                        runOnUiThread { loadDirectory(currentPath) }

                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this, "Create failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)

        if (req == 501 && res == 2001) {

            val target = data?.getStringExtra("selected_folder") ?: return
            val selected = adapter.getSelectedFiles()

            thread {
                try {
                    selected.forEach {
                        SessionManager.move(it.fullPath, "$target/${it.name}")
                    }
                    runOnUiThread {
                        Toast.makeText(this, "Moved", Toast.LENGTH_SHORT).show()
                        loadDirectory(currentPath)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, "Move failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun startDownload(files: List<FileItem>) {
        globalProgress.progress = 0
        globalProgress.visibility = ProgressBar.VISIBLE

        totalDownloads = files.size
        currentDownloads = 0

        files.forEach {
            downloadOne(it)
        }
    }

    private fun downloadOne(item: FileItem) {
        thread {
            try {
                val root = getUreadDir()
                if (!root.exists()) root.mkdirs()

                val folder = when (item.extension) {
                    "jpg","jpeg","png","gif","bmp","webp" -> "Pictures"
                    "mp3","wav","aac","ogg","m4a" -> "Audio"
                    "pdf","txt","doc","docx","ppt","pptx" -> "Docs"
                    else -> "Others"
                }

                val destDir = File(root, folder)
                if (!destDir.exists()) destDir.mkdirs()

                val dest = File(destDir, item.name)
                SessionManager.downloadFile(item.fullPath, dest)

            } catch (_: Exception) {}

            synchronized(this) {
                currentDownloads++
                val progress = ((currentDownloads.toFloat() / totalDownloads) * 100).toInt()

                runOnUiThread {
                    globalProgress.progress = progress
                    if (currentDownloads == totalDownloads) {
                        globalProgress.visibility = ProgressBar.GONE
                        Toast.makeText(this, "Downloaded to Uread/", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
