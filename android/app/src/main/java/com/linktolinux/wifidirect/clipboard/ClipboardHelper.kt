package com.linktolinux.wifidirect.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast

class ClipboardHelper(private val context: Context) {

    private val clipboardManager: ClipboardManager by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    /**
     * Copies the provided text to the system clipboard.
     * On Android 12+, the system automatically shows a visual confirmation.
     * On older versions, we show a simple toast.
     */
    fun copyTextToClipboard(text: String, label: String = "Link to Linux") {
        val clip = ClipData.newPlainText(label, text)
        clipboardManager.setPrimaryClip(clip)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Clears the primary clip from the clipboard.
     */
    fun clearClipboard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboardManager.clearPrimaryClip()
        } else {
            val clip = ClipData.newPlainText("", "")
            clipboardManager.setPrimaryClip(clip)
        }
    }
}
