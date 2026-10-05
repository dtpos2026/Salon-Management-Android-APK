package com.dtpos.salonmanager.presentation.common

import android.content.Context
import android.widget.Toast
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.services.export.WhatsAppResult

/** Tells the owner what actually happened after a WhatsApp button (never claims "sent"). */
fun Context.showWhatsAppResult(result: WhatsAppResult) {
    val res = when (result) {
        WhatsAppResult.WHATSAPP -> return
        WhatsAppResult.OTHER_APP -> R.string.whatsapp_fallback_opened
        WhatsAppResult.FAILED -> R.string.whatsapp_failed
    }
    Toast.makeText(this, getString(res), Toast.LENGTH_LONG).show()
}
