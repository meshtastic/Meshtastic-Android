package com.geeksville.mesh.database.entity

import android.graphics.Color
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.geeksville.mesh.DeviceMetrics
import com.geeksville.mesh.EnvironmentMetrics
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.MeshUser
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.PaxcountProtos
import com.geeksville.mesh.Position
import com.geeksville.mesh.TelemetryProtos
import com.geeksville.mesh.copy
import com.geeksville.mesh.util.latLongToMeter
import com.google.protobuf.ByteString

@Suppress("MagicNumber")
@Entity(tableName = "nodes")
data class NodeEntity(

    @PrimaryKey(autoGenerate = false)
    val num: Int, // This is immutable, and used as a key

    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    var user: MeshProtos.User = MeshProtos.User.getDefaultInstance(),
    @ColumnInfo(name = "long_name") var longName: String? = null,
    @ColumnInfo(name = "short_name") var shortName: String? = null, // used in includeUnknown filter

    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    var position: MeshProtos.Position = MeshProtos.Position.getDefaultInstance(),
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,

    var snr: Float = Float.MAX_VALUE,
    var rssi: Int = Int.MAX_VALUE,

    @ColumnInfo(name = "last_heard")
    var lastHeard: Int = 0, // the last time we've seen this node in secs since 1970

    @ColumnInfo(name = "device_metrics", typeAffinity = ColumnInfo.BLOB)
    var deviceTelemetry: TelemetryProtos.Telemetry = TelemetryProtos.Telemetry.getDefaultInstance(),

    var channel: Int = 0,

    @ColumnInfo(name = "via_mqtt")
    var viaMqtt: Boolean = false,

    @ColumnInfo(name = "hops_away")
    var hopsAway: Int = -1,

    @ColumnInfo(name = "is_favorite")
    var isFavorite: Boolean = false,

    @ColumnInfo(name = "environment_metrics", typeAffinity = ColumnInfo.BLOB)
    var environmentTelemetry: TelemetryProtos.Telemetry = TelemetryProtos.Telemetry.getDefaultInstance(),

    @ColumnInfo(name = "power_metrics", typeAffinity = ColumnInfo.BLOB)
    var powerTelemetry: TelemetryProtos.Telemetry = TelemetryProtos.Telemetry.getDefaultInstance(),

    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    var paxcounter: PaxcountProtos.Paxcount = PaxcountProtos.Paxcount.getDefaultInstance(),
) {
    val deviceMetrics: TelemetryProtos.DeviceMetrics
        get() = deviceTelemetry.deviceMetrics

    val environmentMetrics: TelemetryProtos.EnvironmentMetrics
        get() = environmentTelemetry.environmentMetrics

    val powerMetrics: TelemetryProtos.PowerMetrics
        get() = powerTelemetry.powerMetrics

    val colors: Pair<Int, Int>
        get() { // returns foreground and background @ColorInt for each 'num'
            val r = (num and 0xFF0000) shr 16
            val g = (num and 0x00FF00) shr 8
            val b = num and 0x0000FF
            val brightness = ((r * 0.299) + (g * 0.587) + (b * 0.114)) / 255
            return (if (brightness > 0.5) Color.BLACK else Color.WHITE) to Color.rgb(r, g, b)
        }

    val hasPKC get() = !user.publicKey.isEmpty
    val errorByteString: ByteString get() = ByteString.copyFrom(ByteArray(32) { 0 })
    val mismatchKey get() = user.publicKey == errorByteString

    val batteryLevel get() = deviceMetrics.batteryLevel
    val voltage get() = deviceMetrics.voltage
    val batteryStr get() = if (batteryLevel in 1..100) "$batteryLevel%" else ""

    fun setPosition(p: MeshProtos.Position, defaultTime: Int = currentTime()) {
        position = p.copy { time = if (p.time != 0) p.time else defaultTime }
        latitude = degD(p.latitudeI)
        longitude = degD(p.longitudeI)
    }

    private fun hasValidPosition(): Boolean {
        return latitude != 0.0 && longitude != 0.0 &&
                (latitude >= -90 && latitude <= 90.0) &&
                (longitude >= -180 && longitude <= 180)
    }

    val validPosition: MeshProtos.Position? get() = position.takeIf { hasValidPosition() }

    // @return distance in meters to some other node (or null if unknown)
    fun distance(o: NodeEntity): Int? {
        return if (validPosition == null || o.validPosition == null) null
        else latLongToMeter(latitude, longitude, o.latitude, o.longitude).toInt()
    }

    private fun TelemetryProtos.EnvironmentMetrics.getDisplayString(isFahrenheit: Boolean): String {
        val temp = if (temperature != 0f) {
            if (isFahrenheit) {
                val fahrenheit = temperature * 1.8F + 32
                "%.1f°F".format(fahrenheit)
            } else {
                "%.1f°C".format(temperature)
            }
        } else null
        val humidity = if (relativeHumidity != 0f) "%.0f%%".format(relativeHumidity) else null
        val pressure = if (barometricPressure != 0f) "%.1fhPa".format(barometricPressure) else null
        val gas = if (gasResistance != 0f) "%.0fMΩ".format(gasResistance) else null
        val voltage = if (this.voltage != 0f) "%.2fV".format(this.voltage) else null
        val current = if (current != 0f) "%.1fmA".format(current) else null
        val iaq = if (iaq != 0) "IAQ: $iaq" else null

        return listOfNotNull(
            temp,
            humidity,
            pressure,
            gas,
            voltage,
            current,
            iaq,
        ).joinToString(" ")
    }

    private fun TelemetryProtos.PowerMetrics.getDisplayString(): String = listOfNotNull(
        "%.2fV".format(ch2Voltage).takeIf { hasCh2Voltage() },
        "%.1fmA".format(ch2Current).takeIf { hasCh2Current() },
        "%.2fV".format(ch3Voltage).takeIf { hasCh3Voltage() },
        "%.1fmA".format(ch3Current).takeIf { hasCh3Current() },
    ).joinToString(" ")

    private fun PaxcountProtos.Paxcount.getDisplayString() =
        "PAX: ${ble + wifi} (B:$ble/W:$wifi)".takeIf { ble != 0 && wifi != 0 }

    fun getTelemetryString(isFahrenheit: Boolean = false): String {
        return listOfNotNull(
            paxcounter.getDisplayString(),
            environmentMetrics.getDisplayString(isFahrenheit),
            powerMetrics.getDisplayString(),
        ).joinToString(" ")
    }

    /**
     * true if the device was heard from recently
     */
    val isOnline: Boolean
        get() {
            val now = System.currentTimeMillis() / 1000
            val timeout = 2 * 60 * 60
            return (now - lastHeard <= timeout)
        }

    companion object {
        /// Convert to a double representation of degrees
        fun degD(i: Int) = i * 1e-7
        fun degI(d: Double) = (d * 1e7).toInt()

        fun currentTime() = (System.currentTimeMillis() / 1000).toInt()
    }
}

fun NodeEntity.toNodeInfo() = NodeInfo(
    num = num,
    user = MeshUser(
        id = user.id,
        longName = user.longName,
        shortName = user.shortName,
        hwModel = user.hwModel,
        role = user.roleValue,
    ).takeIf { user.id.isNotEmpty() },
    position = Position(
        latitude = latitude,
        longitude = longitude,
        altitude = position.altitude,
        time = position.time,
        satellitesInView = position.satsInView,
        groundSpeed = position.groundSpeed,
        groundTrack = position.groundTrack,
        precisionBits = position.precisionBits,
    ).takeIf { it.isValid() },
    snr = snr,
    rssi = rssi,
    lastHeard = lastHeard,
    deviceMetrics = DeviceMetrics(
        time = deviceTelemetry.time,
        batteryLevel = deviceMetrics.batteryLevel,
        voltage = deviceMetrics.voltage,
        channelUtilization = deviceMetrics.channelUtilization,
        airUtilTx = deviceMetrics.airUtilTx,
        uptimeSeconds = deviceMetrics.uptimeSeconds,
    ),
    channel = channel,
    environmentMetrics = EnvironmentMetrics(
        time = environmentTelemetry.time,
        temperature = environmentMetrics.temperature,
        relativeHumidity = environmentMetrics.relativeHumidity,
        barometricPressure = environmentMetrics.barometricPressure,
        gasResistance = environmentMetrics.gasResistance,
        voltage = environmentMetrics.voltage,
        current = environmentMetrics.current,
        iaq = environmentMetrics.iaq,
    ),
    hopsAway = hopsAway,
)
