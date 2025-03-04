package net.o137.navelo.stores

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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.o137.navelo.utils.PointSerializer

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
