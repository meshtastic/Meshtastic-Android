package com.geeksville.mesh.ui

import androidx.compose.Composable
import androidx.ui.core.ContextAmbient
import androidx.ui.foundation.Text
import androidx.ui.layout.Column
import androidx.ui.layout.LayoutPadding
import androidx.ui.layout.Row
import androidx.ui.material.Button
import androidx.ui.unit.dp
import com.geeksville.mesh.R
import com.geeksville.mesh.model.NodeDB
import com.geeksville.mesh.model.UIState
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.service.RadioInterfaceService
import com.geeksville.mesh.service.SoftwareUpdateService


/// Given a human name, strip out the first letter of the first three words and return that as the initials for
/// that user.
fun getInitials(name: String): String {
    val words = name.split(Regex("\\s+")).filter { it.isNotEmpty() }.take(3).map { it.first() }
        .joinToString("")

    return words
}

@Composable
fun UsersContent() {
    analyticsScreen(name = "users")

    Column {
        Row {
            fun connected() = UIState.isConnected.value != MeshService.ConnectionState.DISCONNECTED
            VectorImage(
                id = if (connected()) R.drawable.cloud_on else R.drawable.cloud_off,
                tint = palette.onBackground,
                modifier = LayoutPadding(start = 8.dp)
            )

            Column {

                Text(
                    when (UIState.isConnected.value) {
                        MeshService.ConnectionState.CONNECTED -> "Connected"
                        MeshService.ConnectionState.DISCONNECTED -> "Disconnected"
                        MeshService.ConnectionState.DEVICE_SLEEP -> "Power Saving"
                    },
                    modifier = LayoutPadding(start = 8.dp)
                )

                if (false) { // hide the firmware update button for now, it is kinda ugly and users don't need it yet
                    /// Create a software update button
                    val context = ContextAmbient.current
                    RadioInterfaceService.getBondedDeviceAddress(context)?.let { macAddress ->
                        Button(
                            onClick = {
                                SoftwareUpdateService.enqueueWork(
                                    context,
                                    SoftwareUpdateService.startUpdateIntent(macAddress)
                                )
                            }
                        ) {
                            Text(text = "Update firmware")
                        }
                    }
                }
            }
        }

        NodeDB.nodes.values.forEach {
            NodeInfoCard(it)
        }


        /* FIXME - doens't work yet - probably because I'm not using release keys
        // If account is null, then show the signin button, otherwise
        val context = ambient(ContextAmbient)
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null)
            Text("We have an account")
        else {
            Text("No account yet")
            if (context is Activity) {
                Button("Google sign-in", onClick = {
                    val signInIntent: Intent = UIState.googleSignInClient.signInIntent
                    context.startActivityForResult(signInIntent, MainActivity.RC_SIGN_IN)
                })
            }
        } */
    }
}
