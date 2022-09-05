package com.geeksville.mesh.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
            "This application is out of date and must be updated",
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