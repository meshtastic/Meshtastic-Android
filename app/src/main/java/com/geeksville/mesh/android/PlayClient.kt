package com.geeksville.mesh.android

import android.app.Activity
import android.os.Bundle
import com.google.android.gms.common.api.Api
import com.google.android.gms.common.api.Api.ApiOptions.NotRequiredOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GooglePlayServicesUtil
import android.content.IntentSender
import android.content.Intent
import android.util.Log


interface PlayClientCallbacks /* : Activity */ {
    /**
     * Called to tell activity we've lost connection to play
     */
    fun onPlayConnectionSuspended() :Unit

    /**
     * Called to tell activity we are now connected to play
     * Do remaining init here
     */
    fun onPlayConnected() : Unit

    /**
     * Called when this machine does not have a valid form of play.
     */
    fun onPlayUnavailable() : Unit

}

/**
 * Created by kevinh on 1/5/15.
 */

public class PlayClient(val context: Activity, val playCallbacks: PlayClientCallbacks) : Logging {

    var apiClient: GoogleApiClient? = null
    var authInProgress: Boolean = false

    companion object {
        val PLAY_OAUTH_REQUEST_CODE = 901
        val AUTH_PENDING = "authPend"
    }


    /**
     * Must be called from onCreate
     */
    fun playOnCreate(savedInstanceState: Bundle?, apis: Array<Api<out NotRequiredOptions>>, scopes: Array<Scope> = arrayOf()) {

        if(savedInstanceState != null)
            authInProgress = savedInstanceState.getBoolean(AUTH_PENDING)

        if(hasPlayServices()) {
            var builder = GoogleApiClient.Builder(context)
                    .addConnectionCallbacks(object : GoogleApiClient.ConnectionCallbacks {

                        override fun onConnected(p0: Bundle?) {
                            // Connected to Google Play services!
                            // The good stuff goes here.

                            playCallbacks.onPlayConnected()
                        }

                        override fun onConnectionSuspended(i: Int) {
                            // If your connection to the sensor gets lost at some point,
                            // you'll be able to determine the reason and react to it here.
                            if (i == ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                                info("Connection lost.  Cause: Network Lost.");
                            } else if (i == ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                                info("Connection lost.  Reason: Service Disconnected");
                            } else
                                errormsg("Unknown play kode $i")

                            playCallbacks.onPlayConnectionSuspended()
                        }
                    })
                    .addOnConnectionFailedListener(object : GoogleApiClient.OnConnectionFailedListener {
                        override fun onConnectionFailed(result: ConnectionResult) {
                            info("Play connection failed $result")
                            if (!result.hasResolution()) {
                                showErrorDialog(result.errorCode)
                            } else {
                                // The failure has a resolution. Resolve it.
                                // Called typically when the app is not yet authorized, and an
                                // authorization dialog is displayed to the user.
                                if (!authInProgress) {
                                    try {
                                        info("Attempting to resolve failed connection");
                                        authInProgress = true;
                                        result.startResolutionForResult(context,
                                                PLAY_OAUTH_REQUEST_CODE);
                                    } catch (e: IntentSender.SendIntentException) {
                                        errormsg("Exception while starting resolution activity")
                                        playCallbacks.onPlayUnavailable()
                                    }
                                }
                            }
                        }
                    })

            apis.forEach { api ->
                builder = builder.addApi(api)
            }

            scopes.forEach { s ->
                builder = builder.addScope(s)
            }

            apiClient = builder.build()
        }
    }

    private fun showErrorDialog(code: Int) {
        // Show the localized error dialog
        GooglePlayServicesUtil.getErrorDialog(code,
                context, 0)?.show();
        playCallbacks.onPlayUnavailable()
    }

    fun hasPlayServices(): Boolean {
        // Check that Google Play services is available
        val resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(context)
        // For testing
        //val resultCode = ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED

        if (ConnectionResult.SUCCESS == resultCode) {
            // In debug mode, log the status
            Log.d("Geofence Detection",
                    "Google Play services is available.");

            // getAnalytics().track("Has Play")

            // Continue
            return true
            // Google Play services was not available for some reason
        } else {
            showErrorDialog(resultCode)

            return false
        }
    }

    /**
     * Must be called from onActivityResult
     * @return true if we handled this
     */
    fun playOnActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean =
        if (requestCode == PLAY_OAUTH_REQUEST_CODE) {
            authInProgress = false;
            if (resultCode == Activity.RESULT_OK) {
                // Make sure the app is not already connected or attempting to connect
                if (!apiClient!!.isConnecting && !apiClient!!.isConnected) {
                    apiClient!!.connect();
                }
            }
            else {
                // User opted to not install play
                errormsg("User declined play")
                context.finish()
            }
           true
        }
        else
            false

    fun playOnStart() {
        if(apiClient != null)
            apiClient!!.connect()
    }

    fun playOnStop() {
        if(apiClient != null && apiClient!!.isConnected)
            apiClient!!.disconnect()
    }

    fun playSaveInstanceState(outState: Bundle) {
        outState.putBoolean(AUTH_PENDING, authInProgress)
    }
}
