package net.o137.navelo.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.o137.navelo.R
import java.nio.charset.Charset

data class LicenseEntry(
  val name: String,
  val terms: String
)

suspend fun loadLicenses(context: Context): List<LicenseEntry> {
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
