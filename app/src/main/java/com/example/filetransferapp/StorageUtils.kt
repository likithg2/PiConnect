package com.example.filetransferapp

import android.os.Environment
import java.io.File

fun getUreadDir(): File {
    return File(Environment.getExternalStorageDirectory(), "Uread")
}
