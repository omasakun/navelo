package net.o137.navelo.utils

import android.annotation.SuppressLint
import android.os.Parcelable
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.mapbox.maps.plugin.viewport.ViewportStatus
import com.mapbox.maps.plugin.viewport.data.DefaultViewportTransitionOptions
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import kotlinx.parcelize.Parcelize
import net.o137.navelo.R

private const val TAG = "Map"


@Composable
fun MapboxRouteLine(mapboxMap: MapboxMap?, routes: List<NavigationRoute>?) {
  val context = LocalContext.current
  val routeLineApi = remember {
    MapboxRouteLineApi(MapboxRouteLineApiOptions.Builder().build())
  }
  val routeLineView = remember {
    MapboxRouteLineView(
      // road-label layer seems to be below the point annotation and location puck
      // https://github.com/rnmapbox/maps/issues/806
      MapboxRouteLineViewOptions.Builder(context)
        .routeLineBelowLayerId("road-label")
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
        mapboxMap?.style?.let { style ->
          routeLineView.renderRouteDrawData(style, data)
        }
      }
    }
    onDispose {
      routeLineApi.clearRouteLine { data ->
        mapboxMap?.style?.let { style ->
          routeLineView.renderClearRouteLineValue(style, data)
        }
      }
    }
  }
}

@Composable
fun PinAnnotation(point: Point) {
  val markerId = R.drawable.ic_red_marker
  val markerImage = rememberIconImage(markerId, painterResource(markerId))

  PointAnnotation(point) {
    this.iconImage = markerImage
    this.iconOffset = listOf(0.0, 115.0 / 500.0 * 52.0)
    this.iconAnchor = IconAnchor.BOTTOM
  }
}


@Composable
fun enhancedLocationProvider(mapboxNavigation: MapboxNavigation): NavigationLocationProvider {
  val navigationLocationProvider = remember { NavigationLocationProvider() }

  val locationObserver = remember {
    object : LocationObserver {
      /** snapped to the route or map-matched to the road */
      override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
        val enhancedLocation = locationMatcherResult.enhancedLocation
        navigationLocationProvider.changePosition(
          enhancedLocation,
          locationMatcherResult.keyPoints,
        )
        Log.i(TAG, "Enhanced location: $enhancedLocation")
      }

      /** raw location updates */
      override fun onNewRawLocation(rawLocation: Location) {
        Log.i(TAG, "Raw location: $rawLocation")
      }
    }
  }

  DisposableEffect(Unit) {
    mapboxNavigation.registerLocationObserver(locationObserver)
    onDispose {
      mapboxNavigation.unregisterLocationObserver(locationObserver)
    }
  }

  return navigationLocationProvider
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
