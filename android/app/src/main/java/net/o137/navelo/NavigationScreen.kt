@file:OptIn(ExperimentalMaterial3Api::class)

package net.o137.navelo

import android.annotation.SuppressLint
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.CircleStop
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Locate
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Route
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.DisposableMapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.trip.session.NavigationSessionState
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.TripSessionState
import net.o137.navelo.utils.FullMapState
import net.o137.navelo.utils.ListenIndicatorPositionChange
import net.o137.navelo.utils.LocationPermissionState
import net.o137.navelo.utils.MapboxRouteArrow
import net.o137.navelo.utils.MapboxRouteLine
import net.o137.navelo.utils.ObserveRouteProgress
import net.o137.navelo.utils.ObserveRoutes
import net.o137.navelo.utils.RequestLocationPermission
import net.o137.navelo.utils.format
import net.o137.navelo.utils.formatHourMin
import net.o137.navelo.utils.meters
import net.o137.navelo.utils.rememberFullMapState
import net.o137.navelo.utils.rememberLocationPermissionState
import net.o137.navelo.utils.useEnhancedLocationProvider
import net.o137.navelo.utils.useRouteProgress
import kotlin.time.Duration.Companion.seconds


private const val TAG = "NavigationScreen"

private val LocalNavigationScreenGod = compositionLocalOf<NavigationScreenGod> {
  error("No ScreenGod provided")
}


// TODO: god class
private class NavigationScreenGod(
  val mapView: MutableState<MapView?> = mutableStateOf(null),
  val snackbarHostState: SnackbarHostState,
  val locationPermissionState: LocationPermissionState
)

@Composable
fun NavigationScreen() {
  val navController = LocalNavController.current
  val mapboxNavigation = LocalActivityGod.current.mapboxNavigation

  val fullMapState = rememberFullMapState()
  val locationPermissionState = rememberLocationPermissionState()

  val snackbarHostState = remember { SnackbarHostState() }
  var isPaused by remember { mutableStateOf(false) }
  var showExitDialog by remember { mutableStateOf(false) }
  var showMenu by remember { mutableStateOf(false) }
  val showRouteSheet = rememberSaveable { mutableStateOf(false) }

  val navigationScreenGod =
    remember {
      NavigationScreenGod(
        snackbarHostState = snackbarHostState,
        locationPermissionState = locationPermissionState
      )
    }

  RequestLocationPermission(locationPermissionState, askImmediately = true)

  if (showExitDialog) {
    AlertDialog(
      onDismissRequest = { showExitDialog = false },
      title = { Text("End the ride") },
      text = { Text("Are you sure you want to end the navigation?") },
      confirmButton = {
        TextButton(onClick = {
          showExitDialog = false
          navController.popBackStack(Route.Main, inclusive = false)
          mapboxNavigation.stopTripSession() // TODO: proper place to put this?
          Log.d(TAG, "stopTripSession")
        }) {
          Text("Yes")
        }
      },
      dismissButton = {
        TextButton(onClick = { showExitDialog = false }) {
          Text("No")
        }
      }
    )
  }

  BackHandler {
    showExitDialog = true
  }

  CompositionLocalProvider(LocalNavigationScreenGod provides navigationScreenGod) {
    Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      floatingActionButton = {
        if (!fullMapState.isFollowingPuck() || !locationPermissionState.isPermissionReady()) {
          FloatingActionButton(
            shape = CircleShape,
            onClick = {
              if (locationPermissionState.isPermissionReady()) {
                fullMapState.transitionToFollowPuckState()
              } else {
                locationPermissionState.requestLocationPermission()
              }
            },
          ) {
            Icon(Lucide.Locate, "Current location")
          }
        }
      },
      bottomBar = {
        BottomAppBar(
          actions = {
            IconButton(onClick = { showMenu = !showMenu }) {
              Icon(Lucide.EllipsisVertical, contentDescription = "More actions")
            }
            DropdownMenu(
              offset = DpOffset(0.dp, (-30).dp),
              expanded = showMenu,
              onDismissRequest = { showMenu = false }
            ) {
              DropdownMenuItem(
                onClick = {
                  showMenu = false
                  showRouteSheet.value = true
                },
                leadingIcon = { Icon(Lucide.Route, contentDescription = "Route") },
                text = { Text("Show route") }
              )
              DropdownMenuItem(
                onClick = {
                  showMenu = false
                  navController.navigate(Route.Settings)
                },
                leadingIcon = {
                  Icon(Lucide.Settings, contentDescription = "Settings")
                },
                text = { Text("Settings") }
              )
            }
            IconButton(onClick = { /* ... */ }) {
              Icon(Lucide.Search, contentDescription = "Search")
            }
            // TODO: better way to horizontally center the items?
            Spacer(modifier = Modifier.weight(1f))
            if (isPaused) Spacer(modifier = Modifier.width(24.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              val routeProgress = useRouteProgress(mapboxNavigation)
              val distance = routeProgress?.distanceRemaining?.meters
              val duration = routeProgress?.durationRemaining?.seconds

              Text(
                text = distance?.format() ?: "",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = duration?.formatHourMin() ?: "",
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (isPaused) {
              IconButton(onClick = { showExitDialog = true }) {
                Icon(Lucide.CircleStop, contentDescription = "End ride")
              }
              Spacer(modifier = Modifier.width(8.dp))
            } else {
              Spacer(modifier = Modifier.width(32.dp))
            }
          },
          floatingActionButton = {
            FloatingActionButton(
              onClick = { isPaused = !isPaused },
              containerColor = BottomAppBarDefaults.bottomAppBarFabColor,
              elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
            ) {
              if (isPaused) {
                Icon(Lucide.Play, contentDescription = "Resume")
              } else {
                Icon(Lucide.Pause, contentDescription = "Pause")
              }
            }
          },
        )
      },
    ) { innerPadding ->
      MainContent(innerPadding, fullMapState)
      RouteSheet(showRouteSheet)
    }
  }
}

@Composable
private fun RouteSheet(showRouteSheet: MutableState<Boolean>) {
  val sheetState = rememberModalBottomSheetState()

// TODO: better way to handle?
// 一度 expand したあとでそのまま閉じでもう一回開くときに、最大化された状態から縮むアニメーションになることを回避する意図
  LaunchedEffect(showRouteSheet.value) {
    if (!showRouteSheet.value) {
      sheetState.hide()
    }
  }

  if (showRouteSheet.value) {
    ModalBottomSheet(
      modifier = Modifier.fillMaxHeight(),
      sheetState = sheetState,
      onDismissRequest = { showRouteSheet.value = false },
    ) {
      Column {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text("Route", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Route details")
      }
    }
  }
}

@Composable
private fun AutoStartTrip() {
  val context = LocalContext.current
  val mapboxNavigation = LocalActivityGod.current.mapboxNavigation
  val navigationRoutes by LocalActivityGod.current.navigationRoutes
  val locationPermissionState = LocalNavigationScreenGod.current.locationPermissionState
  val isPermissionGranted = locationPermissionState.isGranted == true

  LaunchedEffect(isPermissionGranted) {
    val isStarted = mapboxNavigation.getTripSessionState() == TripSessionState.STARTED
    val routes = navigationRoutes
    @SuppressLint("MissingPermission")
    if (isPermissionGranted && !isStarted && routes != null) {
      Log.d(TAG, "startTripSession")
      mapboxNavigation.setNavigationRoutes(routes)
      mapboxNavigation.startTripSession(false)
      context.startNaveloService()
    }
  }
}

@Composable
private fun MainContent(
  innerPadding: PaddingValues,
  fullMapState: FullMapState,
) {
  val mapboxNavigation = LocalActivityGod.current.mapboxNavigation
  var navigationRoutes by LocalActivityGod.current.navigationRoutes
  val enhancedLocationProvider = useEnhancedLocationProvider(mapboxNavigation)
  var theMapView by LocalNavigationScreenGod.current.mapView

  AutoStartTrip()

  ObserveRoutes(mapboxNavigation, remember {
    RoutesObserver {
      if (mapboxNavigation.getNavigationSessionState() != NavigationSessionState.Idle) {
        navigationRoutes = it.navigationRoutes
      }
    }
  })

  val mapboxMap = theMapView?.mapboxMap
  if (mapboxMap != null) {
    MapboxRouteArrow(mapboxMap, mapboxNavigation)
    MapboxRouteLine(
      mapboxMap,
      navigationRoutes,
      apiOptions = {
        vanishingRouteLineEnabled(true)
        styleInactiveRouteLegsIndependently(true)
      },
      viewOptions = {
        displayRestrictedRoadSections(true)
      }
    ) { api, view ->
      ObserveRouteProgress(mapboxNavigation, remember {
        RouteProgressObserver {
          api.updateWithRouteProgress(it) { data ->
            theMapView?.mapboxMap?.style?.let { style ->
              view.renderRouteLineUpdate(style, data)
            }
          }
        }
      })
      ListenIndicatorPositionChange(theMapView?.location, remember {
        OnIndicatorPositionChangedListener {
          val data = api.updateTraveledRouteLine(it)
          theMapView?.mapboxMap?.style?.let { style ->
            view.renderRouteLineUpdate(style, data)
          }
        }
      })
    }
  }

  MapboxMap(
    // TODO: correct way to achieve edge-to-edge?
    modifier = Modifier.padding(innerPadding),
    compass = { Compass(modifier = Modifier.padding(16.dp)) },
    scaleBar = { },
    attribution = { Attribution(modifier = Modifier.padding(16.dp)) },
    logo = { Logo(modifier = Modifier.padding(16.dp)) },
    style = { MapStyle(Style.MAPBOX_STREETS) },
    mapViewportState = fullMapState.mapViewportState,
  ) {
    var isFirstLaunch by remember { mutableStateOf(true) }

    DisposableMapEffect(Unit) { mapView ->
      Log.d(TAG, "MapEffect")

      mapView.location.updateSettings {
        enabled = true
        locationPuck = createDefault2DPuck(withBearing = true)
        puckBearingEnabled = true
        puckBearing = PuckBearing.HEADING
        showAccuracyRing = true
      }

      mapView.location.setLocationProvider(enhancedLocationProvider)

      if (isFirstLaunch) {
        fullMapState.immediatelyFollowPuckState()
        isFirstLaunch = false
      }

      theMapView = mapView
      onDispose {
        Log.d(TAG, "MapEffect: onDispose")
        theMapView = null
      }
    }
  }
}
