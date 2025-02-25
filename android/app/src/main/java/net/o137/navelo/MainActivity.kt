package net.o137.navelo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.viewport.ViewportStatus
import com.mapbox.maps.plugin.viewport.data.DefaultViewportTransitionOptions
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateOptions
import kotlinx.coroutines.launch
import net.o137.navelo.ui.theme.NaveloTheme
import net.o137.navelo.utils.RequestLocationPermission

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            var permissionRequestCount by remember {
                mutableStateOf(1)
            }
            var showRequestPermissionButton by remember {
                mutableStateOf(false)
            }

            val mapViewportState = rememberMapViewportState {
                setCameraOptions {
                    center(Point.fromLngLat(139.7916227, 35.713481))
                    zoom(ZOOM)
                    pitch(PITCH)
                }
            }

            val followPuckOptions = remember {
                FollowPuckViewportStateOptions.Builder().pitch(0.0).build()
            }
            val transitionOptions = remember {
                DefaultViewportTransitionOptions.Builder().maxDurationMs(1000).build()
            }

            NaveloTheme {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    floatingActionButton = {
                        if (mapViewportState.mapViewportStatus == ViewportStatus.Idle) {
                            FloatingActionButton(
                                onClick = {
                                    mapViewportState.transitionToFollowPuckState(
                                        followPuckOptions,
                                        transitionOptions
                                    )
                                }
                            ) {
                                Image(
                                    painter = painterResource(id = android.R.drawable.ic_menu_mylocation),
                                    contentDescription = "Locate button"
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    RequestLocationPermission(
                        requestCount = permissionRequestCount,
                        onPermissionDenied = {
                            scope.launch {
                                snackbarHostState.showSnackbar("You need to accept location permissions.")
                            }
                            showRequestPermissionButton = true
                        },
                        onPermissionReady = {
                            showRequestPermissionButton = false
                        }
                    )
                    MapboxMap(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        compass = { Compass(modifier = Modifier.padding(16.dp)) },
                        scaleBar = { ScaleBar(modifier = Modifier.padding(16.dp)) },
                        attribution = { Attribution(modifier = Modifier.padding(16.dp)) },
                        logo = { Logo(modifier = Modifier.padding(16.dp)) },
                        style = {
                            MapStyle(Style.MAPBOX_STREETS)
                        },
                        mapViewportState = mapViewportState
                    ) {
                        MapEffect(Unit) { mapView ->
                            mapView.location.updateSettings {
                                enabled = true
                                locationPuck = createDefault2DPuck(withBearing = true)
                                puckBearingEnabled = true
                                puckBearing = PuckBearing.HEADING
                                showAccuracyRing = true
                            }
                            mapViewportState.transitionToFollowPuckState(
                                followPuckOptions,
                                DefaultViewportTransitionOptions.Builder().maxDurationMs(0).build()
                            )
                        }
                    }
                    if (showRequestPermissionButton) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.align(Alignment.Center)) {
                                Button(
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                    onClick = {
                                        permissionRequestCount += 1
                                    }
                                ) {
                                    Text("Request permission again ($permissionRequestCount)")
                                }
                                Button(
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                    onClick = {
                                        context.startActivity(
                                            Intent(
                                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.fromParts("package", packageName, null)
                                            )
                                        )
                                    }
                                ) {
                                    Text("Show App Settings page")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val ZOOM: Double = 9.0
        const val PITCH: Double = 0.0
    }
}
