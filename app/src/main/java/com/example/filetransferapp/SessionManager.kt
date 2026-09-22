package com.example.filetransferapp

import com.jcraft.jsch.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.*

object SessionManager {

    var session: Session? = null
    private var sftp: ChannelSftp? = null

    var loggedInUser: String = ""

    // ★ FIX: store credentials for auto-reconnect
    private var lastIp: String? = null
    private var lastUser: String? = null
    private var lastPass: String? = null

    // --------------------------------------------------
    // CONNECT TO PI
    // --------------------------------------------------
    fun connect(ip: String, user: String, pass: String) {
        // ★ FIX: remember credentials
        lastIp = ip
        lastUser = user
        lastPass = pass

        if (session?.isConnected == true) return

        val jsch = JSch()
        val sess = jsch.getSession(user, ip, 22)

        loggedInUser = user

        val cfg = Properties()
        cfg["StrictHostKeyChecking"] = "no"
        cfg["ServerAliveInterval"] = "15"     // ★ FIX: keepalive
        cfg["ServerAliveCountMax"] = "3"
        sess.setConfig(cfg)

        sess.setPassword(pass)
        sess.connect(5000)

        session = sess
    }

    // ★ FIX: auto-reconnect if session died
    @Synchronized
    fun connectIfNeeded() {
        if (session?.isConnected == true) return

        val ip = lastIp
        val user = lastUser
        val pass = lastPass

        if (ip == null || user == null || pass == null) {
            throw IllegalStateException("SSH credentials not set")
        }

        connect(ip, user, pass)
    }

    // --------------------------------------------------
    // CHECK CONNECTION
    // --------------------------------------------------
    fun isSshConnected(): Boolean {
        return session?.isConnected == true
    }

    // --------------------------------------------------
    // ENSURE SFTP CHANNEL
    // auto-recreate if dropped
    // --------------------------------------------------
    fun ensureSftp(): ChannelSftp {
        // ★ FIX: recover SSH automatically
        connectIfNeeded()

        if (sftp == null || !sftp!!.isConnected) {
            val ch = session!!.openChannel("sftp") as ChannelSftp
            ch.connect(3000)
            sftp = ch
        }
        return sftp!!
    }

    // --------------------------------------------------
    // RUN SHELL COMMAND
    // --------------------------------------------------
    fun runCommand(cmd: String): String {
        // ★ FIX: recover SSH automatically
        connectIfNeeded()

        val channel = session!!.openChannel("exec") as ChannelExec
        val out = ByteArrayOutputStream()

        channel.setCommand(cmd)
        channel.outputStream = out
        channel.connect()

        while (!channel.isClosed) {
            Thread.sleep(20)
        }

        channel.disconnect()
        return out.toString()
    }

    // --------------------------------------------------
    // LIST REMOTE DIR
    // --------------------------------------------------
    fun listDirectory(path: String): List<FileItem> {
        val sftp = ensureSftp()
        val entries = sftp.ls(path)

        return entries.mapNotNull {
            val entry = it as ChannelSftp.LsEntry
            val name = entry.filename
            if (name == "." || name == "..") return@mapNotNull null

            FileItem(
                name = name,
                fullPath = if (path.endsWith("/")) path + name else "$path/$name",
                isDirectory = entry.attrs.isDir
            )
        }
    }

    // --------------------------------------------------
    // GET REMOTE FILE SIZE
    // --------------------------------------------------
    fun remoteFileSize(remote: String): Long {
        return ensureSftp().stat(remote).size
    }

    // --------------------------------------------------
    // DOWNLOAD FILE
    // --------------------------------------------------
    fun downloadFile(remote: String, local: File) {
        FileOutputStream(local).use { out ->
            ensureSftp().get(remote, out)
        }
    }

    // --------------------------------------------------
    // DOWNLOAD WITH PROGRESS
    // --------------------------------------------------
    fun downloadFileWithMonitor(remote: String, local: File, monitor: SftpProgressMonitor) {
        ensureSftp().get(remote, local.absolutePath, monitor)
    }

    // --------------------------------------------------
    // UPLOAD FILE
    // --------------------------------------------------
    fun uploadFile(input: InputStream, remote: String) {
        ensureSftp().put(input, remote)
    }

    // --------------------------------------------------
    // FILE OPERATIONS
    // --------------------------------------------------
    fun delete(remote: String, isDir: Boolean) {
        val sftp = ensureSftp()
        if (isDir) sftp.rmdir(remote) else sftp.rm(remote)
    }

    fun rename(old: String, new: String) {
        ensureSftp().rename(old, new)
    }

    fun move(old: String, new: String) {
        ensureSftp().rename(old, new)
    }

    // --------------------------------------------------
    // DISCONNECT
    // --------------------------------------------------
    fun disconnect() {
        try { sftp?.disconnect() } catch (_: Exception) {}
        try { session?.disconnect() } catch (_: Exception) {}
        sftp = null
        session = null
    }
}
