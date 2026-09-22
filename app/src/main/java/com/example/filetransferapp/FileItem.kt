package com.example.filetransferapp

data class FileItem(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean
) {
    val extension: String
        get() = name.substringAfterLast('.', "")
}

