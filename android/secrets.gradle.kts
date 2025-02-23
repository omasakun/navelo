import java.io.File
import java.util.Properties

val secretsFile = File(rootDir, "secrets.properties")
val secrets = Properties().apply {
    if (secretsFile.exists()) {
        secretsFile.inputStream().use { load(it) }
    } else {
        error("secrets.properties not found!")
    }
}

// https://docs.gradle.org/current/userguide/writing_build_scripts.html#sec:extra_properties
extra["MAPBOX_PRIVATE_TOKEN"] = secrets.getProperty("MAPBOX_PRIVATE_TOKEN") ?: error("MAPBOX_PRIVATE_TOKEN is missing")
extra["MAPBOX_PUBLIC_TOKEN"] = secrets.getProperty("MAPBOX_PUBLIC_TOKEN") ?: error("MAPBOX_PUBLIC_TOKEN is missing")
