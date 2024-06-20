package com.sample.drawoverlay

import android.content.Context
import android.util.TypedValue

const val CHANNEL_ID = "com.sample.drawoverlay.id"
const val CHANNEL_NAME = "com.sample.drawoverlay"

const val SERVICE_ACTION_STOP = "com.sample.drawoverlay.stop.service"

fun Context.toPx(dp: Int): Float = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    dp.toFloat(),
    resources.displayMetrics
)

