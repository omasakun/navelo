@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import net.o137.navelo.ui.theme.NaveloTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // TODO: WindowInsets とかについて理解してから、想定されてる方法で edge to edge を実現する
    // enableEdgeToEdge()
    setContent {
      App()
    }
  }
}

@Composable
private fun App() {
  NaveloTheme {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
    ) {
      val navController = rememberNavController()
      NavHost(
        navController = navController,
        startDestination = Route.Main,
        enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
      ) {
        composable<Route.Main> { MainScreen(navController) }
        composable<Route.Navigation> { NavigationScreen(navController) }
        composable<Route.Settings> { SettingsScreen(navController) }
        composable<Route.License> { LicenseScreen(navController) }
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
