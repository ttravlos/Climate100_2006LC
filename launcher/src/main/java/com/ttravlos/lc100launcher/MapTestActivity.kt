package com.ttravlos.lc100launcher

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.net.HttpURLConnection
import java.net.URL

/**
 * Temporary direct tile-download test.
 *
 * This bypasses osmdroid completely. It downloads one known OpenStreetMap PNG,
 * decodes it with BitmapFactory, and displays it in an ImageView.
 */
class MapTestActivity : ComponentActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF202020.toInt())
        }

        val progress = ProgressBar(this).apply {
            isIndeterminate = true
        }

        val status = TextView(this).apply {
            text = "Downloading test tile…"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xAA000000.toInt())
            setPadding(24, 16, 24, 16)
        }

        val root = FrameLayout(this).apply {
            addView(imageView)
            addView(
                progress,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
            addView(
                status,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                ).apply {
                    bottomMargin = 32
                }
            )
        }

        setContentView(root)

        Thread {
            val tileUrl = "https://tile.openstreetmap.org/4/3/5.png"
            var connection: HttpURLConnection? = null

            try {
                connection = (URL(tileUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    requestMethod = "GET"
                    setRequestProperty(
                        "User-Agent",
                        "${packageName}/direct-tile-test"
                    )
                    setRequestProperty("Accept", "image/png")
                    instanceFollowRedirects = true
                    connect()
                }

                val responseCode = connection.responseCode
                val contentType = connection.contentType ?: "unknown"

                if (responseCode !in 200..299) {
                    throw IllegalStateException(
                        "HTTP $responseCode ${connection.responseMessage}"
                    )
                }

                val bitmap = connection.inputStream.use { input ->
                    BitmapFactory.decodeStream(input)
                } ?: throw IllegalStateException("BitmapFactory returned null")

                mainHandler.post {
                    progress.visibility = ProgressBar.GONE
                    imageView.setImageBitmap(bitmap)
                    status.text =
                        "SUCCESS: HTTP $responseCode, $contentType, ${bitmap.width}×${bitmap.height}"
                }
            } catch (error: Throwable) {
                mainHandler.post {
                    progress.visibility = ProgressBar.GONE
                    status.text = "FAILED: ${error.javaClass.simpleName}: ${error.message}"
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }
}
