@file:OptIn(ExperimentalUuidApi::class, ExperimentalApi::class, ExperimentalUuidApi::class, ObsoleteKableApi::class)

package net.o137.navelo

import android.bluetooth.le.ScanSettings
import android.util.Log
import com.juul.kable.ExperimentalApi
import com.juul.kable.ObsoleteKableApi
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.WriteType.WithResponse
import com.juul.kable.characteristic
import com.juul.kable.characteristicOf
import com.juul.kable.service
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val TAG = "NaveloDevice"

private object Uuids {
  private fun naveloUuid(name: String) = Uuid.parse("0137$name-7b58-4dda-af7b-4b87d25b4296")
  val movementService = naveloUuid("9a00")
  val movementDataCharacteristic = naveloUuid("9a01")
  val movementConfigCharacteristic = naveloUuid("9a02")
  val movementPeriodCharacteristic = naveloUuid("9a03")
  val batteryService = Uuid.service("battery_service")
  val batteryCharacteristic = Uuid.characteristic("battery_level")
}

private object Characteristics {
  val movementData = characteristicOf(
    service = Uuids.movementService,
    characteristic = Uuids.movementDataCharacteristic,
  )
  val movementConfig = characteristicOf(
    service = Uuids.movementService,
    characteristic = Uuids.movementConfigCharacteristic,
  )
  val movementPeriod = characteristicOf(
    service = Uuids.movementService,
    characteristic = Uuids.movementPeriodCharacteristic,
  )
  val battery = characteristicOf(
    service = Uuids.batteryService,
    characteristic = Uuids.batteryCharacteristic,
  )
}

data class Vector3f(val x: Float, val y: Float, val z: Float)

fun Vector3f(data: ByteArray) = Vector3f(
  x = data.readU16(0).toFloat(),
  y = data.readU16(2).toFloat(),
  z = data.readU16(4).toFloat(),
)

class NaveloDevice(private val peripheral: Peripheral) {
  companion object {
    private val RssiInterval = 5.seconds
    val PeriodRange = 100.milliseconds..2550.milliseconds

    val scanner by lazy {
      Scanner {
        scanSettings = ScanSettings.Builder()
          .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
          .build()
        filters {
          match {
            services = listOf(Uuids.movementService)
          }
        }
      }
    }
  }

  suspend fun connect() {
    Log.i(TAG, "Connecting to ${peripheral.name}")
    try {
      peripheral.connect().launch { monitorRssi() }
      readBatteryLevel()
      readGyroPeriod()
      enableGyro()
      Log.i(TAG, "Connected")
    } catch (e: IOException) {
      Log.e(TAG, "Connection attempt failed", e)
      peripheral.disconnect()
      throw e
    }
  }

  suspend fun disconnect() {
    peripheral.disconnect()
  }

  private suspend fun monitorRssi() {
    while (coroutineContext.isActive) {
      readRssi()
      Log.d(TAG, "RSSI: ${_rssi.value}")
      delay(RssiInterval)
    }
  }

  // === Peripheral States === //

  private val _battery = MutableStateFlow<Int?>(null)
  private val _rssi = MutableStateFlow<Int?>(null)
  private val _period = MutableStateFlow<Duration?>(null)

  val state = peripheral.state

  /** Battery percent level (0-100). */
  val battery = merge(
    _battery.filterNotNull(),
    peripheral.observe(Characteristics.battery).map { it.readU8(0) },
  )

  val rssi = _rssi.asStateFlow()

  val period = _period.filterNotNull()

  val gyro: Flow<Vector3f> = peripheral
    .observe(Characteristics.movementData)
    .map(::Vector3f)

  // === Peripheral Accessors === //

  suspend fun writeGyroPeriod(period: Duration) {
    require(period in PeriodRange) { "Period must be in the range $PeriodRange, was $period." }
    val value = period.inWholeMilliseconds / 10
    val data = byteArrayOf(value.toByte())
    peripheral.write(Characteristics.movementPeriod, data, WithResponse)
    _period.value = period
  }

  suspend fun readGyroPeriod(): Duration {
    val value = peripheral.read(Characteristics.movementPeriod)
    val period = ((value[0].toInt() and 0xFF) * 10).milliseconds
    _period.value = period
    return period
  }

  suspend fun enableGyro() {
    peripheral.write(Characteristics.movementConfig, byteArrayOf(0x7F, 0x0), WithResponse)
  }

  suspend fun disableGyro() {
    peripheral.write(Characteristics.movementConfig, byteArrayOf(0x0, 0x0), WithResponse)
  }

  suspend fun readBatteryLevel(): Int {
    val result = peripheral.read(Characteristics.battery)
    val battery = result.first().toInt()
    _battery.value = battery
    return battery
  }

  suspend fun readRssi(): Int {
    val rssi = peripheral.rssi()
    _rssi.value = rssi
    return rssi
  }
}

// Multi-octet fields within the GATT Profile shall be sent least significant octet first (little endian).

private fun ByteArray.readF32(offset: Int): Float {
  val value = get(offset).toInt() and 0xff or
    (get(offset + 1).toInt() and 0xff shl 8) or
    (get(offset + 2).toInt() and 0xff shl 16) or
    (get(offset + 3).toInt() and 0xff shl 24)
  return Float.fromBits(value)
}

private fun ByteArray.readU16(offset: Int): Int {
  val value = get(offset).toInt() and 0xff or (get(offset + 1).toInt() and 0xff shl 8)
  return value
}

private fun ByteArray.readU8(offset: Int): Int {
  return get(offset).toInt() and 0xff
}

