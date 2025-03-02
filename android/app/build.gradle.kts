apply(from = "$rootDir/secrets.gradle.kts")

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.parcelize)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.oss.licenses.plugin)
}

android {
  namespace = "net.o137.navelo"
  compileSdk = 35

  defaultConfig {
    applicationId = "net.o137.navelo"
    minSdk = 26
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    resValue("string", "mapbox_access_token", extra["MAPBOX_PUBLIC_TOKEN"] as String)
  }

  buildTypes {
    debug {
      isDebuggable = true
    }
    release {
      isMinifyEnabled = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions {
    jvmTarget = "11"
  }
  buildFeatures {
    compose = true
  }
}

dependencies {
  // TODO: remove unused dependencies
  implementation(libs.composables.icons.lucide)
  implementation(libs.compose.preference)
  implementation(libs.mapbox.android)
  implementation(libs.mapbox.compose)
  implementation(libs.mapbox.navigation)
  implementation(libs.mapbox.search.base)
  implementation(libs.mapbox.search.place)
  implementation(libs.mapbox.tripdata)
  implementation(libs.mapbox.ui.components)
  implementation(libs.mapbox.ui.maps)
  implementation(libs.mapbox.voice)

  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.material)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.kotlinx.serialization.json)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.ui.test.junit4)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  debugImplementation(libs.androidx.ui.test.manifest)
  debugImplementation(libs.androidx.ui.tooling)
}
