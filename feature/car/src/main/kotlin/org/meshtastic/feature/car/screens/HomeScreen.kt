/*
 * Copyright (c) 2026 Meshtastic LLC
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

package org.meshtastic.feature.car.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Tab
import androidx.car.app.model.TabContents
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.Template
import org.meshtastic.feature.car.R

class HomeScreen(carContext: CarContext) : Screen(carContext) {

    private var selectedTabId: String = TAB_ID_MESSAGES

    override fun onGetTemplate(): Template {
        val messagingTab = Tab.Builder()
            .setContentId(TAB_ID_MESSAGES)
            .setTitle(carContext.getString(R.string.car_tab_messages))
            .build()

        val nodesTab = Tab.Builder()
            .setContentId(TAB_ID_NODES)
            .setTitle(carContext.getString(R.string.car_tab_nodes))
            .build()

        return TabTemplate.Builder(object : TabTemplate.TabCallback {
            override fun onTabSelected(tabContentId: String) {
                selectedTabId = tabContentId
                invalidate()
            }
        }).apply {
            setHeaderAction(Action.APP_ICON)
            addTab(messagingTab)
            addTab(nodesTab)
            setActiveTab(selectedTabId)
            setTabContents(getTabContents())
        }.build()
    }

    private fun getTabContents(): TabContents {
        val placeholder = ListTemplate.Builder()
            .setSingleList(
                ItemList.Builder()
                    .addItem(
                        Row.Builder()
                            .setTitle("Loading...")
                            .build()
                    )
                    .build()
            )
            .build()
        return TabContents.Builder(placeholder).build()
    }

    companion object {
        private const val TAB_ID_MESSAGES = "messages"
        private const val TAB_ID_NODES = "nodes"
    }
}
