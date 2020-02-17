package com.geeksville.mesh

import android.os.Parcel
import android.os.Parcelable
import com.geeksville.mesh.ui.bearing
import com.geeksville.mesh.ui.latLongToMeter


// model objects that directly map to the corresponding protobufs
data class MeshUser(val id: String, val longName: String, val shortName: String) :
    Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!
    ) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(longName)
        parcel.writeString(shortName)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<MeshUser> {
        override fun createFromParcel(parcel: Parcel): MeshUser {
            return MeshUser(parcel)
        }

        override fun newArray(size: Int): Array<MeshUser?> {
            return arrayOfNulls(size)
        }
    }
}

data class Position(val latitude: Double, val longitude: Double, val altitude: Int) :
    Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readDouble(),
        parcel.readDouble(),
        parcel.readInt()
    ) {
    }

    /// @return distance in meters to some other node (or null if unknown)
    fun distance(o: Position) = latLongToMeter(latitude, longitude, o.latitude, o.longitude)

    /// @return bearing to the other position in degrees
    fun bearing(o: Position) = bearing(latitude, longitude, o.latitude, o.longitude)

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeDouble(latitude)
        parcel.writeDouble(longitude)
        parcel.writeInt(altitude)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Position> {
        override fun createFromParcel(parcel: Parcel): Position {
            return Position(parcel)
        }

        override fun newArray(size: Int): Array<Position?> {
            return arrayOfNulls(size)
        }
    }
}


data class NodeInfo(
    val num: Int, // This is immutable, and used as a key
    var user: MeshUser? = null,
    var position: Position? = null,
    var lastSeen: Int? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readParcelable(MeshUser::class.java.classLoader),
        parcel.readParcelable(Position::class.java.classLoader),
        parcel.readValue(Int::class.java.classLoader) as? Int
    ) {
    }

    /// @return distance in meters to some other node (or null if unknown)
    fun distance(o: NodeInfo?): Int? {
        val p = position
        val op = o?.position
        return if (p != null && op != null)
            p.distance(op).toInt()
        else
            null
    }

    /// @return a nice human readable string for the distance, or null for unknown
    fun distanceStr(o: NodeInfo?) = distance(o)?.let { dist ->
        when {
            dist == 0 -> null // same point
            dist < 1000 -> "%.0f m".format(dist)
            else -> "%.1f km".format(dist / 1000.0)
        }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(num)
        parcel.writeParcelable(user, flags)
        parcel.writeParcelable(position, flags)
        parcel.writeValue(lastSeen)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<NodeInfo> {
        override fun createFromParcel(parcel: Parcel): NodeInfo {
            return NodeInfo(parcel)
        }

        override fun newArray(size: Int): Array<NodeInfo?> {
            return arrayOfNulls(size)
        }
    }
}