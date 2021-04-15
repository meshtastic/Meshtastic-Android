package com.geeksville.mesh.service

import android.location.Location
import android.os.RemoteException
import com.geeksville.mesh.DataPacket
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult

val Location.isAccurateForMesh: Boolean get() = !this.hasAccuracy() || this.accuracy < 200

private fun List<Location>.filterAccurateForMesh() = filter { it.isAccurateForMesh }

private fun LocationResult.lastLocationOrBestEffort(): Location? {
    return lastLocation ?: locations.filterAccurateForMesh().lastOrNull()
}

typealias SendPosition = (Double, Double, Int, Int, Boolean) -> Unit // Lat, Lon, alt, destNum, wantResponse
typealias OnSendFailure = () -> Unit
typealias GetNodeNum = () -> Int

class MeshServiceLocationCallback(
    private val onSendPosition: SendPosition,
    private val onSendPositionFailed: OnSendFailure,
    private val getNodeNum: GetNodeNum
) : LocationCallback() {

    companion object {
        const val DEFAULT_SEND_RATE_LIMIT = 30
    }

    private var lastSendTimeMs: Long = 0L

    override fun onLocationResult(locationResult: LocationResult) {
        super.onLocationResult(locationResult)

        locationResult.lastLocationOrBestEffort()?.let { location ->
            MeshService.info("got phone location")
            if (location.isAccurateForMesh) { // if within 200 meters, or accuracy is unknown

                try {
                    // Do we want to broadcast this position globally, or are we just telling the local node what its current position is (
                    val shouldBroadcast =
                        true // no need to rate limit, because we are just sending at the interval requested by the preferences
                    val destinationNumber =
                        if (shouldBroadcast) DataPacket.NODENUM_BROADCAST else getNodeNum()

                    // Note: we never want this message sent as a reliable message, because it is low value and we'll be sending one again later anyways
                    sendPosition(location, destinationNumber, wantResponse = false)

                } catch (ex: RemoteException) { // Really a RadioNotConnected exception, but it has changed into this type via remoting
                    MeshService.warn("Lost connection to radio, stopping location requests")
                    onSendPositionFailed()
                } catch (ex: BLEException) { // Really a RadioNotConnected exception, but it has changed into this type via remoting
                    MeshService.warn("BLE exception, stopping location requests $ex")
                    onSendPositionFailed()
                }
            } else {
                MeshService.warn("accuracy ${location.accuracy} is too poor to use")
            }
        }
    }

    private fun sendPosition(location: Location, destinationNumber: Int, wantResponse: Boolean) {
        onSendPosition(
            location.latitude,
            location.longitude,
            location.altitude.toInt(),
            destinationNumber,
            wantResponse // wantResponse?
        )
    }
}
