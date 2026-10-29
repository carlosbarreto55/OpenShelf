package com.example.paperReader.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** A completed download to hand to another app, by the content URI its record already holds. */
data class OpenFileRequest(val downloadId: String, val contentUri: String, val mimeType: String?)

/**
 * The external handoff, kept as an explicit choice next to the in-app reader. The other app gets a
 * temporary read grant for the one content URI — never a filesystem path, and nothing else in the
 * managed directory. Answers false when no installed app can open it, which callers report.
 */
fun Context.openInAnotherApp(request: OpenFileRequest): Boolean {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(request.contentUri.toUri(), request.mimeType ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
