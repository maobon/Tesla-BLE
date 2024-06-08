package com.teslamotors.protocol.ui

import android.app.Activity
import android.content.DialogInterface.OnClickListener
import androidx.appcompat.app.AlertDialog

class DialogUtil(
    private val activity: Activity
) {

    fun show(
        title: String,
        message: String,
        onClickListener: OnClickListener,
    ) = activity.runOnUiThread {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok, onClickListener)
            .show()
    }
}