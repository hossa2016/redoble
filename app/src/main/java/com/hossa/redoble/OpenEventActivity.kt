package com.hossa.redoble

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Puente invisible: el widget Agenda abre aquí (intención explícita, como exige Android 14+)
 *  y se reenvía el evento al calendario del teléfono. */
class OpenEventActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = intent?.data
        try {
            if (d != null && d.scheme == "content" && d.authority == android.provider.CalendarContract.AUTHORITY)
                startActivity(Intent(Intent.ACTION_VIEW, d).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
        finish()
    }
}
