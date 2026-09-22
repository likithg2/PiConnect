package com.example.filetransferapp.utils

import java.net.InetSocketAddress
import java.net.Socket

object PiConnection {
    fun isPiReachable(ip: String): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, 22), 800)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}
