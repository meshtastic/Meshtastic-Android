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
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import org.meshtastic.feature.car.R

/**
 * Screens for error/empty states and onboarding. Shown when the radio is disconnected or no channels are configured.
 */
class OnboardingScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template = PaneTemplate.Builder(
        Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_onboarding_title))
                    .addText(carContext.getString(R.string.car_onboarding_text))
                    .build(),
            )
            .build(),
    )
        .setHeader(
            Header.Builder()
                .setTitle(carContext.getString(R.string.car_app_name))
                .setStartHeaderAction(Action.APP_ICON)
                .build(),
        )
        .build()
}
