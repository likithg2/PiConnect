package com.example.filetransferapp

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class LocalFilesActivity : AppCompatActivity() {

    private lateinit var fileList: RecyclerView
    private lateinit var pathText: TextView
    private lateinit var backBtn: ImageButton
    private lateinit var sortBtn: ImageButton
    private lateinit var searchBox: EditText

    private lateinit var adapter: FileAdapter
    private lateinit var rootDir: File
    private lateinit var currentDir: File

    private var fullList: List<FileItem> = emptyList()
    private var actionMode: ActionMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_files)
        supportActionBar?.hide()

        fileList = findViewById(R.id.fileList)
        pathText = findViewById(R.id.pathText)
        backBtn = findViewById(R.id.backBtn)
        sortBtn = findViewById(R.id.sortBtn)
        searchBox = findViewById(R.id.searchBox)

        // MAIN DIRECTORY → /storage/emulated/0/Uread
        rootDir = File(Environment.getExternalStorageDirectory(), "Uread")
        if (!rootDir.exists()) rootDir.mkdirs()

        currentDir = rootDir

        adapter = FileAdapter(mutableListOf(),
            object : FileAdapter.Listener {

                override fun onItemClick(item: FileItem, pos: Int) {
                    if (adapter.getSelectedFiles().isNotEmpty()) {
                        updateActionModeTitle()
                        return
                    }
                    openItem(item)
                }

                fun onItemLongClick(item: FileItem, pos: Int) {
                    if (actionMode == null) actionMode = startSupportActionMode(actionModeCallback)
                    updateActionModeTitle()
                }

                override fun onSelectionChanged(count: Int) {
                    if (count > 0 && actionMode == null)
                        actionMode = startSupportActionMode(actionModeCallback)
                    else if (count == 0)
                        actionMode?.finish()

                    updateActionModeTitle()
                }
            }
        )

        fileList.layoutManager = LinearLayoutManager(this)
        fileList.adapter = adapter

        loadDirectory(currentDir)

        backBtn.setOnClickListener {
            if (currentDir != rootDir) {
                currentDir = currentDir.parentFile!!
                loadDirectory(currentDir)
            } else finish()
        }

        searchBox.addTextChangedListener { t ->
            val q = t?.toString()?.lowercase() ?: ""
            adapter.updateList(if (q.isEmpty()) fullList else fullList.filter {
                it.name.lowercase().contains(q)
            })
        }

        sortBtn.setOnClickListener {
            val popup = PopupMenu(this, sortBtn)
            popup.menuInflater.inflate(R.menu.menu_sort, popup.menu)

            popup.setOnMenuItemClickListener { menu ->
                fullList = when (menu.itemId) {
                    R.id.sort_name -> fullList.sortedBy { it.name.lowercase() }
                    R.id.sort_date -> fullList.sortedBy { File(it.fullPath).lastModified() }
                    R.id.sort_size -> fullList.sortedBy { File(it.fullPath).length() }
                    R.id.sort_type -> fullList.sortedBy { it.extension.lowercase() }
                    else -> fullList
                }
                adapter.updateList(fullList)
                true
            }
            popup.show()
        }
    }

    private fun loadDirectory(dir: File) {
        val list = dir.listFiles()?.map {
            FileItem(
                name = it.name,
                fullPath = it.absolutePath,
                isDirectory = it.isDirectory
            )
        } ?: emptyList()

        fullList = list
        adapter.updateList(list)
        pathText.text = dir.absolutePath
    }

    private fun openItem(item: FileItem) {
        val file = File(item.fullPath)

        if (item.isDirectory) {
            currentDir = file
            loadDirectory(file)
            return
        }

        val ext = item.extension.lowercase()

        when (ext) {

            "jpg","jpeg","png","gif","bmp","webp" -> {
                startActivity(
                    Intent(this, ImageViewerActivity::class.java)
                        .putExtra("local_path", item.fullPath)
                )
            }

            "mp3","wav","aac","ogg","m4a" -> {
                startActivity(
                    Intent(this, AudioPlayerActivity::class.java)
                        .putExtra("file_path", item.fullPath)
                        .putExtra("is_local", true)
                )
            }

            "pdf" -> {
                startActivity(
                    Intent(this, PdfViewerActivity::class.java)
                        .putExtra("local_path", item.fullPath)
                )
            }

            "doc","docx","txt","rtf" -> {
                startActivity(
                    Intent(this, LocalFilePreviewActivity::class.java)
                        .putExtra("local_path", item.fullPath)
                )
            }

            else -> {
                startActivity(
                    Intent(this, LocalFilePreviewActivity::class.java)
                        .putExtra("local_path", item.fullPath)
                )
            }
        }
    }

    private fun updateActionModeTitle() {
        actionMode?.title = "${adapter.getSelectedFiles().size} selected"
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
                    selected.forEach { File(it.fullPath).deleteRecursively() }
                    loadDirectory(currentDir)
                    mode?.finish()
                    return true
                }

                R.id.action_rename -> {
                    if (selected.size == 1) showRenameDialog(selected.first())
                    mode?.finish()
                    return true
                }

                R.id.action_move -> {
                    showMoveDialog(selected)
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

    private fun showRenameDialog(item: FileItem) {
        val et = EditText(this)
        et.setText(item.name)

        AlertDialog.Builder(this)
            .setTitle("Rename")
            .setView(et)
            .setPositiveButton("OK") { _, _ ->
                val newName = et.text.toString()
                if (newName.isNotBlank()) {
                    val src = File(item.fullPath)
                    val dst = File(src.parentFile, newName)
                    src.renameTo(dst)
                    loadDirectory(currentDir)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMoveDialog(files: List<FileItem>) {
        val dirs = rootDir.walk().filter { it.isDirectory }.toList()
        val names = dirs.map { it.absolutePath }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Move to...")
            .setItems(names) { _, idx ->
                val target = dirs[idx]
                files.forEach {
                    val src = File(it.fullPath)
                    src.renameTo(File(target, src.name))
                }
                loadDirectory(currentDir)
            }
            .show()
    }
}
