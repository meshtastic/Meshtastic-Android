package com.geeksville.mesh

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.Composable
import androidx.compose.Model
import androidx.compose.mutableStateOf
import androidx.compose.state
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.ui.animation.Crossfade
import androidx.ui.core.Modifier
import androidx.ui.core.Text
import androidx.ui.core.WithDensity
import androidx.ui.core.setContent
import androidx.ui.foundation.Clickable
import androidx.ui.foundation.VerticalScroller
import androidx.ui.foundation.shape.corner.RoundedCornerShape
import androidx.ui.graphics.Color
import androidx.ui.graphics.vector.DrawVector
import androidx.ui.layout.*
import androidx.ui.material.*
import androidx.ui.material.ripple.Ripple
import androidx.ui.material.surface.Surface
import androidx.ui.res.vectorResource
import androidx.ui.tooling.preview.Preview
import androidx.ui.unit.dp
import com.geeksville.android.Logging
import com.geeksville.util.exceptionReporter
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.nio.charset.Charset
import java.util.*

// defines the screens we have in the app
sealed class Screen {
    object Home : Screen()
    object Settings : Screen()
}

@Model
object AppStatus {
    var currentScreen: Screen = Screen.Home
}

/**
 * Temporary solution pending navigation support.
 */
fun navigateTo(destination: Screen) {
    AppStatus.currentScreen = destination
}


class MainActivity : AppCompatActivity(), Logging {

    companion object {
        const val REQUEST_ENABLE_BT = 10
        const val DID_REQUEST_PERM = 11

        private val testPositions = arrayOf(
            Position(32.776665, -96.796989, 35), // dallas
            Position(32.960758, -96.733521, 35), // richardson
            Position(32.912901, -96.781776, 35) // north dallas
        )

        private val testNodes = testPositions.mapIndexed { index, it ->
            NodeInfo(
                9 + index,
                MeshUser("+65087653%02d".format(9 + index), "Kevin Mester$index", "KM$index"),
                it,
                12345
            )
        }

        data class TextMessage(val date: Date, val from: String, val text: String)

        private val testTexts = listOf(
            TextMessage(Date(), "+6508675310", "I found the cache"),
            TextMessage(Date(), "+6508675311", "Help! I've fallen and I can't get up.")
        )
    }

    /// A map from nodeid to to nodeinfo
    private val nodes = mutableStateOf(testNodes.map { it.user!!.id to it }.toMap())

    private val messages = mutableStateOf(testTexts)

    /// Are we connected to our radio device
    private var isConnected = mutableStateOf(false)

    private val utf8 = Charset.forName("UTF-8")


    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    fun requestPermission() {
        debug("Checking permissions")

        val perms = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        val missingPerms = perms.filter {
            ContextCompat.checkSelfPermission(
                this,
                it
            ) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPerms.isNotEmpty()) {
            missingPerms.forEach {
                // Permission is not granted
                // Should we show an explanation?
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, it)) {
                    // FIXME
                    // Show an explanation to the user *asynchronously* -- don't block
                    // this thread waiting for the user's response! After the user
                    // sees the explanation, try again to request the permission.
                }
            }

            // Ask for all the missing perms
            ActivityCompat.requestPermissions(this, missingPerms.toTypedArray(), DID_REQUEST_PERM)

            // DID_REQUEST_PERM is an
            // app-defined int constant. The callback method gets the
            // result of the request.
        } else {
            // Permission has already been granted
        }
    }

    @Preview
    @Composable
    fun previewView() {
        composeView()
    }

    private fun sendTestPackets() {
        exceptionReporter {
            val m = meshService!!

            // Do some test operations
            m.setOwner("+16508675309", "Kevin Xter", "kx")
            val testPayload = "hello world".toByteArray()
            m.sendData(
                "+16508675310",
                testPayload,
                MeshProtos.Data.Type.SIGNAL_OPAQUE_VALUE
            )
            m.sendData(
                "+16508675310",
                testPayload,
                MeshProtos.Data.Type.CLEAR_TEXT_VALUE
            )
        }
    }

    @Composable
    fun composeNodeInfo(it: NodeInfo) {
        Text("Node: ${it.user?.longName}")
    }

    @Composable
    fun VectorImageButton(@DrawableRes id: Int, onClick: () -> Unit) {
        Ripple(bounded = false) {
            Clickable(onClick = onClick) {
                VectorImage(id = id)
            }
        }
    }

    @Composable
    fun VectorImage(
        modifier: Modifier = Modifier.None, @DrawableRes id: Int,
        tint: Color = Color.Transparent
    ) {
        val vector = vectorResource(id)
        WithDensity {
            Container(
                modifier = modifier + LayoutSize(
                    vector.defaultWidth,
                    vector.defaultHeight
                )
            ) {
                DrawVector(vector, tint)
            }
        }
    }

    @Composable
    fun HomeScreen(openDrawer: () -> Unit) {
        Column {
            TopAppBar(
                title = { Text(text = "Meshtastic") },
                navigationIcon = {
                    VectorImageButton(R.drawable.ic_launcher_foreground) {
                        openDrawer()
                    }
                }
            )
            VerticalScroller(modifier = LayoutFlexible(1f)) {
                Column {
                    Text(text = "Meshtastic")

                    Text("Radio connected: ${isConnected.value}")

                    nodes.value.values.forEach {
                        composeNodeInfo(it)
                    }

                    messages.value.forEach {
                        Text("Text: ${it.text}")
                    }

                    Button(text = "Start scan",
                        onClick = {
                            if (bluetoothAdapter != null) {
                                // Note: We don't want this service to die just because our activity goes away (because it is doing a software update)
                                // So we use the application context instead of the activity
                                SoftwareUpdateService.enqueueWork(
                                    applicationContext,
                                    SoftwareUpdateService.startUpdateIntent
                                )
                            }
                        })

                    Button(text = "send packets",
                        onClick = { sendTestPackets() })
                }
            }
        }
    }

    @Composable
    fun composeView() {
        val (drawerState, onDrawerStateChange) = state { DrawerState.Closed }

        MaterialTheme {
            ModalDrawerLayout(
                drawerState = drawerState,
                onStateChange = onDrawerStateChange,
                gesturesEnabled = drawerState == DrawerState.Opened,
                drawerContent = {

                    AppDrawer(
                        currentScreen = AppStatus.currentScreen,
                        closeDrawer = { onDrawerStateChange(DrawerState.Closed) }
                    )

                    /*
                    // modifier = Spacing(8.dp)
                    Column() {


                     */
                }, bodyContent = { AppContent { onDrawerStateChange(DrawerState.Opened) } })
        }
    }

    @Composable
    private fun AppContent(openDrawer: () -> Unit) {
        Crossfade(AppStatus.currentScreen) { screen ->
            Surface(color = (MaterialTheme.colors()).background) {
                when (screen) {
                    is Screen.Home -> HomeScreen { openDrawer() }
                    /* is Screen.Interests -> InterestsScreen { openDrawer() }
                    is Screen.Article -> ArticleScreen(postId = screen.postId) */
                }
            }
        }
    }

    @Composable
    private fun AppDrawer(
        currentScreen: Screen,
        closeDrawer: () -> Unit
    ) {
        Column(modifier = LayoutSize.Fill) {
            Spacer(LayoutHeight(24.dp))
            Row(modifier = LayoutPadding(16.dp)) {
                VectorImage(
                    id = R.drawable.ic_launcher_foreground,
                    tint = (MaterialTheme.colors()).primary
                )
                Spacer(LayoutWidth(8.dp))
                VectorImage(id = R.drawable.ic_launcher_foreground)
            }
            Divider(color = Color(0x14333333))
            DrawerButton(
                icon = R.drawable.ic_launcher_foreground,
                label = "Home",
                isSelected = currentScreen == Screen.Home
            ) {
                navigateTo(Screen.Home)
                closeDrawer()
            }

            /*
            DrawerButton(
                icon = R.drawable.ic_interests,
                label = "Interests",
                isSelected = currentScreen == Screen.Interests
            ) {
                navigateTo(Screen.Interests)
                closeDrawer()
            }
             */
        }
    }

    @Composable
    private fun DrawerButton(
        modifier: Modifier = Modifier.None,
        @DrawableRes icon: Int,
        label: String,
        isSelected: Boolean,
        action: () -> Unit
    ) {
        val colors = MaterialTheme.colors()
        val textIconColor = if (isSelected) {
            colors.primary
        } else {
            colors.onSurface.copy(alpha = 0.6f)
        }
        val backgroundColor = if (isSelected) {
            colors.primary.copy(alpha = 0.12f)
        } else {
            colors.surface
        }

        Surface(
            modifier = modifier + LayoutPadding(
                left = 8.dp,
                top = 8.dp,
                right = 8.dp,
                bottom = 0.dp
            ),
            color = backgroundColor,
            shape = RoundedCornerShape(4.dp)
        ) {
            Button(onClick = action, style = TextButtonStyle()) {
                Row {
                    VectorImage(
                        modifier = LayoutGravity.Center,
                        id = icon,
                        tint = textIconColor
                    )
                    Spacer(LayoutWidth(16.dp))
                    Text(
                        text = label,
                        style = (MaterialTheme.typography()).body2.copy(
                            color = textIconColor
                        )
                    )
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // We default to off in the manifest, FIXME turn on only if user approves
        // leave off when running in the debugger
        if (false && !Debug.isDebuggerConnected())
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)

        setContent {
            composeView()
        }

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (bluetoothAdapter != null) {
            bluetoothAdapter!!.takeIf { !it.isEnabled }?.apply {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
        } else {
            Toast.makeText(this, "Error - this app requires bluetooth", Toast.LENGTH_LONG).show()
        }

        requestPermission()

        val filter = IntentFilter()
        filter.addAction("")
        registerReceiver(meshServiceReceiver, filter)
    }

    override fun onDestroy() {
        unregisterReceiver(meshServiceReceiver)
        super.onDestroy()
    }


    private val meshServiceReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) = exceptionReporter {
            debug("Received from mesh service $intent")

            when (intent.action) {
                MeshService.ACTION_NODE_CHANGE -> {
                    warn("TODO nodechange")
                    val info: NodeInfo = intent.getParcelableExtra(EXTRA_NODEINFO)!!

                    // We only care about nodes that have user info
                    info.user?.id?.let {
                        val newnodes = nodes.value.toMutableMap()
                        newnodes[it] = info
                        nodes.value = newnodes
                    }
                }

                MeshService.ACTION_RECEIVED_DATA -> {
                    warn("TODO rxopaqe")
                    val sender = intent.getStringExtra(EXTRA_SENDER)!!
                    val payload = intent.getByteArrayExtra(EXTRA_PAYLOAD)!!
                    val typ = intent.getIntExtra(EXTRA_TYP, -1)

                    when (typ) {
                        MeshProtos.Data.Type.CLEAR_TEXT_VALUE -> {
                            // FIXME - use the real time from the packet
                            val modded = messages.value.toMutableList()
                            modded.add(TextMessage(Date(), sender, payload.toString(utf8)))
                            messages.value = modded
                        }
                        else -> TODO()
                    }
                }
                RadioInterfaceService.CONNECTCHANGED_ACTION -> {
                    isConnected.value = intent.getBooleanExtra(EXTRA_CONNECTED, false)
                    debug("connchange $isConnected")
                }
                else -> TODO()
            }
        }
    }

    private var meshService: IMeshService? = null
    private var isBound = false

    private var serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) = exceptionReporter {
            val m = IMeshService.Stub.asInterface(service)
            meshService = m

            // FIXME - do actions for when we connect to the service
            // FIXME - do actions for when we connect to the service
            debug("did connect")

            isConnected.value = m.isConnected

            // make some placeholder nodeinfos
            nodes.value =
                m.online.toList().map { it to NodeInfo(0, MeshUser(it, "unknown", "unk")) }.toMap()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            meshService = null
        }
    }

    private fun bindMeshService() {
        debug("Binding to mesh service!")
        // we bind using the well known name, to make sure 3rd party apps could also
        logAssert(meshService == null)

        // bind to our service using the same mechanism an external client would use (for testing coverage)
        // The following would work for us, but not external users
        //val intent = Intent(this, MeshService::class.java)
        //intent.action = IMeshService::class.java.name
        val intent = Intent()
        intent.setClassName("com.geeksville.mesh", "com.geeksville.mesh.MeshService")

        // Before binding we want to explicitly create - so the service stays alive forever (so it can keep
        // listening for the bluetooth packets arriving from the radio.  And when they arrive forward them
        // to Signal or whatever.

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        // ALSO bind so we can use the api
        logAssert(bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE))
        isBound = true;
    }

    private fun unbindMeshService() {
        // If we have received the service, and hence registered with
        // it, then now is the time to unregister.
        // if we never connected, do nothing
        debug("Unbinding from mesh service!")
        if (isBound)
            unbindService(serviceConnection)
        meshService = null
    }

    override fun onPause() {
        unbindMeshService()

        super.onPause()
    }

    override fun onResume() {
        super.onResume()

        bindMeshService()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
}


