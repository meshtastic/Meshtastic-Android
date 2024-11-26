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

package com.geeksville.mesh

import android.os.Bundle
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.geeksville.mesh.model.UIViewModel
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment

class AppIntroduction : AppIntro() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make sure you don't call setContentView!

        // Call addSlide passing your Fragments.
        // You can use AppIntroFragment to use a pre-built fragment
        addSlide(
            AppIntroFragment.createInstance(
            title = resources.getString(R.string.intro_welcome),
            description = resources.getString(R.string.intro_welcome_text),
            imageDrawable = R.mipmap.ic_launcher2_round,
            backgroundColorRes = R.color.colourGrey,
            descriptionColorRes = R.color.colorOnPrimary
        ))
        addSlide(AppIntroFragment.createInstance(
            title = resources.getString(R.string.intro_started),
            description = resources.getString(R.string.intro_started_text),
            imageDrawable = R.drawable.icon_meanings,
            backgroundColorRes = R.color.colourGrey,
            descriptionColorRes = R.color.colorOnPrimary
        ))
        addSlide(AppIntroFragment.createInstance(
            title = resources.getString(R.string.intro_encryption),
            description = resources.getString(R.string.intro_encryption_text),
            imageDrawable = R.drawable.channel_name_image,
            backgroundColorRes = R.color.colourGrey,
            descriptionColorRes = R.color.colorOnPrimary
        ))
        //addSlide(SlideTwoFragment())
    }

    private fun done() {
        val prefs = UIViewModel.getPreferences(this)
        prefs.edit { putBoolean("app_intro_completed", true) }
        finish()
    }

    override fun onSkipPressed(currentFragment: Fragment?) {
        super.onSkipPressed(currentFragment)
        // Decide what to do when the user clicks on "Skip"
        done()
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        // Decide what to do when the user clicks on "Done"
        done()
    }
}