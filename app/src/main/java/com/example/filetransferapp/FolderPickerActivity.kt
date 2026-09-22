package com.example.filetransferapp

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.concurrent.thread

class FolderPickerActivity : AppCompatActivity(), PickerAdapter.Listener {

    private lateinit var backBtn: ImageButton
    private lateinit var sortBtn: ImageButton
    private lateinit var searchBox: EditText
    private lateinit var folderList: RecyclerView
    private lateinit var selectBtn: ImageButton
    private lateinit var createBtn: ImageButton
    private lateinit var pathText: TextView

    private lateinit var adapter: PickerAdapter

    private var currentPath = "/"
    private var fullList: List<FSItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder_picker)
        supportActionBar?.hide()

        backBtn = findViewById(R.id.backBtn)
        sortBtn = findViewById(R.id.sortBtn)
        searchBox = findViewById(R.id.searchBox)
        folderList = findViewById(R.id.folderList)
        selectBtn = findViewById(R.id.selectBtn)
        createBtn = findViewById(R.id.createBtn)
        pathText = findViewById(R.id.pathText)

        currentPath = "/home/${SessionManager.loggedInUser}"

        adapter = PickerAdapter(mutableListOf(), this)
        folderList.layoutManager = LinearLayoutManager(this)
        folderList.adapter = adapter

        loadDirectory(currentPath)

        // Back
        backBtn.setOnClickListener {
            if (currentPath == "/") {
                finish()
                return@setOnClickListener
            }
            val parent = currentPath.substringBeforeLast('/', "")
            currentPath = if (parent.isEmpty()) "/" else parent
            loadDirectory(currentPath)
        }

        // Sort
        sortBtn.setOnClickListener {
            val popup = PopupMenu(this, sortBtn)
            popup.menuInflater.inflate(R.menu.menu_sort, popup.menu)

            popup.setOnMenuItemClickListener { item ->
                fullList = when (item.itemId) {
                    R.id.sort_name -> fullList.sortedBy { it.name.lowercase() }
                    else -> fullList
                }
                adapter.updateList(fullList)
                true
            }

            popup.show()
        }

        // Search
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.lowercase() ?: ""
                adapter.updateList(
                    if (q.isEmpty()) fullList
                    else fullList.filter { it.name.lowercase().contains(q) }
                )
            }
        })

        // Select
        selectBtn.setOnClickListener {
            val data = intent
            data.putExtra("selected_folder", currentPath)
            setResult(2001, data)
            finish()
        }

        // Create new folder
        createBtn.setOnClickListener { createNewFolder() }
    }

    // Load folders only
    private fun loadDirectory(path: String) {
        pathText.text = path

        thread {
            try {
                val original = SessionManager.listDirectory(path)   // List<FileItem>

                val list = original
                    .filter { it.isDirectory }      // correct Boolean
                    .map { fileItem ->
                        FSItem(
                            name = fileItem.name,
                            fullPath = fileItem.fullPath,
                            isDir = fileItem.isDirectory
                        )
                    }

                runOnUiThread {
                    fullList = list
                    adapter.updateList(list)
                    currentPath = path
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Load failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    override fun onFolderClick(item: FSItem) {
        if (item.isDir) loadDirectory(item.fullPath)
    }

    override fun onFolderLongPress(item: FSItem) {
        Toast.makeText(this, "Selected: ${item.name}", Toast.LENGTH_SHORT).show()
    }

    private fun createNewFolder() {
        val et = EditText(this)

        AlertDialog.Builder(this)
            .setTitle("New Folder")
            .setView(et)
            .setPositiveButton("Create") { _, _ ->
                val name = et.text.toString().trim()
                if (name.isNotEmpty()) {
                    thread {
                        try {
                            SessionManager.ensureSftp().mkdir("$currentPath/$name")
                            runOnUiThread { loadDirectory(currentPath) }
                        } catch (e: Exception) {
                            runOnUiThread {
                                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
