package net.o137.navelo

import android.content.Context
import android.os.Parcelable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mapbox.geojson.Point
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

val Context.bookmarkDataStore by preferencesDataStore(name = "bookmarks_store")

private val BOOKMARKS_KEY = stringPreferencesKey("bookmarks")

@Serializable
@Parcelize
data class Bookmark(
  val text: String,
  val additionalInfo: String,
  @Serializable(with = PointSerializer::class)
  val point: Point
) : Parcelable

object PointSerializer : KSerializer<Point> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Point", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: Point) {
    encoder.encodeString(value.toJson())
  }

  override fun deserialize(decoder: Decoder): Point {
    return Point.fromJson(decoder.decodeString())
  }
}

private fun readBookmarks(preferences: Preferences): List<Bookmark> {
  return preferences[BOOKMARKS_KEY]?.let { json ->
    try {
      Json.decodeFromString<List<Bookmark>>(json)
    } catch (e: Exception) {
      emptyList()
    }
  } ?: emptyList()
}

fun getBookmarksFlow(dataStore: DataStore<Preferences>): Flow<List<Bookmark>> =
  dataStore.data.map { readBookmarks(it) }

suspend fun addBookmark(dataStore: DataStore<Preferences>, bookmark: Bookmark) {
  dataStore.edit { preferences ->
    val currentList = readBookmarks(preferences)
    val updatedList = currentList + bookmark
    preferences[BOOKMARKS_KEY] = Json.encodeToString(updatedList)
  }
}

suspend fun removeBookmark(dataStore: DataStore<Preferences>, bookmark: Bookmark) {
  dataStore.edit { preferences ->
    val currentList = readBookmarks(preferences)
    val updatedList = currentList.filterNot { it == bookmark }
    preferences[BOOKMARKS_KEY] = Json.encodeToString(updatedList)
  }
}
