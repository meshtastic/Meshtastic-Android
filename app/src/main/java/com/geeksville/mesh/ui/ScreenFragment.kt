package com.geeksville.mesh.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.geeksville.android.GeeksvilleApplication
import com.geeksville.mesh.R

/**
 * A fragment that represents a current 'screen' in our app.
 *
 * Useful for tracking analytics
 */
open class ScreenFragment(private val screenName: String) : Fragment() {
    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager =
            requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    override fun onResume() {
        super.onResume()
        GeeksvilleApplication.analytics.sendScreenView(screenName)

        // Keep reminding user BLE is still off
        if (bluetoothAdapter?.isEnabled != true) {
            Toast.makeText(
                requireContext(),
                R.string.error_bluetooth,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onPause() {
        GeeksvilleApplication.analytics.endScreenView()
        super.onPause()
    }
}
