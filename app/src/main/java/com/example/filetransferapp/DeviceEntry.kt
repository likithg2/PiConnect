package com.example.filetransferapp

data class DeviceEntry(
    val ip: String,
    val name: String = "",
    val user: String = "",
    val pass: String = ""
)