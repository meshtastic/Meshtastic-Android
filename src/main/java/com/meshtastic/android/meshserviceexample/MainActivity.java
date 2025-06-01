package com.meshtastic.android.meshserviceexample;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.geeksville.mesh.IMeshService;
import com.geeksville.mesh.NodeInfo;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MeshServiceExample";
    private IMeshService meshService;
    private ServiceConnection serviceConnection;
    private BroadcastReceiver meshtasticReceiver;
    private boolean isMeshServiceBound = false;

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Now you can call methods on meshService
        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                meshService = IMeshService.Stub.asInterface(service);
                Log.i(TAG, "Connected to MeshService");
                isMeshServiceBound = true;
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                meshService = null;
                isMeshServiceBound = false;
            }
        };

        meshtasticReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) {
                    Log.w(TAG, "Received null intent or action");
                    return;
                }
                // Handle the received broadcast
                String action = intent.getAction();
                Log.d(TAG, "Received broadcast: " + action);

                switch (Objects.requireNonNull(action)) {
                    case "com.geeksville.mesh.NODE_CHANGE":
                        // handle node changed
                        try {
                            NodeInfo ni = intent.getParcelableExtra("com.geeksville.mesh.NodeInfo");
                            Log.d(TAG, "NodeInfo: " + ni);
                        } catch (Exception e) {
                            e.printStackTrace();
                            return;
                        }
                        break;
                    default:
                        Log.w(TAG, "Unknown action: " + action);
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.geeksville.mesh.RECEIVED.NODEINFO_APP");
        registerReceiver(meshtasticReceiver, filter, Context.RECEIVER_EXPORTED);
        Log.d(TAG, "Registered meshtasticPacketReceiver");

        while (!bindMeshService()) {
            try {
                // Wait for the service to bind
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Binding interrupted", e);
                break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindMeshService();
    }

    private boolean bindMeshService() {
        try {
            Log.i(TAG, "Attempting to bind to Mesh Service...");
            Intent intent = new Intent("com.geeksville.mesh.Service");
            intent.setClassName("com.geeksville.mesh", "com.geeksville.mesh.service.MeshService");
            return bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind", e);
        }
        return false;
    }

    private void unbindMeshService() {
        if (isMeshServiceBound) {
            try {
                unbindService(serviceConnection);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "MeshService not registered or already unbound: " + e.getMessage());
            }
            isMeshServiceBound = false;
            meshService = null;
        }
    }

}