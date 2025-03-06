package net.o137.navelo

import android.app.Application
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import kotlinx.coroutines.flow.MutableStateFlow

// TODO: god object
object ApplicationGod {
  val naveloDevice = MutableStateFlow<NaveloDevice?>(null)
}

class MainApplication : Application() {
  override fun onCreate() {
    super.onCreate()

    MapboxNavigationApp.setup {
      NavigationOptions.Builder(this).build()
    }
  }
}
