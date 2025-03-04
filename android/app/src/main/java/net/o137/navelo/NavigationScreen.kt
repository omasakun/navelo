@file:OptIn(ExperimentalMaterial3Api::class)

package net.o137.navelo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun NavigationScreen() {
  val navController = LocalNavController.current

  var isPaused by remember { mutableStateOf(false) }
  var showExitDialog by remember { mutableStateOf(false) }
  var showMenu by remember { mutableStateOf(false) }

  if (showExitDialog) {
    AlertDialog(
      onDismissRequest = { showExitDialog = false },
      title = { Text("End the ride") },
      text = { Text("Are you sure you want to end the navigation?") },
      confirmButton = {
        TextButton(onClick = {
          showExitDialog = false
          navController.popBackStack()
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

  val showRouteSheet = rememberSaveable { mutableStateOf(false) }

  Scaffold(
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
              leadingIcon = { Icon(Lucide.Settings, contentDescription = "Settings") },
              text = { Text("Settings") }
            )
          }
          IconButton(onClick = { /* ... */ }) {
            Icon(Lucide.Search, contentDescription = "Search")
          }
          // TODO: better way to horizontally center the items?
          Spacer(modifier = Modifier.weight(1f))
          if (isPaused) Spacer(modifier = Modifier.width(24.dp))
          Column(
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = "15:00",
              fontSize = 24.sp,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "5.0 km",
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
    floatingActionButton = {
      FloatingActionButton(
        shape = CircleShape,
        onClick = { /* do something */ },
      ) {
        Icon(Lucide.Locate, "Current location")
      }
    },
  ) { innerPadding ->
    Text(modifier = Modifier.padding(innerPadding), text = "Navigation screen")

    RouteSheet(showRouteSheet)
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


