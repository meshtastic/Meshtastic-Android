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

package com.geeksville.mesh.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.geeksville.mesh.R
import java.util.*

/**
 * Created by kevinh on 1/13/16.
 */
class ExpireChecker(val context: Activity) : Logging {
    
    fun check(year: Int, month: Int, day: Int) {
        val expireDate = DateUtils.dateUTC(year, month, day)
        val now = Date()

        debug("Expire check $now vs $expireDate")
        if (now.after(expireDate))
            doExpire()
    }

    private fun doExpire() {
        val packageName = context.packageName
        errormsg("$packageName is too old and must be updated at the Play store")

        Toast.makeText(
            context,
            R.string.app_too_old,
            Toast.LENGTH_LONG
        ).show()
        val i = Intent(Intent.ACTION_VIEW)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        i.setData(Uri.parse("market://details?id=$packageName&referrer=utm_source%3Dexpired"))
        context.startActivity(i)
        context.finish()
    }
}