package com.example.filetransferapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PickerAdapter(
    private var folders: MutableList<FSItem>,
    private val listener: Listener
) : RecyclerView.Adapter<PickerAdapter.VH>() {

    interface Listener {
        fun onFolderClick(item: FSItem)
        fun onFolderLongPress(item: FSItem)
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.folderIcon)
        val name: TextView = v.findViewById(R.id.folderName)

        init {
            v.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onFolderClick(folders[pos])
                }
            }

            v.setOnLongClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onFolderLongPress(folders[pos])
                }
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_folder_picker, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = folders[position]
        holder.name.text = item.name
        holder.icon.setImageResource(R.drawable.ic_folder)
    }

    override fun getItemCount(): Int = folders.size

    fun updateList(newList: List<FSItem>) {
        folders = newList.toMutableList()
        notifyDataSetChanged()
    }
}
