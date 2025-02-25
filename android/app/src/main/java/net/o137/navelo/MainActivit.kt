@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package net.o137.navelo

import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.CornerUpRight
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings2
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
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
    }
  }
}

private sealed class Route {
  @Serializable
  object Main

  @Serializable
  object Settings
}

@Composable
private fun SettingsScreen(navController: NavController) {
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

@Composable
private fun MainScreen(navController: NavController) {
  val snackbarHostState = remember { SnackbarHostState() }
  val searchExpanded = rememberSaveable { mutableStateOf(false) }
  val showBookmarks = rememberSaveable { mutableStateOf(false) }
  val showPairingDialog = rememberSaveable { mutableStateOf(false) }
  val bookmarksSheetState = rememberModalBottomSheetState()
  val searchQuery = rememberSaveable { mutableStateOf("") }

  HandleSearchReset(searchExpanded, searchQuery)

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    modifier = Modifier.fillMaxSize(),
    bottomBar = { BottomNavigationBar(navController, showPairingDialog, showBookmarks) },
  ) {
    MainContent(Modifier.padding(it))
    SearchComponent(searchExpanded, searchQuery, snackbarHostState)
    BookmarksSheet(showBookmarks, bookmarksSheetState)
    PairingDialog(showPairingDialog)
  }
}

@Composable
private fun HandleSearchReset(
  searchExpanded: MutableState<Boolean>,
  searchQuery: MutableState<String>
) {
  // TODO: better way to handle?
  LaunchedEffect(searchExpanded.value) {
    if (!searchExpanded.value) {
      searchQuery.value = ""
    }
  }
}

@Composable
private fun BottomNavigationBar(
  navController: NavController,
  showPairingDialog: MutableState<Boolean>,
  showBookmarks: MutableState<Boolean>
) {
  BottomAppBar(
    actions = {
      val menuExpanded = remember { mutableStateOf(false) }
      IconButton(onClick = { menuExpanded.value = true }) {
        Icon(Lucide.EllipsisVertical, contentDescription = "More options")
      }
      NavigationMenu(navController, menuExpanded, showPairingDialog)
      IconButton(onClick = { showBookmarks.value = true }) {
        Icon(Lucide.Bookmark, contentDescription = "Show bookmarks")
      }
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { /* do something */ },
        containerColor = BottomAppBarDefaults.bottomAppBarFabColor,
        elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation()
      ) {
        Icon(Lucide.CornerUpRight, "Start navigation")
      }
    }
  )
}

@Composable
private fun NavigationMenu(
  navController: NavController,
  menuExpanded: MutableState<Boolean>,
  showPairingDialog: MutableState<Boolean>
) {
  DropdownMenu(
    offset = DpOffset(0.dp, (-30).dp),
    expanded = menuExpanded.value,
    onDismissRequest = { menuExpanded.value = false }
  ) {
    DropdownMenuItem(
      onClick = {
        showPairingDialog.value = true
        menuExpanded.value = false
      },
      leadingIcon = { Icon(Lucide.Link, contentDescription = "Connect to device") },
      text = { Text("Connect") }
    )
    DropdownMenuItem(
      onClick = {
        navController.navigate(Route.Settings)
        menuExpanded.value = false
      },
      leadingIcon = { Icon(Lucide.Settings2, contentDescription = "Settings") },
      text = { Text("Settings") }
    )
  }
}

@Parcelize
data class SearchResult(
  val text: String,
  val additionalInfo: String
) : Parcelable

@Composable
private fun SearchComponent(
  expanded: MutableState<Boolean>,
  query: MutableState<String>,
  snackbarHostState: SnackbarHostState
) {
  val scope = rememberCoroutineScope()
  val searchResults = rememberSaveable {
    mutableStateOf(
      listOf(
        SearchResult("Search result 1", "Additional info"),
        SearchResult("Search result 2", "Additional info"),
        SearchResult("Search result 3", "Additional info"),
      )
    )
  }
  SearchComponentLayout(
    expanded = expanded,
    query = query,
    onQueryChange = { currentQuery ->
      Log.d("SearchComponent", "Query changed: $currentQuery")

      searchResults.value = listOf(
        SearchResult("Search result $currentQuery", "Additional info"),
        SearchResult("Search result 1", "Additional info"),
        SearchResult("Search result 2", "Additional info"),
        SearchResult("Search result 3", "Additional info"),
      )
    },
    onSelect = {
      scope.launch {
        snackbarHostState.showSnackbar("Selected: ${it.text}")
      }
    },
    searchResults = searchResults.value
  )
}

@Composable
private fun SearchComponentLayout(
  expanded: MutableState<Boolean>,
  query: MutableState<String>,
  onQueryChange: (String) -> Unit,
  onSelect: (SearchResult) -> Unit,
  searchResults: List<SearchResult>
) {
  val horizontalMargin = 16.dp
  SearchBar(
    expanded = expanded.value,
    onExpandedChange = { expanded.value = it },
    shape = searchBarShape(expanded.value, horizontalMargin),
    inputField = {
      SearchBarDefaults.InputField(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = if (expanded.value) 0.dp else horizontalMargin),
        query = query.value,
        onQueryChange = {
          query.value = it
          onQueryChange(it)
        },
        onSearch = {
          expanded.value = false
          val firstSuggestion = searchResults.firstOrNull()
          if (firstSuggestion != null) {
            onSelect(firstSuggestion)
          }
        },
        expanded = expanded.value,
        onExpandedChange = { expanded.value = it },
        leadingIcon = { SearchBarIcon(expanded) },
        placeholder = { Text("Search") },
      )
    },
  ) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
      searchResults.forEach { suggestion ->
        ListItem(
          modifier = Modifier
            .fillMaxWidth()
            .clickable {
              expanded.value = false
              onSelect(suggestion)
            },
          leadingContent = { Icon(Lucide.MapPin, contentDescription = null) },
          headlineContent = { Text(suggestion.text) },
          supportingContent = { Text(suggestion.additionalInfo) },
        )
      }
    }
  }
}

@Composable
private fun searchBarShape(searchExpanded: Boolean, inputHorizontalMargin: Dp): Shape {
  val density = LocalDensity.current
  val inputFieldShape = remember {
    GenericShape { size, _ ->
      with(density) {
        val rect = size.toRect().inset(inputHorizontalMargin.toPx(), 0.0F)
        val cornerRadius = CornerRadius((SearchBarDefaults.InputFieldHeight / 2).toPx())
        addRoundRect(RoundRect(rect, cornerRadius))
      }
    }
  }
  return if (searchExpanded) {
    SearchBarDefaults.fullScreenShape
  } else {
    inputFieldShape
  }
}

@Composable
private fun SearchBarIcon(expanded: MutableState<Boolean>) {
  if (expanded.value) {
    IconButton(
      onClick = { expanded.value = false },
      content = { Icon(Lucide.ArrowLeft, contentDescription = "Back") }
    )
  } else {
    Icon(Lucide.Search, contentDescription = null)
  }
}

@Composable
private fun BookmarksSheet(
  showBookmarks: MutableState<Boolean>,
  bookmarksSheetState: SheetState
) {
  if (showBookmarks.value) {
    ModalBottomSheet(
      modifier = Modifier.fillMaxHeight(),
      sheetState = bookmarksSheetState,
      onDismissRequest = { showBookmarks.value = false }
    ) {
      Text(
        "Swipe up to open sheet. Swipe down to dismiss.",
        modifier = Modifier.padding(16.dp)
      )
    }
  }
}

@Composable
private fun PairingDialog(openDialog: MutableState<Boolean>) {
  if (openDialog.value) {
    BasicAlertDialog(
      onDismissRequest = {}
      // onDismissRequest = { openDialog.value = false }
    ) {
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .wrapContentHeight(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = AlertDialogDefaults.TonalElevation
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(text = "Pairing")
          Spacer(modifier = Modifier.height(24.dp))
          Row(modifier = Modifier.align(Alignment.End)) {
            TextButton(onClick = { openDialog.value = false }) { Text("Cancel") }
            TextButton(onClick = { openDialog.value = false }) { Text("Confirm") }
          }
        }
      }
    }
  }
}

@Composable
private fun MainContent(modifier: Modifier = Modifier) {
  Text(
    text = "Hello, Android!",
    modifier = modifier
  )
}

private fun Rect.inset(dx: Float, dy: Float): Rect {
  return Rect(left + dx, top + dy, right - dx, bottom - dy)
}
