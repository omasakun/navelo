@file:OptIn(ExperimentalMaterial3Api::class)

package net.o137.navelo

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.listPreference
import me.zhanghai.compose.preference.multiSelectListPreference
import me.zhanghai.compose.preference.preferenceCategory
import me.zhanghai.compose.preference.switchPreference
import me.zhanghai.compose.preference.textFieldPreference

@Composable
fun SettingsScreen() {
  val navController = LocalNavController.current

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = "Settings") },
        navigationIcon = {
          IconButton(onClick = { navController.navigateUp() }) {
            Icon(Lucide.ArrowLeft, "Back")
          }
        },
        actions = {
          val context = LocalContext.current
          var showMenu by remember { mutableStateOf(false) }
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
                navController.navigate(Route.License)
              },
              text = { Text("License notice") }
            )
            DropdownMenuItem(
              onClick = {
                showMenu = false
                // TODO: Update the URL
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://navelo.net"))
                context.startActivity(intent)
              },
              text = { Text("Website") }
            )
          }
        }
      )
    }
  ) { innerPadding ->
    ProvidePreferenceLocals {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
      ) {
        preferenceCategory(
          key = "experiments",
          title = { Text(text = "Experiments") }
        )
        switchPreference(
          key = "switch_preference",
          defaultValue = false,
          title = { Text(text = "Switch preference") },
          icon = { Icon(imageVector = Lucide.Info, contentDescription = null) },
          summary = { Text(text = if (it) "On" else "Off") }
        )
        listPreference(
          key = "list_alert_dialog_preference",
          defaultValue = "Alpha",
          values = listOf("Alpha", "Beta", "Canary"),
          title = { Text(text = "List preference (alert dialog)") },
          summary = { Text(text = it) },
        )
        multiSelectListPreference(
          key = "multi_select_list_preference",
          defaultValue = setOf("Alpha", "Beta"),
          values = listOf("Alpha", "Beta", "Canary"),
          title = { Text(text = "Multi-select list preference") },
          summary = { Text(text = it.sorted().joinToString(", ")) },
        )
        textFieldPreference(
          key = "text_field_preference",
          defaultValue = "Value",
          title = { Text(text = "Text field preference") },
          textToValue = { it },
          summary = { Text(text = it) },
        )
      }
    }
  }
}
