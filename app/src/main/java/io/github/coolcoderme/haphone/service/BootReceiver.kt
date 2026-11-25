package io.github.coolcoderme.haphone.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.coolcoderme.haphone.data.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        if (Prefs(context).autostart) HubService.start(context)
    }
}
