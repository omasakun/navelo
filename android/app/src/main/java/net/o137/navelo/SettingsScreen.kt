@file:OptIn(ExperimentalMaterial3Api::class)

package net.o137.navelo

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide

@Composable
fun SettingsScreen(navController: NavController) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = "Settings") },
        navigationIcon = {
          IconButton(onClick = { navController.navigateUp() }) {
            Icon(Lucide.ArrowLeft, "Back")
          }
        }
      )
    }
  ) {
    Text(
      modifier = Modifier.padding(it),
      text = "Settings screen"
    )
  }
}
