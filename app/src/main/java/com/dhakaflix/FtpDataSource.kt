package com.dhakaflix

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/**
 * ExoPlayer সরাসরি ftp:// চালাতে পারে না, তাই এই কাস্টম DataSource।
 * - anonymous লগইন করে
 * - SIZE কমান্ড দিয়ে ফাইলের সাইজ জানে (seek করার জন্য জরুরি)
 * - seek করলে REST কমান্ড দিয়ে নির্দিষ্ট জায়গা থেকে ডাউনলোড শুরু করে
 */
@UnstableApi
class FtpDataSource : BaseDataSource(/* isNetwork = */ true) {

    private var client: FTPClient? = null
    private var stream: InputStream? = null
    private var uri: Uri? = null
    private var remaining = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        try {
            val host = dataSpec.uri.host ?: throw IOException("Missing FTP host")
            val port = if (dataSpec.uri.port > 0) dataSpec.uri.port else 21
            val path = dataSpec.uri.path ?: "/"

            val c = FTPClient()
            client = c
            c.setConnectTimeout(15_000)
            c.setControlEncoding("UTF-8")
            c.connect(host, port)
            c.setSoTimeout(30_000)
            if (!c.login("anonymous", "anonymous@dhakaflix.app")) throw IOException("FTP login refused")
            c.enterLocalPassiveMode()
            c.setFileType(FTP.BINARY_FILE_TYPE)

            // ফাইলের মোট সাইজ
            var size = -1L
            if (c.sendCommand("SIZE", path) == 213) {
                size = c.replyString.trim().split(' ').last().toLongOrNull() ?: -1L
            }

            // seek: নির্দিষ্ট বাইট থেকে শুরু
            if (dataSpec.position > 0) c.setRestartOffset(dataSpec.position)

            val s = c.retrieveFileStream(path)
                ?: throw IOException("FTP RETR failed: ${c.replyString?.trim()}")
            stream = s

            remaining = when {
                dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
                size >= 0 -> size - dataSpec.position
                else -> C.LENGTH_UNSET.toLong()
            }
        } catch (e: IOException) {
            close()
            throw e
        }
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val want = if (remaining == C.LENGTH_UNSET.toLong()) length else minOf(remaining, length.toLong()).toInt()
        val n = stream?.read(buffer, offset, want) ?: throw IOException("FTP stream closed")
        if (n == -1) {
            if (remaining != C.LENGTH_UNSET.toLong()) throw EOFException()
            return C.RESULT_END_OF_INPUT
        }
        if (remaining != C.LENGTH_UNSET.toLong()) remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try { stream?.close() } catch (_: Exception) {}
        try { client?.disconnect() } catch (_: Exception) {}
        stream = null
        client = null
        uri = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}
