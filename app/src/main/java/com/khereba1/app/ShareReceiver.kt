package com.khereba1.app

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Toast

/** Share target: other apps send text, links, photos, audio, video here; it is saved to the Inbox without opening the app. */
class ShareReceiverActivity : Activity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = intent
        val app = applicationContext
        Thread {
            var ok = false
            try {
                ok = NoteStore.createFromShare(app, i)
            } catch (e: Throwable) {
                AppLog.log("NOTES", "Share failed", e)
            }
            runOnUiThread {
                val msg = LocaleHelper.wrap(app).getString(if (ok) R.string.share_saved else R.string.share_failed)
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                finish()
            }
        }.start()
    }
}
