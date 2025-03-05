package net.o137.navelo

import android.app.Application
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp

class MainApplication : Application() {
  override fun onCreate() {
    super.onCreate()
      
    MapboxNavigationApp.setup {
      NavigationOptions.Builder(this).build()
    }
  }
}
