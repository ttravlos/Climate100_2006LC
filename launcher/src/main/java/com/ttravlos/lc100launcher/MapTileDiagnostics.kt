package com.ttravlos.lc100launcher

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

/**
 * Temporary bounded diagnostics for the embedded OpenStreetMap panel.
 *
 * The log is written under:
 * Android/data/com.ttravlos.lc100launcher.dev/files/diagnostics/map-tile.log
 *
 * It records generated tile URLs plus a direct DNS/TLS/HTTP test against a
 * known tile. Failures are diagnostic only and must never crash the launcher.
 */
class MapTileDiagnostics(context: Context) {
    private val directory = File(
        context.getExternalFilesDir(null) ?: context.filesDir,
        "diagnostics"
    ).apply { mkdirs() }

    private val logFile = File(directory, "map-tile.log")
    private val timestampFormat =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var probeStarted = false

    @Synchronized
    fun log(message: String) {
        try {
            rotateIfNeeded()
            FileWriter(logFile, true).use { writer ->
                writer.append(timestampFormat.format(Date()))
                    .append(" ")
                    .append(message)
                    .append('\n')
            }
        } catch (_: Exception) {
            // Diagnostics must never interfere with launcher operation.
        }
    }

    fun probe() {
        if (probeStarted) return
        probeStarted = true

        Thread {
            val osmHost = "tile.openstreetmap.org"

            dnsProbe(osmHost)
            tcpProbe("1.1.1.1", 443)
            tcpProbe(osmHost, 443)
            httpsProbe("https://example.com/")
            httpsProbe("https://tile.openstreetmap.org/4/4/6.png")
        }.apply {
            name = "MapTileDiagnostics"
            isDaemon = true
            start()
        }
    }

    private fun dnsProbe(host: String) {
        try {
            val addresses = InetAddress.getAllByName(host)
                .joinToString { it.hostAddress ?: "(unknown)" }
            log("DNS host=$host addresses=$addresses")
        } catch (error: Exception) {
            log(
                "DNS-ERROR host=$host type=${error.javaClass.name} " +
                    "message=${error.message}"
            )
        }
    }

    private fun tcpProbe(host: String, port: Int) {
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(host, port), 10_000)
            log(
                "TCP host=$host port=$port connected=true " +
                    "local=${socket.localAddress?.hostAddress}:${socket.localPort} " +
                    "remote=${socket.inetAddress?.hostAddress}:${socket.port}"
            )
        } catch (error: Exception) {
            log(
                "TCP-ERROR host=$host port=$port type=${error.javaClass.name} " +
                    "message=${error.message}"
            )
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
                // Ignore close failures in diagnostics.
            }
        }
    }

    private fun httpsProbe(testUrl: String) {
        var connection: HttpsURLConnection? = null
        try {
            connection = URL(testUrl).openConnection() as HttpsURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty(
                "User-Agent",
                "com.ttravlos.lc100launcher.dev/0.9 (TEYES CC3 diagnostic)"
            )

            val responseCode = connection.responseCode
            val certificateSummary = connection.serverCertificates
                .joinToString(" | ") {
                    it.toString().lineSequence().firstOrNull() ?: "(unknown)"
                }

            log(
                "HTTPS url=$testUrl response=$responseCode " +
                    "contentType=${connection.contentType} " +
                    "contentLength=${connection.contentLengthLong} " +
                    "certificates=$certificateSummary"
            )
        } catch (error: Exception) {
            log(
                "HTTPS-ERROR url=$testUrl type=${error.javaClass.name} " +
                    "message=${error.message}"
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun rotateIfNeeded() {
        if (!logFile.exists() || logFile.length() < MAX_LOG_BYTES) return

        val oldFile = File(directory, "map-tile.previous.log")
        if (oldFile.exists()) oldFile.delete()
        logFile.renameTo(oldFile)
    }

    companion object {
        private const val MAX_LOG_BYTES = 256L * 1024L
    }
}
