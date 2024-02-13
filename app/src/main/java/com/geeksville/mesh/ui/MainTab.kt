package com.geeksville.mesh.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.geeksville.mesh.R
import com.geeksville.mesh.ui.map.MapFragment

enum class MainTab(
    @StringRes
    val text: Int,
    @DrawableRes
    val icon: Int,
    val content: Fragment
) {
    MESSAGES(
        R.string.main_tab_lbl_messages,
        R.drawable.ic_twotone_message_24,
        ContactsFragment()
    ),
    USERS(
        R.string.main_tab_lbl_users,
        R.drawable.ic_twotone_people_24,
        UsersFragment()
    ),
    MAP(
        R.string.main_tab_lbl_map,
        R.drawable.ic_twotone_map_24,
        MapFragment()
    ),
    CHANNEL(
        R.string.main_tab_lbl_channel,
        R.drawable.ic_twotone_contactless_24,
        ChannelFragment()
    ),
    SETTINGS(
        R.string.main_tab_lbl_settings,
        R.drawable.ic_twotone_settings_applications_24,
        SettingsFragment()
    );
}