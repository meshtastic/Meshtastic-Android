package com.geeksville.mesh

import android.os.Parcelable
import com.geeksville.mesh.ui.bearing
import com.geeksville.mesh.ui.latLongToMeter
import com.geeksville.util.anonymize
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable


//
// model objects that directly map to the corresponding protobufs
//

@Serializable
@Parcelize
data class MeshUser(
    val id: String,
    val longName: String,
    val shortName: String,
    val hwModel: MeshProtos.HardwareModel
) : Parcelable {

    override fun toString(): String {
        return "MeshUser(id=${id.anonymize}, longName=${longName.anonymize}, shortName=${shortName.anonymize}, hwModel=${hwModelString})"
    }

    /** a string version of the hardware model, converted into pretty lowercase and changing _ to -, and p to dot
     * or null if unset
     * */
    val hwModelString: String?
        get() =
            if (hwModel == MeshProtos.HardwareModel.UNSET) null
            else hwModel.name.replace('_', '-').replace('p', '.').lowercase()
}

@Serializable
@Parcelize
data class Position(
    val latitude: Double,
    val longitude: Double,
    val altitude: Int,
    val time: Int = currentTime() // default to current time in secs (NOT MILLISECONDS!)
) : Parcelable {
    companion object {
        /// Convert to a double representation of degrees
        fun degD(i: Int) = i * 1e-7
        fun degI(d: Double) = (d * 1e7).toInt()

        fun currentTime() = (System.currentTimeMillis() / 1000).toInt()
    }

    /** Create our model object from a protobuf.  If time is unspecified in the protobuf, the provided default time will be used.
     */
    constructor(p: MeshProtos.Position, defaultTime: Int = currentTime()) : this(
        // We prefer the int version of lat/lon but if not available use the depreciated legacy version
        degD(p.latitudeI),
        degD(p.longitudeI),
        p.altitude,
        if (p.time != 0) p.time else defaultTime
    )

    /// @return distance in meters to some other node (or null if unknown)
    fun distance(o: Position) = latLongToMeter(latitude, longitude, o.latitude, o.longitude)

    /// @return bearing to the other position in degrees
    fun bearing(o: Position) = bearing(latitude, longitude, o.latitude, o.longitude)

    // If GPS gives a crap position don't crash our app
    fun isValid(): Boolean {
        return (latitude <= 90.0 && latitude >= -90) &&
                latitude != 0.0 &&
                longitude != 0.0
    }

    override fun toString(): String {
        return "Position(lat=${latitude.anonymize}, lon=${longitude.anonymize}, alt=${altitude.anonymize}, time=${time})"
    }
}


@Serializable
@Parcelize
data class Telemetry(
    val time: Int = currentTime(), // default to current time in secs (NOT MILLISECONDS!)
    val batteryLevel: Int = 0,
    val voltage: Float,
    val channelUtilization: Float,
    val airUtilTx: Float
) : Parcelable {
    companion object {
        fun currentTime() = (System.currentTimeMillis() / 1000).toInt()
    }

    /** Create our model object from a protobuf.
     */
    constructor(p: TelemetryProtos.Telemetry, defaultTime: Int = currentTime()) : this(
        if (p.time != 0) p.time else defaultTime,
        p.deviceMetrics.batteryLevel,
        p.deviceMetrics.voltage,
        p.deviceMetrics.channelUtilization,
        p.deviceMetrics.airUtilTx
    )

    override fun toString(): String {
        return "Telemetry(time=${time}, batteryLevel=${batteryLevel}, voltage=${voltage}, channelUtilization=${channelUtilization}, airUtilTx=${airUtilTx})"
    }
}


@Serializable
@Parcelize
data class NodeInfo(
    val num: Int, // This is immutable, and used as a key
    var user: MeshUser? = null,
    var position: Position? = null,
    var snr: Float = Float.MAX_VALUE,
    var rssi: Int = Int.MAX_VALUE,
    var lastHeard: Int = 0, // the last time we've seen this node in secs since 1970
    var telemetry: Telemetry? = null
) : Parcelable {

    val batteryPctLevel get() = telemetry?.batteryLevel

    /**
     * true if the device was heard from recently
     *
     * Note: if a node has never had its time set, it will have a time of zero.  In that
     * case assume it is online - so that we will start sending GPS updates
     */
    val isOnline: Boolean
        get() {
            val now = System.currentTimeMillis() / 1000
            // FIXME - use correct timeout from the device settings
            val timeout =
                15 * 60 // Don't set this timeout too tight, or otherwise we will stop sending GPS helper positions to our device
            return (now - lastHeard <= timeout) || lastHeard == 0
        }

    /// return the position if it is valid, else null
    val validPosition: Position?
        get() {
            return position?.takeIf { it.isValid() }
        }

    /// @return distance in meters to some other node (or null if unknown)
    fun distance(o: NodeInfo?): Int? {
        val p = validPosition
        val op = o?.validPosition
        return if (p != null && op != null)
            p.distance(op).toInt()
        else
            null
    }

    /// @return a nice human readable string for the distance, or null for unknown
    fun distanceStr(o: NodeInfo?) = distance(o)?.let { dist ->
        when {
            dist == 0 -> null // same point
            dist < 1000 -> "%.0f m".format(dist.toDouble())
            else -> "%.1f km".format(dist / 1000.0)
        }
    }
}