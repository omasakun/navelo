@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package net.o137.navelo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import net.o137.navelo.ui.theme.NaveloTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      App()
    }
  }
}

@Composable
private fun App() {
  NaveloTheme {
    val navController = rememberNavController()
    NavHost(navController, startDestination = Route.Main,
      enterTransition = { slideInHorizontally(initialOffsetX = { it }) },
      exitTransition = { ExitTransition.None },
      popEnterTransition = { EnterTransition.None },
      popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
    ) {
      composable<Route.Main> { MainScreen(navController) }
      composable<Route.Settings> { SettingsScreen(navController) }
      composable<Route.Navigation> { NavigationScreen(navController) }
    }
  }
}

sealed class Route {
  @Serializable
  object Main

  @Serializable
  object Settings

  @Serializable
  object Navigation
}
