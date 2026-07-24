package com.ttravlos.lc100launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.text.TextUtils
import android.widget.ImageView
import android.widget.TextView
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var locationState by mutableStateOf(
        LocationUiState.searching("Initializing GPS")
    )

    private lateinit var locationTracker: LocationTracker
    private lateinit var climateTracker: ClimateTracker
    private lateinit var mediaTracker: MediaTracker

    private var climateState by mutableStateOf(ClimateUiState())
    private var mediaState by mutableStateOf(MediaUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationTracker = LocationTracker(this) { newState ->
            runOnUiThread { locationState = newState }
        }
        climateTracker = ClimateTracker(this) { newState ->
            runOnUiThread { climateState = newState }
        }
        mediaTracker = MediaTracker(this) { newState ->
            runOnUiThread { mediaState = newState }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // A HOME activity should remain at the root instead of closing.
                }
            }
        )

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                LauncherScreen(locationState, climateState, mediaState)
            }
        }

        if (!locationTracker.hasLocationPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST
            )
        }
    }

    override fun onStart() {
        super.onStart()
        locationTracker.start()
        climateTracker.start()
        mediaTracker.start()
    }

    override fun onStop() {
        mediaTracker.stop()
        climateTracker.stop()
        locationTracker.stop()
        super.onStop()
    }

    @Deprecated("Deprecated in Android API")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            locationTracker.onPermissionResult()
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
    }
}

private val ScreenBackground = Color(0xFF080B0F)
private val CardBackground = Color(0xFF151A20)
private val ButtonBackground = Color(0xFF202832)
private val BorderColor = Color(0xFF37414D)
private val PrimaryText = Color(0xFFF2F5F7)
private val SecondaryText = Color(0xFF9DA8B3)
private val Accent = Color(0xFF55B7FF)
private val Warning = Color(0xFFFFC857)
private val DaylightSecondaryText = Color(0xFFD8D8D8)
private const val VEHICLE_MARKER_BEARING_OFFSET_DEGREES = -90f

@Composable
private fun LauncherScreen(
    locationState: LocationUiState,
    climateState: ClimateUiState,
    mediaState: MediaUiState
) {
    var showAppDrawer by remember { mutableStateOf(false) }
    BackHandler(enabled = showAppDrawer) {
        showAppDrawer = false
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ScreenBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StatusBar(locationState)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                MapCard(
                    locationState = locationState,
                    modifier = Modifier
                        .weight(1.65f)
                        .fillMaxHeight()
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ClimateCard(
                        state = climateState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    MediaCard(
                        state = mediaState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }

            AppButtonGrid(onOpenApps = { showAppDrawer = true })
        }

        if (showAppDrawer) {
            AppDrawer(onDismiss = { showAppDrawer = false })
        }
    }
}

@Composable
private fun StatusBar(locationState: LocationUiState) {
    var currentTime by remember { mutableStateOf(formattedTime()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = formattedTime()
            delay(30_000)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = currentTime,
            color = PrimaryText,
            fontSize = 27.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "68°F",
            color = PrimaryText,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium
        )

        Text(
            text = "   Wi-Fi   ${gpsStatusLabel(locationState)}",
            color = if (locationState.kind == LocationUiState.Kind.FIXED) Accent else SecondaryText,
            fontSize = 19.sp
        )
    }
}

@Composable
private fun MapCard(
    locationState: LocationUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tileDiagnostics = remember(context) { MapTileDiagnostics(context) }

    val mapView = remember(context) {
        val osmBasePath = File(context.cacheDir, "osmdroid").apply { mkdirs() }
        val osmTileCache = File(osmBasePath, "tiles").apply { mkdirs() }

        Configuration.getInstance().apply {
            load(context, PreferenceManager.getDefaultSharedPreferences(context))
            userAgentValue = "${context.packageName}/0.10 (TEYES CC3 embedded map)"
            setOsmdroidBasePath(osmBasePath)
            setOsmdroidTileCache(osmTileCache)
            setDebugMode(true)
            setDebugMapView(true)
        }

        tileDiagnostics.log("Creating MapView v0.10")
        tileDiagnostics.log(
            "CACHE base=${osmBasePath.absolutePath} " +
                "tiles=${osmTileCache.absolutePath} " +
                "baseWritable=${osmBasePath.canWrite()} " +
                "tilesWritable=${osmTileCache.canWrite()}"
        )
        tileDiagnostics.probe()

        val diagnosticTileSource = object : OnlineTileSourceBase(
            "OpenStreetMap-Diagnostic",
            0,
            19,
            256,
            ".png",
            arrayOf("https://tile.openstreetmap.org/"),
            "© OpenStreetMap contributors",
            TileSourcePolicy(
                2,
                TileSourcePolicy.FLAG_NO_BULK or
                    TileSourcePolicy.FLAG_NO_PREVENTIVE or
                    TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL or
                    TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED
            )
        ) {
            override fun getTileURLString(mapTileIndex: Long): String {
                val url = baseUrl +
                    MapTileIndex.getZoom(mapTileIndex) + "/" +
                    MapTileIndex.getX(mapTileIndex) + "/" +
                    MapTileIndex.getY(mapTileIndex) + imageFilenameEnding()
                tileDiagnostics.log("TILE $url")
                return url
            }
        }

        MapView(context).apply {
            setTileSource(diagnosticTileSource)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            isTilesScaledToDpi = true
            controller.setZoom(4.5)
            controller.setCenter(GeoPoint(39.8283, -98.5795))
        }
    }

    val locationMarker = remember(mapView) {
        Marker(mapView).apply {
            title = "Vehicle location"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            mapView.overlays.add(this)
        }
    }

    var hasCenteredOnGps by remember(mapView) { mutableStateOf(false) }

    LaunchedEffect(locationState.location) {
        val fix = locationState.location ?: return@LaunchedEffect
        val point = GeoPoint(fix.latitude, fix.longitude)
        locationMarker.position = point
        locationMarker.snippet =
            "${fix.provider}, ±${fix.accuracyMeters.toInt()} m"
        fix.bearing?.let { bearing ->
            locationMarker.rotation =
                (bearing + VEHICLE_MARKER_BEARING_OFFSET_DEGREES + 360f) % 360f
        }

        if (!hasCenteredOnGps) {
            mapView.controller.setZoom(16.0)
            mapView.controller.animateTo(point)
            hasCenteredOnGps = true
        } else {
            mapView.controller.animateTo(point)
        }
        mapView.invalidate()
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    DashboardCard(modifier = modifier.clip(RoundedCornerShape(14.dp))) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { mapView },
                update = { it.invalidate() }
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .background(
                        color = Color(0xCC080B0F),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = locationState.status,
                    color = if (locationState.kind == LocationUiState.Kind.FIXED) Accent else Warning,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                locationState.location?.let { fix ->
                    Text(
                        text = String.format(
                            Locale.US,
                            "%.5f, %.5f  •  age %ds",
                            fix.latitude,
                            fix.longitude,
                            fix.ageSeconds
                        ),
                        color = PrimaryText,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ClimateCard(state: ClimateUiState, modifier: Modifier = Modifier) {
    DashboardCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("CLIMATE", color = DaylightSecondaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(state.status, color = if (state.status == "Live") Accent else Warning, fontSize = 13.sp)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (state.temperature in listOf("LO", "HI", "—")) state.temperature else "${state.temperature}°",
                    color = PrimaryText,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Light
                )
                Column(modifier = Modifier.padding(start = 14.dp, bottom = 3.dp)) {
                    Text(if (state.auto) "AUTO" else "MANUAL", color = if (state.auto) Accent else DaylightSecondaryText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Fan ${state.fanSpeed ?: "—"}", color = DaylightSecondaryText, fontSize = 15.sp)
                }
            }
            val features = buildList {
                add(if (state.ac) "A/C on" else "A/C off")
                if (state.recirc) add("Recirc")
                if (state.defrost) add("Defrost") else add(state.ventLabel)
            }
            Text(features.joinToString("  •  "), color = DaylightSecondaryText, fontSize = 13.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MediaCard(state: MediaUiState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    DashboardCard(
        modifier = modifier.clickable {
            if (state.permissionRequired) {
                context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                AppLauncher.openMusic(context)
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text("NOW PLAYING", color = DaylightSecondaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            when {
                state.permissionRequired -> {
                    Text("Enable media access", color = PrimaryText, fontSize = 21.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Tap here, then enable LC100 Launcher", color = Warning, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                state.title != null -> {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp),
                        factory = { viewContext ->
                            TextView(viewContext).apply {
                                setTextColor(android.graphics.Color.rgb(242, 245, 247))
                                textSize = 19f
                                setSingleLine(true)
                                ellipsize = TextUtils.TruncateAt.MARQUEE
                                marqueeRepeatLimit = -1
                                isSelected = true
                                isFocusable = true
                                isFocusableInTouchMode = true
                                includeFontPadding = false
                            }
                        },
                        update = { titleView ->
                            titleView.text = state.title
                            titleView.isSelected = true
                        }
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(state.artist ?: state.appName.orEmpty(), color = DaylightSecondaryText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(if (state.playing) "Playing • ${state.appName.orEmpty()}" else "Paused • ${state.appName.orEmpty()}", color = if (state.playing) Accent else DaylightSecondaryText, fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
                else -> {
                    Text("No media playing", color = PrimaryText, fontSize = 21.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Tap to open music", color = DaylightSecondaryText, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun AppButtonGrid(onOpenApps: () -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LauncherButton("Navigation", Modifier.weight(1f)) { AppLauncher.openNavigation(context) }
            LauncherButton("Music", Modifier.weight(1f)) { AppLauncher.openMusic(context) }
            LauncherButton("Climate", Modifier.weight(1f)) { AppLauncher.openClimate(context) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LauncherButton("Phone", Modifier.weight(1f)) { AppLauncher.openPhone(context) }
            LauncherButton("Home Assistant", Modifier.weight(1f)) { AppLauncher.openHomeAssistant(context) }
            LauncherButton("Apps", Modifier.weight(1f), onClick = onOpenApps)
        }
    }
}

private data class LaunchableApp(val label: String, val packageName: String, val activityName: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppDrawer(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val apps = remember {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { LaunchableApp(it.loadLabel(context.packageManager).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            .distinctBy { "${it.packageName}/${it.activityName}" }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xF2080B0F)).clickable(onClick = onDismiss)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp).clickable(enabled = false) {},
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("APPS", color = PrimaryText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = ButtonBackground)) { Text("Close") }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 130.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(apps) { app ->
                    Column(
                        modifier = Modifier.height(112.dp).background(ButtonBackground, RoundedCornerShape(12.dp)).clickable {
                            val launch = Intent().setClassName(app.packageName, app.activityName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(launch) }
                            onDismiss()
                        }.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AndroidView(
                            modifier = Modifier.size(48.dp),
                            factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE } },
                            update = { view ->
                                val drawable = runCatching { context.packageManager.getActivityIcon(Intent().setClassName(app.packageName, app.activityName)) }.getOrNull()
                                view.setImageDrawable(drawable)
                            }
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(app.label, color = PrimaryText, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun LauncherButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(74.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ButtonBackground,
            contentColor = PrimaryText
        )
    ) {
        Text(text = label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DashboardCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(color = CardBackground, shape = RoundedCornerShape(14.dp))
            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(14.dp))
    ) {
        content()
    }
}

private fun gpsStatusLabel(state: LocationUiState): String = when (state.kind) {
    LocationUiState.Kind.FIXED -> "GPS FIX"
    LocationUiState.Kind.SEARCHING -> "GPS…"
    LocationUiState.Kind.PERMISSION_REQUIRED,
    LocationUiState.Kind.PERMISSION_DENIED -> "GPS PERM"
    LocationUiState.Kind.DISABLED -> "GPS OFF"
    LocationUiState.Kind.ERROR -> "GPS ERR"
}

private fun formattedTime(): String =
    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
