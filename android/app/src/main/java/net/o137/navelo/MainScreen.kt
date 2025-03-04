@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package net.o137.navelo

import android.animation.ValueAnimator
import android.os.Parcelable
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Bluetooth
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.CornerUpRight
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Locate
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.common.location.LocationError
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.DisposableMapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.LocationConsumer
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.search.autocomplete.PlaceAutocomplete
import com.mapbox.search.autocomplete.PlaceAutocompleteOptions
import com.mapbox.search.autocomplete.PlaceAutocompleteSuggestion
import com.mapbox.search.autocomplete.PlaceAutocompleteType
import com.mapbox.search.common.IsoLanguageCode
import com.mapbox.search.common.NavigationProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import net.o137.navelo.stores.Bookmark
import net.o137.navelo.stores.addBookmark
import net.o137.navelo.stores.bookmarkDataStore
import net.o137.navelo.stores.getBookmarksFlow
import net.o137.navelo.stores.removeBookmark
import net.o137.navelo.utils.FullMapState
import net.o137.navelo.utils.MapboxRouteLine
import net.o137.navelo.utils.ObserveRoutes
import net.o137.navelo.utils.PinAnnotation
import net.o137.navelo.utils.RequestLocationPermission
import net.o137.navelo.utils.format
import net.o137.navelo.utils.formatHourMin
import net.o137.navelo.utils.inset
import net.o137.navelo.utils.meters
import net.o137.navelo.utils.negativePadding
import net.o137.navelo.utils.rememberFullMapState
import net.o137.navelo.utils.rememberLocationPermissionState
import kotlin.time.Duration.Companion.seconds


private const val TAG = "MainScreen"

private val LocalMainScreenGod = compositionLocalOf<MainScreenGod> {
  error("No ScreenGod provided")
}


// TODO: god class
private data class MainScreenGod(
  val routes: MutableState<List<NavigationRoute>?> = mutableStateOf(null),
  val currentPoint: MutableState<Point?> = mutableStateOf(null),
  val selectedPoint: MutableState<Point?> = mutableStateOf(null),
  val mapView: MutableState<MapView?> = mutableStateOf(null),
  val snackbarHostState: SnackbarHostState
)


@Composable
fun MainScreen() {
  val fullMapState = rememberFullMapState()
  val locationPermissionState = rememberLocationPermissionState()

  val snackbarHostState = remember { SnackbarHostState() }
  val searchExpanded = rememberSaveable { mutableStateOf(false) }
  val showBookmarks = rememberSaveable { mutableStateOf(false) }
  val showPairingDialog = rememberSaveable { mutableStateOf(false) }
  val searchQuery = rememberSaveable { mutableStateOf("") }

  val mainScreenGod = remember { MainScreenGod(snackbarHostState = snackbarHostState) }

  HandleSearchReset(searchExpanded, searchQuery)
  RequestLocationPermission(locationPermissionState, askImmediately = false)

  CompositionLocalProvider(LocalMainScreenGod provides mainScreenGod) {
    Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      modifier = Modifier.fillMaxSize(),
      floatingActionButton = {
        if (!fullMapState.isFollowingPuck() || !locationPermissionState.isPermissionReady()) {
          FloatingActionButton(
            shape = CircleShape,
            onClick = {
              if (locationPermissionState.isPermissionReady()) {
                fullMapState.transitionToFollowPuckState()
              } else {
                locationPermissionState.requestLocationPermission()
              }
            },
          ) {
            Icon(Lucide.Locate, "Current location")
          }
        }
      },
      bottomBar = { BottomNavigationBar(showPairingDialog, showBookmarks) },
    ) { innerPadding ->
      MainContent(innerPadding, fullMapState)
      SearchComponent(searchExpanded, searchQuery)
      BookmarksSheet(showBookmarks)
      PairingDialog(showPairingDialog)
    }
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
  showPairingDialog: MutableState<Boolean>,
  showBookmarks: MutableState<Boolean>,
) {
  val scope = rememberCoroutineScope()
  val navController = LocalNavController.current
  val snackbarHostState = LocalMainScreenGod.current.snackbarHostState
  val selectedPoint by LocalMainScreenGod.current.selectedPoint
  val routes by LocalMainScreenGod.current.routes
  var destinationPoint by LocalActivityGod.current.destinationPoint
  var navigationRoutes by LocalActivityGod.current.navigationRoutes

  BottomAppBar(
    actions = {
      val menuExpanded = remember { mutableStateOf(false) }
      IconButton(onClick = { menuExpanded.value = true }) {
        Icon(Lucide.EllipsisVertical, contentDescription = "More actions")
      }
      NavigationMenu(menuExpanded, showPairingDialog)
      IconButton(onClick = { showBookmarks.value = true }) {
        Icon(Lucide.Bookmark, contentDescription = "Show bookmarks")
      }
      Spacer(modifier = Modifier.weight(1f))
      Column(
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        val directionsRoute = routes?.firstOrNull()?.directionsRoute
        val distance = directionsRoute?.distance()?.meters
        val duration = directionsRoute?.duration()?.seconds

        Text(
          text = duration?.formatHourMin() ?: "",
          fontSize = 24.sp,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = distance?.format() ?: "",
          fontSize = 16.sp,
          fontWeight = FontWeight.Normal,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Spacer(modifier = Modifier.weight(1f))
      Spacer(modifier = Modifier.width(32.dp))
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = {
          if (routes?.firstOrNull() == null) {
            // TODO: focus search box
            scope.launch {
              snackbarHostState.showSnackbar("Long press on the map to choose a destination")
            }
          } else {
            destinationPoint = selectedPoint
            navigationRoutes = routes
            navController.navigate(Route.Navigation)
          }
        },
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
  menuExpanded: MutableState<Boolean>,
  showPairingDialog: MutableState<Boolean>
) {
  val navController = LocalNavController.current

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
      leadingIcon = { Icon(Lucide.Settings, contentDescription = "Settings") },
      text = { Text("Settings") }
    )
  }
}

data class SearchResult(
  val text: String,
  val additionalInfo: String,
  val detail: PlaceAutocompleteSuggestion
)

@Composable
private fun SearchComponent(
  expanded: MutableState<Boolean>,
  query: MutableState<String>,
) {
  val scope = rememberCoroutineScope()
  var searchResults by remember { mutableStateOf<List<SearchResult>>(listOf()) }
  val mapView by LocalMainScreenGod.current.mapView
  var selectedPoint by LocalMainScreenGod.current.selectedPoint
  val snackbarHostState = LocalMainScreenGod.current.snackbarHostState

  val placeAutocomplete = remember { PlaceAutocomplete.create() }

  SearchComponentLayout(
    expanded = expanded,
    query = query,
    onQueryChange = { currentQuery ->
      scope.launch {
        placeAutocomplete.suggestions(
          currentQuery,
          proximity = mapView?.mapboxMap?.cameraState?.center,
          options = PlaceAutocompleteOptions(
            language = IsoLanguageCode("ja"), // TODO: JA -> error on placeAutocomplete.select
            navigationProfile = NavigationProfile.CYCLING
          )
        ).onValue { value ->
          searchResults = value.map { result ->
            SearchResult(result.name, result.distanceMeters?.meters?.format() ?: "", result)
          }
        }.onError {
          // TODO: proper handling
        }
      }
    },
    onSelect = { searchResult ->
      scope.launch {
        placeAutocomplete.select(searchResult.detail)
          .onValue { value ->
            selectedPoint = value.coordinate
            Log.d(TAG, "Selected: ${value.coordinate}")
          }
          .onError {
            Log.d(TAG, "Error: $it")
            scope.launch {
              // TODO: proper handling
              snackbarHostState.showSnackbar("Error: $it")
            }
          }
      }
    },
    searchResults = searchResults
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
    LazyColumn {
      items(searchResults) { searchResult ->
        ListItem(
          modifier = Modifier
            .fillMaxWidth()
            .clickable {
              expanded.value = false
              onSelect(searchResult)
            },
          leadingContent = { Icon(Lucide.MapPin, contentDescription = null) },
          headlineContent = { Text(searchResult.text) },
          supportingContent = { Text(searchResult.additionalInfo) },
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
fun BookmarksSheet(showBookmarks: MutableState<Boolean>) {
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var selectedPoint by LocalMainScreenGod.current.selectedPoint
  val snackbarHostState = LocalMainScreenGod.current.snackbarHostState
  val placeAutocomplete = remember { PlaceAutocomplete.create() }

  val bookmarksFlow = getBookmarksFlow(context.bookmarkDataStore)
  val bookmarks by bookmarksFlow.collectAsState(initial = emptyList())

  val showAddDialog = remember { mutableStateOf(false) }
  var newBookmarkPoint by remember { mutableStateOf<Point?>(null) }

  val showDeleteDialog = remember { mutableStateOf(false) }
  var bookmarkToDelete by remember { mutableStateOf<Bookmark?>(null) }

  BookmarkAddDialog(
    showDialog = showAddDialog,
    onConfirm = { bookmarkName ->
      newBookmarkPoint?.let { point ->
        scope.launch {
          // TODO: 直接 API を呼び出して確実に住所を取得する
          val address = placeAutocomplete.getJapanAddress(point)
          val newBookmark = Bookmark(
            text = bookmarkName,
            additionalInfo = address,
            point = point
          )
          addBookmark(context.bookmarkDataStore, newBookmark)
        }
        newBookmarkPoint = null
      }
    },
    onDismiss = {
      newBookmarkPoint = null
    }
  )

  BookmarkDeleteDialog(
    showDialog = showDeleteDialog,
    onConfirm = {
      bookmarkToDelete?.let { bookmark ->
        scope.launch { removeBookmark(context.bookmarkDataStore, bookmark) }
      }
    },
    onDismiss = {
      bookmarkToDelete = null
    }
  )

  BookmarksSheetLayout(
    showBookmarks = showBookmarks,
    bookmarks = bookmarks,
    onSelect = { bookmark ->
      selectedPoint = bookmark.point
    },
    onLongPress = { bookmark ->
      bookmarkToDelete = bookmark
      showDeleteDialog.value = true
    },
    onAddClicked = {
      val point = selectedPoint
      if (point == null) {
        scope.launch { snackbarHostState.showSnackbar("Long press on the map to choose a location") }
      } else {
        newBookmarkPoint = point
        showAddDialog.value = true
      }
    }
  )
}

private suspend fun PlaceAutocomplete.getJapanAddress(point: Point): String {
  val suggestions = this.reverse(
    point, PlaceAutocompleteOptions(
      language = IsoLanguageCode("ja"), // TODO: JA -> error on placeAutocomplete.select
      types = listOf(PlaceAutocompleteType.AdministrativeUnit.Address)
    )
  ).getValueOrElse { listOf() }
  val address = (suggestions.firstOrNull()?.name ?: "Unknown")
  return if (address.startsWith("〒")) {
    address.substring(10)
  } else {
    address
  }
}

@Composable
private fun BookmarksSheetLayout(
  showBookmarks: MutableState<Boolean>,
  bookmarks: List<Bookmark>,
  onSelect: (Bookmark) -> Unit,
  onLongPress: (Bookmark) -> Unit,
  onAddClicked: () -> Unit
) {
  val bookmarksSheetState = rememberModalBottomSheetState()

  // TODO: better way to handle?
  // 一度 expand したあとでそのまま閉じでもう一回開くときに、最大化された状態から縮むアニメーションになることを回避する意図
  LaunchedEffect(showBookmarks.value) {
    if (!showBookmarks.value) {
      bookmarksSheetState.hide()
    }
  }

  if (showBookmarks.value) {
    ModalBottomSheet(
      modifier = Modifier.fillMaxHeight(),
      sheetState = bookmarksSheetState,
      onDismissRequest = { showBookmarks.value = false }
    ) {
      LazyColumn {
        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Bookmarks", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = {
              showBookmarks.value = false
              onAddClicked()
            }) {
              Icon(
                Lucide.Plus,
                contentDescription = "Add",
                modifier = Modifier.size(ButtonDefaults.IconSize)
              )
              Spacer(Modifier.size(ButtonDefaults.IconSpacing))
              Text("Add")
            }
          }
        }
        item {
          Spacer(modifier = Modifier.height(8.dp))
        }
        items(bookmarks) { bookmark ->
          ListItem(
            modifier = Modifier
              .fillMaxWidth()
              .combinedClickable(
                onClick = {
                  showBookmarks.value = false
                  onSelect(bookmark)
                },
                onLongClick = {
                  onLongPress(bookmark)
                }
              ),
            leadingContent = { Icon(Lucide.Bookmark, contentDescription = null) },
            headlineContent = { Text(bookmark.text) },
            supportingContent = { Text(bookmark.additionalInfo) },
          )
        }
      }
    }
  }
}

@Composable
private fun BookmarkAddDialog(
  showDialog: MutableState<Boolean>,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit
) {
  var bookmarkName by remember { mutableStateOf("") }
  LaunchedEffect(showDialog.value) {
    if (!showDialog.value) {
      bookmarkName = ""
    }
  }
  if (showDialog.value) {
    AlertDialog(
      onDismissRequest = {
        showDialog.value = false
        onDismiss()
      },
      title = { Text("Add bookmark") },
      text = {
        Column {
          Text("Enter a name for the new bookmark")
          OutlinedTextField(
            value = bookmarkName,
            onValueChange = { bookmarkName = it },
            label = { Text("Bookmark name") }
          )
        }
      },
      confirmButton = {
        TextButton(onClick = {
          showDialog.value = false
          onConfirm(bookmarkName)
        }) {
          Text("Add")
        }
      },
      dismissButton = {
        TextButton(onClick = {
          showDialog.value = false
          onDismiss()
        }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
private fun BookmarkDeleteDialog(
  showDialog: MutableState<Boolean>,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  if (showDialog.value) {
    AlertDialog(
      onDismissRequest = {
        showDialog.value = false
        onDismiss()
      },
      title = { Text("Remove bookmark") },
      text = { Text("Are you sure you want to remove this bookmark?") },
      confirmButton = {
        TextButton(onClick = {
          showDialog.value = false
          onConfirm()
        }) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = {
          showDialog.value = false
          onDismiss()
        }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
private fun PairingDialog(openDialog: MutableState<Boolean>) {
  PairingDialogLayout(
    showDialog = openDialog,
    getDevices = {
      delay(1000)
      (0..3).random().let { count ->
        (0 until count * 5).map { index ->
          BluetoothDevice("Device $index", "00:11:22:33:44:55")
        }
      }
    },
    pairDevice = { device ->
      delay(2000)
      (0..1).random() == 0
    }
  )
}

@Composable
private fun PairingDialogLayout(
  showDialog: MutableState<Boolean>,
  getDevices: suspend () -> List<BluetoothDevice>,
  pairDevice: suspend (BluetoothDevice) -> Boolean
) {
  val scope = rememberCoroutineScope()
  val pairingState = rememberSaveable { mutableStateOf<PairingState>(PairingState.SCAN) }
  val devices = remember { mutableStateOf<List<BluetoothDevice>?>(null) }

  LaunchedEffect(showDialog.value) {
    if (!showDialog.value) {
      pairingState.value = PairingState.SCAN
    }
  }

  if (showDialog.value) {
    AlertDialog(
      onDismissRequest = { /* Do nothing */ },
      title = {
        when (pairingState.value) {
          is PairingState.COMPLETE -> {
            Text("Connected")
          }

          is PairingState.FAILED -> {
            Text("Failed to connect")
          }

          else -> {
            Text("Connect")
          }
        }
      },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          when (val state = pairingState.value) {
            PairingState.SCAN -> {
              LaunchedEffect(Unit) {
                devices.value = null
                devices.value = getDevices()
              }

              val currentDevices = devices.value

              if (currentDevices == null) {
                Text(text = "Scanning for devices...")
              } else if (currentDevices.isEmpty()) {
                Text(text = "No devices found.")
                Text(text = "Please make sure the device is turned on.")
              } else {
                Text(text = "Select a device to connect.")
              }
              Spacer(modifier = Modifier.height(16.dp))
              if (currentDevices == null) {
                CircularProgressIndicator(
                  modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 16.dp)
                )
              } else if (currentDevices.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.negativePadding((-24).dp)) {
                  items(currentDevices) { device ->
                    ListItem(
                      modifier = Modifier.clickable {
                        pairingState.value = PairingState.PROGRESS(device)
                        scope.launch {
                          val success = pairDevice(device)
                          pairingState.value = if (success) {
                            PairingState.COMPLETE(device)
                          } else {
                            PairingState.FAILED(device)
                          }
                        }
                      },
                      leadingContent = { Icon(Lucide.Bluetooth, "Bluetooth") },
                      headlineContent = { Text(text = device.name) },
                      supportingContent = { Text(text = device.address) }
                    )
                  }
                }
              }
            }

            is PairingState.PROGRESS -> {
              Text(text = "Connecting with ${state.device.name}...")
              Spacer(modifier = Modifier.height(16.dp))
              CircularProgressIndicator(
                modifier = Modifier
                  .align(Alignment.CenterHorizontally)
                  .padding(vertical = 16.dp)
              )
            }

            is PairingState.COMPLETE -> {
              Text(text = "You have successfully connected with ${state.device.name}.")
            }

            is PairingState.FAILED -> {
              Text(text = "Something went wrong while connecting with ${state.device.name}.")
            }
          }
        }
      },
      confirmButton = {
        when (pairingState.value) {
          PairingState.SCAN -> {
            TextButton(
              enabled = devices.value != null,
              onClick = {
                if (devices.value != null) {
                  scope.launch {
                    devices.value = null
                    devices.value = getDevices()
                  }
                }
              }
            ) { Text("Rescan") }
          }

          is PairingState.COMPLETE, is PairingState.FAILED -> {
            TextButton(onClick = { showDialog.value = false }) { Text("OK") }
          }

          else -> {}
        }
      },
      dismissButton = {
        when (pairingState.value) {
          is PairingState.COMPLETE, is PairingState.FAILED -> {}
          else -> {
            TextButton(onClick = { showDialog.value = false }) { Text("Cancel") }
          }
        }
      }
    )
  }
}

@Parcelize
data class BluetoothDevice(val name: String, val address: String) : Parcelable

@Parcelize
sealed class PairingState : Parcelable {
  data object SCAN : PairingState()
  data class PROGRESS(val device: BluetoothDevice) : PairingState()
  data class COMPLETE(val device: BluetoothDevice) : PairingState()
  data class FAILED(val device: BluetoothDevice) : PairingState()
}

@Composable
private fun MainContent(
  innerPadding: PaddingValues,
  fullMapState: FullMapState,
) {
  val mapboxNavigation = LocalActivityGod.current.mapboxNavigation
  var currentRoutes by LocalMainScreenGod.current.routes
  var currentPoint by LocalMainScreenGod.current.currentPoint
  var selectedPoint by LocalMainScreenGod.current.selectedPoint
  var theMapView by LocalMainScreenGod.current.mapView

  LaunchedEffect(selectedPoint) {
    val startPoint = currentPoint
    val endPoint = selectedPoint
    if (startPoint != null && endPoint != null) {
      currentRoutes = null
      mapboxNavigation.requestRoutes(
        RouteOptions.builder().applyDefaultNavigationOptions(DirectionsCriteria.PROFILE_CYCLING)
          //  .alleyBias()
          //  .enableRefresh()
          //  .steps()
          .coordinatesList(listOf(startPoint, endPoint))
          .build(),
        object : NavigationRouterCallback {
          override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
            Log.d(TAG, "Router: onCanceled")
          }

          override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
            Log.d(TAG, "Router: onFailure")
          }

          override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
            currentRoutes = routes
            Log.d(TAG, "Router: onRoutesReady ${routes.size}")
          }
        }
      )
    }
  }

  ObserveRoutes(mapboxNavigation, remember {
    RoutesObserver {
      currentRoutes = it.navigationRoutes
    }
  })

  val mapboxMap = theMapView?.mapboxMap
  if (mapboxMap != null) {
    MapboxRouteLine(mapboxMap, currentRoutes)
  }

  MapboxMap(
    // TODO: correct way to achieve edge-to-edge?
    modifier = Modifier
      .padding(bottom = innerPadding.calculateBottomPadding())
      .consumeWindowInsets(PaddingValues(top = innerPadding.calculateTopPadding())),
    compass = {
      Compass(
        modifier = Modifier
          .padding(16.dp)
          .padding(top = 64.dp + innerPadding.calculateTopPadding()) // add more padding
      )
    },
    scaleBar = { },
    attribution = { Attribution(modifier = Modifier.padding(16.dp)) },
    logo = { Logo(modifier = Modifier.padding(16.dp)) },
    style = { MapStyle(Style.MAPBOX_STREETS) },
    mapViewportState = fullMapState.mapViewportState,
    onMapClickListener = {
      if (selectedPoint == null) {
        false
      } else {
        selectedPoint = null
        currentRoutes = null
        true
      }
    },
    onMapLongClickListener = { point ->
      selectedPoint = point
      true
    }
  ) {
    var isFirstLaunch by rememberSaveable { mutableStateOf(true) }
    val locationConsumer = remember {
      object : LocationConsumer {
        override fun onBearingUpdated(vararg bearing: Double, options: (ValueAnimator.() -> Unit)?) {}

        override fun onError(error: LocationError) {}
        override fun onHorizontalAccuracyRadiusUpdated(vararg radius: Double, options: (ValueAnimator.() -> Unit)?) {}
        override fun onLocationUpdated(vararg location: Point, options: (ValueAnimator.() -> Unit)?) {
          currentPoint = location.lastOrNull() ?: currentPoint
          Log.d(TAG, "new location: $location")
        }

        override fun onPuckAccuracyRadiusAnimatorDefaultOptionsUpdated(options: ValueAnimator.() -> Unit) {}
        override fun onPuckBearingAnimatorDefaultOptionsUpdated(options: ValueAnimator.() -> Unit) {}
        override fun onPuckLocationAnimatorDefaultOptionsUpdated(options: ValueAnimator.() -> Unit) {}
      }
    }

    DisposableMapEffect(Unit) { mapView ->
      Log.d(TAG, "MapEffect")

      mapView.location.updateSettings {
        enabled = true
        locationPuck = createDefault2DPuck(withBearing = true)
        puckBearingEnabled = true
        puckBearing = PuckBearing.HEADING
        showAccuracyRing = true
      }

      // TODO: is this good?
      val locationProvider = mapView.location.getLocationProvider()
      locationProvider?.registerLocationConsumer(locationConsumer)

      // mapView.location.setLocationProvider(activityGod.navigationLocationProvider)
      if (isFirstLaunch) {
        fullMapState.immediatelyFollowPuckState()
        isFirstLaunch = false
      }

      theMapView = mapView
      onDispose {
        Log.d(TAG, "MapEffect: onDispose")
        locationProvider?.unRegisterLocationConsumer(locationConsumer)
        theMapView = null
      }
    }

    selectedPoint?.let {
      PinAnnotation(it)
    }
  }
}
