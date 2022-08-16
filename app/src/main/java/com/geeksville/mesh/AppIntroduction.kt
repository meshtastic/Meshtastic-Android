package com.geeksville.mesh

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.fragment.app.Fragment
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
            title = resources.getString(R.string.intro_welcome_title),
            description = resources.getString(R.string.intro_meshtastic_desc),
            imageDrawable = R.mipmap.ic_launcher2_round,
            backgroundColorRes = R.color.colourGrey,
            descriptionColorRes = R.color.colorOnPrimary
        ))
        addSlide(AppIntroFragment.createInstance(
            title = resources.getString(R.string.intro_get_started),
            description = resources.getString(R.string.intro_started_text),
            imageDrawable = R.drawable.icon_meanings,
            backgroundColorRes = R.color.colourGrey,
            descriptionColorRes = R.color.colorOnPrimary
        ))
        addSlide(AppIntroFragment.createInstance(
            title = resources.getString(R.string.intro_encryption_title),
            description = resources.getString(R.string.intro_encryption_text),
            imageDrawable = R.drawable.channel_name_image,
            backgroundColorRes = R.color.colourGrey,
            descriptionColorRes = R.color.colorOnPrimary
        ))
        //addSlide(SlideTwoFragment())
    }

    override fun onSkipPressed(currentFragment: Fragment?) {
        super.onSkipPressed(currentFragment)
        // Decide what to do when the user clicks on "Skip"
        finish()
        val preferences = getSharedPreferences("PREFERENCES", Context.MODE_PRIVATE)
        var editor = preferences.edit()
        editor.putBoolean("app_intro_completed", true)
        editor.apply()

        startActivity(Intent(this, MainActivity::class.java))
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        // Decide what to do when the user clicks on "Done"
        finish()
        val preferences = getSharedPreferences("PREFERENCES", Context.MODE_PRIVATE)
        var editor = preferences.edit()
        editor.putBoolean("app_intro_completed", true)
        editor.apply()

        startActivity(Intent(this, MainActivity::class.java))
    }
}