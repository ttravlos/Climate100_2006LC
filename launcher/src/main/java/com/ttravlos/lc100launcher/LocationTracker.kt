package com.ttravlos.lc100launcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centralized location acquisition and diagnostics for the launcher.
 *
 * Uses only Android framework APIs so it can work on a TEYES unit without
 * Google Play services. All important branches are written to gps.log.
 */
class LocationTracker(
    context: Context,
    private val onStateChanged: (LocationUiState) -> Unit
) : LocationListener {

    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val diagnostics = GpsDiagnostics(appContext)

    private var running = false
    private var bestLocation: Location? = null
    private var lastPublishedStatus: String? = null

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    fun permissionState(): String {
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return "fine=$fine coarse=$coarse"
    }

    fun start() {
        if (running) return
        running = true

        diagnostics.log("START sdk=${Build.VERSION.SDK_INT} permission=${permissionState()}")
        publishSystemSnapshot()

        if (!hasLocationPermission()) {
            publish(LocationUiState.permissionRequired("Location permission required"))
            diagnostics.log("START blocked: location permission missing")
            return
        }

        startWithPermission()
    }

    @SuppressLint("MissingPermission")
    private fun startWithPermission() {
        val providers = try {
            locationManager.allProviders.orEmpty()
        } catch (error: Throwable) {
            diagnostics.log("PROVIDERS-ERROR ${error.javaClass.name}: ${error.message}")
            emptyList()
        }

        diagnostics.log("PROVIDERS all=${providers.joinToString()}")

        val usableProviders = providers.filter { provider ->
            try {
                val enabled = locationManager.isProviderEnabled(provider)
                val properties = if (Build.VERSION.SDK_INT >= 31) {
                    locationManager.getProviderProperties(provider)?.toString()
                } else {
                    @Suppress("DEPRECATION")
                    locationManager.getProvider(provider)?.toString()
                }
                diagnostics.log("PROVIDER name=$provider enabled=$enabled properties=$properties")
                enabled
            } catch (error: Throwable) {
                diagnostics.log(
                    "PROVIDER-ERROR name=$provider ${error.javaClass.name}: ${error.message}"
                )
                false
            }
        }

        readLastKnownLocations(providers)

        var subscriptions = 0
        for (provider in usableProviders) {
            try {
                locationManager.requestLocationUpdates(
                    provider,
                    UPDATE_INTERVAL_MS,
                    MIN_DISTANCE_METERS,
                    this
                )
                subscriptions++
                diagnostics.log("SUBSCRIBE provider=$provider success=true")
            } catch (error: Throwable) {
                diagnostics.log(
                    "SUBSCRIBE-ERROR provider=$provider ${error.javaClass.name}: ${error.message}"
                )
            }
        }

        when {
            subscriptions > 0 && bestLocation != null -> publishLocation(
                bestLocation!!,
                "Using last known fix; waiting for live GPS"
            )
            subscriptions > 0 -> publish(
                LocationUiState.searching(
                    "Searching for GPS ($subscriptions provider${if (subscriptions == 1) "" else "s"})"
                )
            )
            providers.isEmpty() -> publish(
                LocationUiState.error("No Android location providers found")
            )
            else -> publish(
                LocationUiState.disabled("Location providers are disabled")
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun readLastKnownLocations(providers: List<String>) {
        for (provider in providers) {
            try {
                val location = locationManager.getLastKnownLocation(provider)
                if (location == null) {
                    diagnostics.log("LAST-KNOWN provider=$provider value=null")
                } else {
                    diagnostics.log("LAST-KNOWN ${locationSummary(location)}")
                    considerLocation(location, publishImmediately = false)
                }
            } catch (error: Throwable) {
                diagnostics.log(
                    "LAST-KNOWN-ERROR provider=$provider ${error.javaClass.name}: ${error.message}"
                )
            }
        }
    }

    fun onPermissionResult() {
        diagnostics.log("PERMISSION-RESULT ${permissionState()}")
        if (!running) return

        if (hasLocationPermission()) {
            startWithPermission()
        } else {
            publish(LocationUiState.permissionDenied("Location permission denied"))
        }
    }

    fun stop() {
        if (!running) return
        running = false
        try {
            locationManager.removeUpdates(this)
            diagnostics.log("STOP removeUpdates success=true")
        } catch (error: Throwable) {
            diagnostics.log("STOP-ERROR ${error.javaClass.name}: ${error.message}")
        }
    }

    override fun onLocationChanged(location: Location) {
        diagnostics.log("UPDATE ${locationSummary(location)}")
        considerLocation(location, publishImmediately = true)
    }

    @Deprecated("Deprecated in Android API")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        diagnostics.log("STATUS provider=$provider status=$status extras=$extras")
    }

    override fun onProviderEnabled(provider: String) {
        diagnostics.log("PROVIDER-ENABLED name=$provider")
        publish(LocationUiState.searching("$provider enabled; searching for fix"))
    }

    override fun onProviderDisabled(provider: String) {
        diagnostics.log("PROVIDER-DISABLED name=$provider")
        publishSystemSnapshot()
    }

    private fun considerLocation(location: Location, publishImmediately: Boolean) {
        val current = bestLocation
        val shouldReplace = current == null || isBetterLocation(location, current)
        diagnostics.log(
            "CANDIDATE provider=${location.provider} accepted=$shouldReplace " +
                "current=${current?.provider ?: "none"}"
        )

        if (shouldReplace) bestLocation = Location(location)
        if (publishImmediately && shouldReplace) {
            publishLocation(location, "GPS fix")
        }
    }

    private fun isBetterLocation(candidate: Location, current: Location): Boolean {
        val timeDelta = candidate.time - current.time
        val significantlyNewer = timeDelta > TWO_MINUTES_MS
        val significantlyOlder = timeDelta < -TWO_MINUTES_MS
        val newer = timeDelta > 0

        if (significantlyNewer) return true
        if (significantlyOlder) return false

        val accuracyDelta = candidate.accuracy - current.accuracy
        val moreAccurate = accuracyDelta < 0
        val significantlyLessAccurate = accuracyDelta > 200
        val sameProvider = candidate.provider == current.provider

        return moreAccurate || (newer && !significantlyLessAccurate) ||
            (newer && sameProvider)
    }

    private fun publishLocation(location: Location, prefix: String) {
        val ageSeconds = locationAgeSeconds(location)
        val status = "$prefix • ${location.provider ?: "unknown"} • ±${location.accuracy.toInt()} m"
        publish(
            LocationUiState.fixed(
                status = status,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = location.accuracy,
                provider = location.provider ?: "unknown",
                ageSeconds = ageSeconds,
                bearing = if (location.hasBearing()) location.bearing else null,
                speedMetersPerSecond = if (location.hasSpeed()) location.speed else null,
                fixTimeMillis = location.time
            )
        )
    }

    private fun publishSystemSnapshot() {
        val gpsEnabled = providerEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = providerEnabled(LocationManager.NETWORK_PROVIDER)
        val passiveEnabled = providerEnabled(LocationManager.PASSIVE_PROVIDER)
        diagnostics.log(
            "SYSTEM gpsEnabled=$gpsEnabled networkEnabled=$networkEnabled " +
                "passiveEnabled=$passiveEnabled locationEnabled=${locationEnabled()}"
        )
    }

    private fun providerEnabled(provider: String): Boolean = try {
        locationManager.isProviderEnabled(provider)
    } catch (_: Throwable) {
        false
    }

    private fun locationEnabled(): Boolean = try {
        if (Build.VERSION.SDK_INT >= 28) locationManager.isLocationEnabled else true
    } catch (_: Throwable) {
        false
    }

    private fun publish(state: LocationUiState) {
        if (lastPublishedStatus != state.status || state.location != null) {
            diagnostics.log("UI state=${state.kind} status=${state.status}")
            lastPublishedStatus = state.status
        }
        onStateChanged(state)
    }

    private fun locationSummary(location: Location): String =
        "provider=${location.provider} lat=${location.latitude} lon=${location.longitude} " +
            "accuracy=${location.accuracy} time=${location.time} " +
            "ageSec=${locationAgeSeconds(location)} speed=${if (location.hasSpeed()) location.speed else "n/a"} " +
            "bearing=${if (location.hasBearing()) location.bearing else "n/a"}"

    private fun locationAgeSeconds(location: Location): Long {
        return if (location.elapsedRealtimeNanos > 0L) {
            ((SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) /
                1_000_000_000L).coerceAtLeast(0L)
        } else {
            ((System.currentTimeMillis() - location.time) / 1000L).coerceAtLeast(0L)
        }
    }

    companion object {
        private const val UPDATE_INTERVAL_MS = 1_000L
        private const val MIN_DISTANCE_METERS = 0f
        private const val TWO_MINUTES_MS = 120_000L
    }
}

data class LocationUiState(
    val kind: Kind,
    val status: String,
    val location: LocationSnapshot? = null
) {
    enum class Kind { PERMISSION_REQUIRED, PERMISSION_DENIED, SEARCHING, DISABLED, FIXED, ERROR }

    companion object {
        fun permissionRequired(status: String) =
            LocationUiState(Kind.PERMISSION_REQUIRED, status)
        fun permissionDenied(status: String) =
            LocationUiState(Kind.PERMISSION_DENIED, status)
        fun searching(status: String) = LocationUiState(Kind.SEARCHING, status)
        fun disabled(status: String) = LocationUiState(Kind.DISABLED, status)
        fun error(status: String) = LocationUiState(Kind.ERROR, status)
        fun fixed(
            status: String,
            latitude: Double,
            longitude: Double,
            accuracyMeters: Float,
            provider: String,
            ageSeconds: Long,
            bearing: Float?,
            speedMetersPerSecond: Float?,
            fixTimeMillis: Long
        ) = LocationUiState(
            Kind.FIXED,
            status,
            LocationSnapshot(
                latitude,
                longitude,
                accuracyMeters,
                provider,
                ageSeconds,
                bearing,
                speedMetersPerSecond,
                fixTimeMillis
            )
        )
    }
}

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val provider: String,
    val ageSeconds: Long,
    val bearing: Float?,
    val speedMetersPerSecond: Float?,
    val fixTimeMillis: Long
)

private class GpsDiagnostics(context: Context) {
    private val directory = File(
        context.getExternalFilesDir(null) ?: context.filesDir,
        "diagnostics"
    ).apply { mkdirs() }
    private val logFile = File(directory, "gps.log")
    private val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(message: String) {
        try {
            rotateIfNeeded()
            FileWriter(logFile, true).use { writer ->
                writer.append(timestamp.format(Date()))
                    .append(" ")
                    .append(message)
                    .append('\n')
            }
        } catch (_: Throwable) {
            // Diagnostics must never break the launcher.
        }
    }

    private fun rotateIfNeeded() {
        if (!logFile.exists() || logFile.length() < MAX_LOG_BYTES) return
        val previous = File(directory, "gps.previous.log")
        if (previous.exists()) previous.delete()
        logFile.renameTo(previous)
    }

    companion object {
        private const val MAX_LOG_BYTES = 512L * 1024L
    }
}
