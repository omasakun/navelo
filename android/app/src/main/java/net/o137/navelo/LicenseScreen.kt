@file:OptIn(ExperimentalMaterial3Api::class)

package net.o137.navelo


import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

@Composable
fun LicenseScreen() {
  val navController = LocalNavController.current

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = "License notice") },
        navigationIcon = {
          IconButton(onClick = { navController.navigateUp() }) {
            Icon(Lucide.ArrowLeft, "Back")
          }
        },
      )
    }
  ) { innerPadding ->
    val context = LocalContext.current
    var licensesState by remember { mutableStateOf<List<LicenseEntry>?>(null) }
    var selectedLicense by remember { mutableStateOf<LicenseEntry?>(null) }

    LaunchedEffect(Unit) {
      licensesState = loadLicenses(context)
    }

    when (val licenses = licensesState) {
      null -> {
        Row(
          modifier = Modifier.fillMaxSize(),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          CircularProgressIndicator()
        }
      }

      else -> {
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
          items(licenses) { license ->
            val terms = license.terms.lines().first().trim()

            ListItem(
              modifier = Modifier.clickable {
                selectedLicense = license
              },
              headlineContent = { Text(text = license.name) },
              supportingContent = {
                Text(
                  text = terms,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            )
          }
        }
      }
    }

    val currentLicense = selectedLicense
    if (currentLicense != null) {
      LicenseBottomSheet(
        license = currentLicense,
        onDismiss = { selectedLicense = null }
      )
    }
  }
}

@Composable
fun LicenseBottomSheet(license: LicenseEntry, onDismiss: () -> Unit) {
  ModalBottomSheet(
    modifier = Modifier.fillMaxHeight(),
    onDismissRequest = onDismiss
  ) {
    Column(
      modifier = Modifier
        .fillMaxHeight()
        .verticalScroll(rememberScrollState())
        .padding(vertical = 16.dp)
    ) {
      Text(
        text = license.name, style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp)
      )
      Spacer(modifier = Modifier.height(16.dp))
      Box(
        modifier = Modifier
          .weight(1f)
          .horizontalScroll(rememberScrollState())
      ) {
        Text(
          text = license.terms,
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.padding(horizontal = 16.dp)
        )
      }
    }
  }
}

private suspend fun loadLicenses(context: Context): List<LicenseEntry> {
  return withContext(Dispatchers.IO) {
    val data = context.resources.openRawResource(R.raw.third_party_licenses).readBytes()
    val metadataResource = context.resources.openRawResource(R.raw.third_party_license_metadata)
    val metadata = metadataResource.bufferedReader().use { it.readLines() }
    metadata.map { line ->
      val (section, name) = line.split(" ", limit = 2)
      val (start, length) = section.split(":").map(String::toInt)
      val licenseData = data.sliceArray(start until start + length)
      val licenseText = licenseData.toString(Charset.forName("UTF-8"))
      LicenseEntry(name, trimEmptyLines(licenseText))
    }.sortedBy { it.name }
  }
}

private fun trimEmptyLines(text: String): String {
  val lines = text.lines()
  val start = lines.indexOfFirst { it.isNotBlank() }
  val end = lines.indexOfLast { it.isNotBlank() }
  return lines.subList(start, end + 1).joinToString("\n")
}

data class LicenseEntry(
  val name: String,
  val terms: String
)
