package com.example.filetransferapp

import android.view.*
import android.widget.*
import androidx.recyclerview.widget.RecyclerView

class FileAdapter(
    private var files: MutableList<FileItem>,
    private val listener: Listener
) : RecyclerView.Adapter<FileAdapter.VH>() {

    interface Listener {
        fun onItemClick(item: FileItem, pos: Int)   // OPEN
        fun onSelectionChanged(count: Int)          // UPDATE UI
    }

    private val selected = mutableSetOf<Int>()

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.fileIcon)
        val name: TextView = v.findViewById(R.id.fileName)
        val check: ImageView = v.findViewById(R.id.checkIcon)

        init {
            // ✅ OPEN on icon click
            icon.setOnClickListener {
                val pos = adapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                listener.onItemClick(files[pos], pos)
            }

            // ✅ OPEN on name click
            name.setOnClickListener {
                val pos = adapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                listener.onItemClick(files[pos], pos)
            }

            // ✅ SELECT ONLY via checkbox
            check.setOnClickListener {
                val pos = adapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                toggleSelection(pos)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val item = files[pos]
        holder.name.text = item.name

        holder.icon.setImageResource(
            when {
                item.isDirectory -> R.drawable.ic_folder
                item.extension == "pdf" -> R.drawable.ic_pdf
                item.extension in listOf("jpg","jpeg","png","gif") -> R.drawable.ic_image
                item.extension in listOf("mp3","wav","aac","m4a","ogg") -> R.drawable.ic_audio
                else -> R.drawable.ic_file
            }
        )

        holder.check.setImageResource(
            if (selected.contains(pos)) R.drawable.ic_check_on
            else R.drawable.ic_check_off
        )
    }

    override fun getItemCount(): Int = files.size

    private fun toggleSelection(pos: Int) {
        if (selected.contains(pos)) selected.remove(pos)
        else selected.add(pos)

        notifyItemChanged(pos)
        listener.onSelectionChanged(selected.size)
    }

    fun clearSelection() {
        val old = selected.toList()
        selected.clear()
        old.forEach { notifyItemChanged(it) }
        listener.onSelectionChanged(0)
    }

    fun updateList(newList: List<FileItem>) {
        files = newList.toMutableList()
        selected.clear()
        notifyDataSetChanged()
        listener.onSelectionChanged(0)
    }

    fun getSelectedFiles(): List<FileItem> =
        selected.map { files[it] }
}
