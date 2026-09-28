package com.dtpos.salonmanager.services.backup

import android.content.Context
import android.content.Intent

/** Restarts the app process after a restore so Room reopens the new database cleanly. */
object AppRestarter {
    fun restart(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val component = launch.component ?: return
        val intent = Intent.makeRestartActivityTask(component)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
