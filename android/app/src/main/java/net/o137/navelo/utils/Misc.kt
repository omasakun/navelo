package net.o137.navelo.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.offset
import com.mapbox.geojson.Point
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.text.DecimalFormat
import kotlin.time.Duration

fun trimEmptyLines(text: String): String {
  val lines = text.lines()
  val start = lines.indexOfFirst { it.isNotBlank() }
  val end = lines.indexOfLast { it.isNotBlank() }
  return lines.subList(start, end + 1).joinToString("\n")
}

object PointSerializer : KSerializer<Point> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Point", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: Point) {
    encoder.encodeString(value.toJson())
  }

  override fun deserialize(decoder: Decoder): Point {
    return Point.fromJson(decoder.decodeString())
  }
}

fun Duration.formatHourMin(): String {
  val totalMinutes = this.inWholeMinutes
  val hours = totalMinutes / 60
  val minutes = totalMinutes % 60
  return when {
    hours > 0 && minutes > 0 -> "$hours h $minutes min"
    hours > 0 -> "$hours h"
    else -> "$minutes min"
  }
}

data class Distance(val meters: Double)

val Double.meters: Distance get() = Distance(this)

fun Distance.format(): String {
  return if (this.meters >= 1000) {
    DecimalFormat("#.##").format(this.meters / 1000) + " km"
  } else {
    DecimalFormat("#").format(this.meters) + " m"
  }
}

fun Rect.inset(dx: Float, dy: Float): Rect {
  return Rect(left + dx, top + dy, right - dx, bottom - dy)
}

// TODO: better solution
fun Modifier.negativePadding(horizontal: Dp): Modifier {
  return this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.offset((-horizontal * 2).roundToPx()))
    layout(
      width = placeable.width + (horizontal * 2).roundToPx(),
      height = placeable.height
    ) { placeable.place(horizontal.roundToPx(), 0) }
  }
}
