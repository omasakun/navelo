package net.o137.navelo.utils

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Parcelable
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import kotlinx.parcelize.Parcelize

private const val TAG = "LocationPermission"

private val locationPermissions = arrayOf(
  android.Manifest.permission.ACCESS_FINE_LOCATION,
  android.Manifest.permission.ACCESS_COARSE_LOCATION
)

@Composable
fun rememberLocationPermissionState(): LocationPermissionState {
  return rememberSaveable(saver = LocationPermissionState.Saver) {
    LocationPermissionState()
  }
}

class LocationPermissionState {
  internal var count by mutableIntStateOf(0)
  var isGranted by mutableStateOf<Boolean?>(null)
    internal set

  fun requestLocationPermission() {
    count++
    Log.d(TAG, "RequestLocationPermission $count")
  }

  // TODO: duplicated accessor?
  fun isPermissionReady(): Boolean {
    return isGranted == true
  }

  // TODO: is it really necessary to define custom saver?
  companion object {
    val Saver: Saver<LocationPermissionState, SavedState> = Saver(
      save = {
        SavedState(it.count, it.isGranted)
      },
      restore = {
        LocationPermissionState().apply {
          count = it.count
          isGranted = it.isGranted
        }
      }
    )
  }

  @Parcelize
  data class SavedState(val count: Int, val isGranted: Boolean?) : Parcelable
}

@Composable
fun RequestLocationPermission(state: LocationPermissionState, askImmediately: Boolean) {
  val context = LocalContext.current

  val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
  ) { permissionsMap ->
    state.isGranted = permissionsMap.values.all { it }
  }

  val showAlertDialog = remember { mutableStateOf(false) }

  LaunchedEffect(state.count) {
    val isGranted = locationPermissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    state.isGranted = isGranted

    if (!isGranted && (askImmediately || state.count > 0)) {
      val permanentlyDenied = locationPermissions.any {
        !ActivityCompat.shouldShowRequestPermissionRationale(context as Activity, it)
      }
      if (permanentlyDenied) {
        showAlertDialog.value = true
      } else {
        launcher.launch(locationPermissions)
      }
    }
  }

  PermissionRequestDialog(showAlertDialog)
}

@Composable
private fun PermissionRequestDialog(showDialog: MutableState<Boolean>) {
  val context = LocalContext.current
  if (showDialog.value) {
    AlertDialog(
      onDismissRequest = { showDialog.value = false },
      title = { Text("Permission Required") },
      text = { Text("This app needs access to location information, but the permission has been permanently denied. Please change the settings.") },
      confirmButton = {
        TextButton(onClick = {
          context.startActivity(
            Intent(
              android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
              Uri.fromParts("package", context.packageName, null)
            )
          )
          showDialog.value = false
        }) {
          Text("Go to Settings")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDialog.value = false }) {
          Text("Cancel")
        }
      }
    )
  }
}
