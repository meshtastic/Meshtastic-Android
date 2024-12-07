/*
 * Copyright (c) 2024 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.geeksville.mesh.ui

import androidx.fragment.app.Fragment
import com.geeksville.mesh.android.GeeksvilleApplication

/**
 * A fragment that represents a current 'screen' in our app.
 *
 * Useful for tracking analytics
 */
open class ScreenFragment(private val screenName: String) : Fragment() {

    override fun onResume() {
        super.onResume()
        GeeksvilleApplication.analytics.sendScreenView(screenName)
    }

    override fun onPause() {
        GeeksvilleApplication.analytics.endScreenView()
        super.onPause()
    }
}
