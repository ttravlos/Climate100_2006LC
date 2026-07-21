package com.ryansteckler.lx470climate

import android.content.Context
import android.os.Debug
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small, bounded diagnostic logger for rare head-unit failures.
 * Logs live in the app's private files directory and rotate automatically.
 */
class DiagnosticLogger(context: Context) {
    private val directory = File(context.filesDir, "diagnostics").apply { mkdirs() }
    private val activeFile = File(directory, "climate.log")
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(message: String) {
        try {
            rotateIfNeeded()
            val timestamp = timestampFormat.format(Date())
            FileWriter(activeFile, true).use { writer ->
                writer.append(timestamp).append(' ').append(message).append('\n')
            }
        } catch (_: Exception) {
            // Diagnostics must never interfere with HVAC operation.
        }
    }

    fun logMemory(tag: String) {
        val runtime = Runtime.getRuntime()
        val javaUsedKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024
        val nativeUsedKb = Debug.getNativeHeapAllocatedSize() / 1024
        log("MEMORY tag=$tag javaUsedKb=$javaUsedKb nativeUsedKb=$nativeUsedKb")
    }

    private fun rotateIfNeeded() {
        if (!activeFile.exists() || activeFile.length() < MAX_FILE_BYTES) return

        val oldest = File(directory, "climate.$MAX_ARCHIVES.log")
        if (oldest.exists()) oldest.delete()

        for (index in MAX_ARCHIVES - 1 downTo 1) {
            val source = File(directory, "climate.$index.log")
            if (source.exists()) {
                source.renameTo(File(directory, "climate.${index + 1}.log"))
            }
        }
        activeFile.renameTo(File(directory, "climate.1.log"))
    }

    companion object {
        private const val MAX_FILE_BYTES = 256L * 1024L
        private const val MAX_ARCHIVES = 3
    }
}
