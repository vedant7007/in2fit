package io.github.vedant7007.katori.ui

import android.content.Context
import android.content.Intent

/**
 * Hands text to the system share sheet. Text, not a file: no `FileProvider`, no permission, and
 * the demo build's permission whitelist is untouched. What the person picks (mail, a chat, a
 * drive) is theirs; the app has no network and never sends anything itself.
 */
fun shareText(context: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
