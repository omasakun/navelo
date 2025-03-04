package net.o137.navelo.utils

import android.annotation.SuppressLint
import android.os.Parcelable
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.mapbox.common.location.Location
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraState
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.MapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.rememberIconImage
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin
import com.mapbox.maps.plugin.locationcomponent.LocationConsumer
import com.mapbox.maps.plugin.locationcomponent.LocationProvider
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.viewport.ViewportStatus
import com.mapbox.maps.plugin.viewport.data.DefaultViewportTransitionOptions
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateOptions
import com.mapbox.navigation.base.formatter.DistanceFormatter
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.arrival.ArrivalObserver
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.OffRouteObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.tripdata.maneuver.model.ManeuverOptions
import com.mapbox.navigation.tripdata.shield.api.MapboxRouteShieldApi
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.RouteLayerConstants
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowApi
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import kotlinx.parcelize.Parcelize
import net.o137.navelo.R
import java.util.concurrent.CopyOnWriteArraySet

private const val TAG = "Map"


@Composable
fun MapboxRouteLine(
  mapboxMap: MapboxMap,
  routes: List<NavigationRoute>?,
  apiOptions: MapboxRouteLineApiOptions.Builder.() -> Unit = {},
  viewOptions: MapboxRouteLineViewOptions.Builder.() -> Unit = {},
  content: @Composable (api: MapboxRouteLineApi, view: MapboxRouteLineView) -> Unit = { _, _ -> },
) {
  val context = LocalContext.current
  val routeLineApi = remember {
    MapboxRouteLineApi(MapboxRouteLineApiOptions.Builder().apply(apiOptions).build())
  }
  val routeLineView = remember {
    MapboxRouteLineView(
      // road-label layer seems to be below the point annotation and location puck
      // https://github.com/rnmapbox/maps/issues/806
      MapboxRouteLineViewOptions.Builder(context)
        .routeLineBelowLayerId("road-label")
        .apply(viewOptions)
        .build()
    )
  }

  // TODO: is this correct?
  DisposableEffect(Unit) {
    onDispose {
      routeLineApi.cancel()
      routeLineView.cancel()
    }
  }

  DisposableEffect(mapboxMap, routes) {
    if (routes != null) {
      routeLineApi.setNavigationRoutes(routes) { data ->
        mapboxMap.style?.let { style ->
          routeLineView.renderRouteDrawData(style, data)
        }
      }
    }
    onDispose {
      routeLineApi.clearRouteLine { data ->
        mapboxMap.style?.let { style ->
          routeLineView.renderClearRouteLineValue(style, data)
        }
      }
    }
  }

  content(routeLineApi, routeLineView)
}

@Composable
fun MapboxRouteArrow(
  mapboxMap: MapboxMap,
  mapboxNavigation: MapboxNavigation,
  arrowOptions: RouteArrowOptions.Builder.() -> Unit = {},
) {
  val context = LocalContext.current
  val routeArrowApi = remember { MapboxRouteArrowApi() }
  val routeArrowView = remember {
    MapboxRouteArrowView(
      RouteArrowOptions.Builder(context)
        .withAboveLayerId(RouteLayerConstants.TOP_LEVEL_ROUTE_LINE_LAYER_ID)
        .apply(arrowOptions)
        .build()
    )
  }

  ObserveRouteProgress(mapboxNavigation, remember {
    RouteProgressObserver {
      val updatedManeuverArrow = routeArrowApi.addUpcomingManeuverArrow(it)
      Log.d(TAG, "RouteProgressObserver: $updatedManeuverArrow")
      mapboxMap.style?.let { style ->
        routeArrowView.renderManeuverUpdate(style, updatedManeuverArrow)
      }
    }
  })
}


@Composable
fun PinAnnotation(point: Point) {
  val markerId = R.drawable.ic_red_marker
  val markerImage = rememberIconImage(markerId, painterResource(markerId))

  PointAnnotation(point) {
    iconImage = markerImage
    iconOffset = listOf(0.0, 115.0 / 500.0 * 52.0)
    iconAnchor = IconAnchor.BOTTOM
  }
}

class WrappedLocationProvider(private val providers: List<LocationProvider>) : LocationProvider {
  val locationConsumers = CopyOnWriteArraySet<LocationConsumer>()

  override fun registerLocationConsumer(locationConsumer: LocationConsumer) {
    locationConsumers.add(locationConsumer)
    providers.forEach { it.registerLocationConsumer(locationConsumer) }
  }

  override fun unRegisterLocationConsumer(locationConsumer: LocationConsumer) {
    locationConsumers.remove(locationConsumer)
    providers.forEach { it.unRegisterLocationConsumer(locationConsumer) }
  }
}

@Composable
fun useEnhancedLocationProvider(mapboxNavigation: MapboxNavigation): LocationProvider {
  val context = LocalContext.current
  val navigationLocationProvider = remember { NavigationLocationProvider() }
  val customLocationProvider = remember { WrappedLocationProvider(listOf(navigationLocationProvider)) }

  val locationObserver = remember {
    object : LocationObserver {
      /** snapped to the route or map-matched to the road */
      override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
        val enhancedLocation = locationMatcherResult.enhancedLocation
        val keyPoints = locationMatcherResult.keyPoints

        // Ignore bearing derived from the route
        enhancedLocation.bearing = null
        keyPoints.forEach { it.bearing = null }

        navigationLocationProvider.changePosition(
          enhancedLocation,
          keyPoints,
        )
//        Log.i(TAG, "Enhanced location: $enhancedLocation")
      }

      /** raw location updates */
      override fun onNewRawLocation(rawLocation: Location) {
//        Log.i(TAG, "Raw location: $rawLocation")
      }
    }
  }

  DisposableEffect(Unit) {
    mapboxNavigation.registerLocationObserver(locationObserver)
    onDispose {
      mapboxNavigation.unregisterLocationObserver(locationObserver)
    }
  }

  // TODO: better way to use compass bearing + snapped location

  val locationCompassEngine = remember { LocationCompassEngine(context) }
  val compassListener = remember {
    LocationCompassEngine.CompassListener { userHeading ->
      customLocationProvider.locationConsumers.forEach {
        it.onBearingUpdated(userHeading.toDouble())
      }
    }
  }

  DisposableEffect(Unit) {
    locationCompassEngine.addCompassListener(compassListener)
    onDispose {
      locationCompassEngine.removeCompassListener(compassListener)
    }
  }

  return customLocationProvider
}

@Composable
fun useRouteProgress(mapboxNavigation: MapboxNavigation): RouteProgress? {
  var routeProgress by remember { mutableStateOf<RouteProgress?>(null) }
  ObserveRouteProgress(mapboxNavigation, remember { RouteProgressObserver { routeProgress = it } })
  return routeProgress
}

@Composable
fun useManeuverApi(
  formatter: DistanceFormatter,
  maneuverOptions: ManeuverOptions.Builder.() -> Unit = {},
  routeShieldApi: MapboxRouteShieldApi = MapboxRouteShieldApi()
): MapboxManeuverApi {
  val maneuverApi = remember {
    MapboxManeuverApi(
      formatter,
      ManeuverOptions.Builder().apply(maneuverOptions).build(),
      routeShieldApi
    )
  }

  DisposableEffect(Unit) {
    onDispose {
      maneuverApi.cancel()
    }
  }

  return maneuverApi
}

/** Don't forget to wrap the observer with remember */
@Composable
fun ObserveRoutes(mapboxNavigation: MapboxNavigation, observer: RoutesObserver) {
  DisposableEffect(Unit) {
    Log.d(TAG, "registerRoutesObserver")
    mapboxNavigation.registerRoutesObserver(observer)
    onDispose {
      Log.d(TAG, "unregisterRoutesObserver")
      mapboxNavigation.unregisterRoutesObserver(observer)
    }
  }
}

/** Don't forget to wrap the observer with remember */
@Composable
fun ObserveRouteProgress(mapboxNavigation: MapboxNavigation, observer: RouteProgressObserver) {
  DisposableEffect(Unit) {
    Log.d(TAG, "registerRouteProgressObserver")
    mapboxNavigation.registerRouteProgressObserver(observer)
    onDispose {
      Log.d(TAG, "unregisterRouteProgressObserver")
      mapboxNavigation.unregisterRouteProgressObserver(observer)
    }
  }
}

/** Don't forget to wrap the observer with remember */
@Composable
fun ObserveOffRoute(mapboxNavigation: MapboxNavigation, observer: OffRouteObserver) {
  DisposableEffect(Unit) {
    Log.d(TAG, "registerOffRouteObserver")
    mapboxNavigation.registerOffRouteObserver(observer)
    onDispose {
      Log.d(TAG, "unregisterOffRouteObserver")
      mapboxNavigation.unregisterOffRouteObserver(observer)
    }
  }
}

/** Don't forget to wrap the observer with remember */
@Composable
fun ObserveArrival(mapboxNavigation: MapboxNavigation, observer: ArrivalObserver) {
  DisposableEffect(Unit) {
    Log.d(TAG, "registerArrivalObserver")
    mapboxNavigation.registerArrivalObserver(observer)
    onDispose {
      Log.d(TAG, "unregisterArrivalObserver")
      mapboxNavigation.unregisterArrivalObserver(observer)
    }
  }
}

/** Don't forget to wrap the listener with remember */
@Composable
fun ListenIndicatorPositionChange(location: LocationComponentPlugin?, listener: OnIndicatorPositionChangedListener) {
  DisposableEffect(Unit) {
    Log.d(TAG, "addOnIndicatorPositionChangedListener")
    location?.addOnIndicatorPositionChangedListener(listener)
    onDispose {
      Log.d(TAG, "removeOnIndicatorPositionChangedListener")
      location?.removeOnIndicatorPositionChangedListener(listener)
    }
  }
}


@Composable
fun rememberFullMapState(): FullMapState {
  return rememberSaveable(saver = FullMapState.Saver) {
    FullMapState()
  }
}

// TODO: better name
@SuppressLint("AutoboxingStateCreation")
class FullMapState {
  var mapViewportState = MapViewportState().apply {
    setCameraOptions {
      center(Point.fromLngLat(139.692912, 35.688985)) // Tokyo
      zoom(9.0)
      pitch(0.0)
    }
  }

  private val followPuckOptions = FollowPuckViewportStateOptions.Builder().pitch(0.0).build()
  private val transitionOptions = DefaultViewportTransitionOptions.Builder().maxDurationMs(1000).build()

  fun transitionToFollowPuckState() {
    mapViewportState.transitionToFollowPuckState(followPuckOptions, transitionOptions)
  }

  fun immediatelyFollowPuckState() {
    mapViewportState.transitionToFollowPuckState(
      followPuckOptions,
      DefaultViewportTransitionOptions.Builder().maxDurationMs(0).build()
    )
  }

  fun isFollowingPuck(): Boolean {
    return mapViewportState.mapViewportStatus != ViewportStatus.Idle
  }

  companion object {
    val Saver: Saver<FullMapState, SavedState> = Saver(
      save = {
        SavedState(
          it.mapViewportState.cameraState,
        )
      },
      restore = {
        FullMapState().apply {
          if (it.cameraState != null) mapViewportState = MapViewportState(it.cameraState)
        }
      }
    )
  }

  @Parcelize
  data class SavedState(
    val cameraState: CameraState?,
  ) : Parcelable
}
