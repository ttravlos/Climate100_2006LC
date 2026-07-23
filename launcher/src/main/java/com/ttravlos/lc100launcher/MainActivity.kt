package com.ttravlos.lc100launcher

import android.os.Bundle
import android.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // A HOME activity should remain at the root instead of closing.
                }
            }
        )

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme()
            ) {
                LauncherScreen()
            }
        }
    }
}

private val ScreenBackground = Color(0xFF080B0F)
private val CardBackground = Color(0xFF151A20)
private val ButtonBackground = Color(0xFF202832)
private val BorderColor = Color(0xFF37414D)
private val PrimaryText = Color(0xFFF2F5F7)
private val SecondaryText = Color(0xFF9DA8B3)
private val Accent = Color(0xFF55B7FF)

@Composable
private fun LauncherScreen() {
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
            StatusBar()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                MapCard(
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    MediaCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }

            AppButtonGrid()
        }
    }
}

@Composable
private fun StatusBar() {
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
            text = "   Wi-Fi   GPS",
            color = SecondaryText,
            fontSize = 19.sp
        )
    }
}

@Composable
private fun MapCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tileDiagnostics = remember(context) { MapTileDiagnostics(context) }

    // Keep one stable MapView instance for the lifetime of this composition.
    // Storing it in mutable Compose state caused DisposableEffect to dispose
    // and detach the newly-created view as soon as the state key changed.
    val mapView = remember(context) {
        val osmBasePath = File(
            context.cacheDir,
            "osmdroid"
        ).apply { mkdirs() }

        val osmTileCache = File(
            osmBasePath,
            "tiles"
        ).apply { mkdirs() }

        Configuration.getInstance().apply {
            load(
                context,
                PreferenceManager.getDefaultSharedPreferences(context)
            )
            userAgentValue =
                "${context.packageName}/0.9 (TEYES CC3 embedded map)"
            setOsmdroidBasePath(osmBasePath)
            setOsmdroidTileCache(osmTileCache)
            setDebugMode(true)
            setDebugMapView(true)
        }

        tileDiagnostics.log("Creating MapView")
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
            controller.setCenter(
                GeoPoint(39.8283, -98.5795)
            )
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()

        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    DashboardCard(
        modifier = modifier.clip(RoundedCornerShape(14.dp))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
            update = { it.invalidate() }
        )
    }
}

@Composable
private fun ClimateCard(modifier: Modifier = Modifier) {
    DashboardCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(22.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "CLIMATE",
                color = SecondaryText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "72°",
                    color = PrimaryText,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Light
                )

                Column(
                    modifier = Modifier.padding(start = 20.dp, bottom = 7.dp)
                ) {
                    Text(
                        text = "AUTO",
                        color = Accent,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Fan 3",
                        color = SecondaryText,
                        fontSize = 19.sp
                    )
                }
            }

            Text(
                text = "A/C on  •  Face + feet",
                color = SecondaryText,
                fontSize = 17.sp
            )
        }
    }
}

@Composable
private fun MediaCard(modifier: Modifier = Modifier) {
    DashboardCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(22.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "NOW PLAYING",
                color = SecondaryText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "No media playing",
                color = PrimaryText,
                fontSize = 27.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Open a music app to begin",
                color = SecondaryText,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
private fun AppButtonGrid() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LauncherButton("Navigation", Modifier.weight(1f)) {
                AppLauncher.openNavigation(context)
            }
            LauncherButton("Music", Modifier.weight(1f)) {
                AppLauncher.openMusic(context)
            }
            LauncherButton("Climate", Modifier.weight(1f)) {
                AppLauncher.openClimate(context)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LauncherButton("Phone", Modifier.weight(1f)) {
                AppLauncher.openPhone(context)
            }
            LauncherButton("Home Assistant", Modifier.weight(1f)) {
                AppLauncher.openHomeAssistant(context)
            }
            LauncherButton("Apps", Modifier.weight(1f)) {
                AppLauncher.openApps(context)
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
        Text(
            text = label,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DashboardCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                color = CardBackground,
                shape = RoundedCornerShape(14.dp)
            )
            .border(
                width = 1.dp,
                color = BorderColor,
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        content()
    }
}

private fun formattedTime(): String {
    return LocalTime.now().format(
        DateTimeFormatter.ofPattern("h:mm a")
    )
}