package com.geeksville.mesh

import android.os.Parcel
import android.os.Parcelable


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
    var lastSeen: Long? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readParcelable(MeshUser::class.java.classLoader),
        parcel.readParcelable(Position::class.java.classLoader),
        parcel.readValue(Long::class.java.classLoader) as? Long
    ) {
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