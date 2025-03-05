package net.o137.navelo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import kotlinx.serialization.Serializable
import net.o137.navelo.ui.theme.NaveloTheme


private const val TAG = "MainActivity"


val LocalNavController = compositionLocalOf<NavController> {
  error("No NavController provided")
}

val LocalActivityGod = compositionLocalOf<ActivityGod> {
  error("No ActivityGod provided")
}

// TODO: god class
class ActivityGod(lifecycleOwner: LifecycleOwner) {
  val mapboxNavigation by lifecycleOwner.requireMapboxNavigation()
  val navigationRoutes = mutableStateOf<List<NavigationRoute>?>(null)
}


class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // TODO: WindowInsets とかについて理解してから、想定されてる方法で edge to edge を実現する
    // enableEdgeToEdge()

    val activityGod = ActivityGod(this)

    setContent {
      App(activityGod)
    }
  }
}

@Composable
private fun App(activityGod: ActivityGod) {
  NaveloTheme {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
    ) {
      val navController = rememberNavController()
      CompositionLocalProvider(LocalNavController provides navController, LocalActivityGod provides activityGod) {
        NavHost(
          navController = navController,
          startDestination = Route.Main,
          enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
          exitTransition = { fadeOut() },
          popEnterTransition = { fadeIn() },
          popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
        ) {
          composable<Route.Main> { MainScreen() }
          composable<Route.Navigation> { NavigationScreen() }
          composable<Route.Settings> { SettingsScreen() }
          composable<Route.License> { LicenseScreen() }
        }
      }
    }
  }
}

sealed class Route {
  @Serializable
  object Main

  @Serializable
  object Navigation

  @Serializable
  object Settings

  @Serializable
  object License
}
