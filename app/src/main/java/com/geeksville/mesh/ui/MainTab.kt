package com.geeksville.mesh.ui

import androidx.fragment.app.Fragment
import com.geeksville.mesh.R
import com.geeksville.mesh.ui.map.MapFragment

enum class MainTab(
    val text: String,
    val icon: Int,
    val content: Fragment
) {
    MESSAGES(
        "Messages",
        R.drawable.ic_twotone_message_24,
        ContactsFragment()
    ),
    USERS(
        "Users",
        R.drawable.ic_twotone_people_24,
        UsersFragment()
    ),
    MAP(
        "Map",
        R.drawable.ic_twotone_map_24,
        MapFragment()
    ),
    CHANNEL(
        "Channel",
        R.drawable.ic_twotone_contactless_24,
        ChannelFragment()
    ),
    SETTINGS(
        "Settings",
        R.drawable.ic_twotone_settings_applications_24,
        SettingsFragment()
    );
}