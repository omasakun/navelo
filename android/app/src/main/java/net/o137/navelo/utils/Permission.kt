package net.o137.navelo.utils

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

@Composable
public fun RequestLocationPermission(
    requestCount: Int = 0,
    onPermissionDenied: () -> Unit,
    onPermissionReady: () -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissionsMap ->
        if (permissionsMap.values.all { it }) {
            onPermissionReady()
        } else {
            onPermissionDenied()
        }
    }
    LaunchedEffect(requestCount) {
        if (locationPermissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            onPermissionReady()
        } else {
            launcher.launch(locationPermissions)
        }
    }
}

private val locationPermissions = arrayOf(
    android.Manifest.permission.ACCESS_FINE_LOCATION,
    android.Manifest.permission.ACCESS_COARSE_LOCATION
)
