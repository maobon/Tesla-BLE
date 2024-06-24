package com.teslamotors.protocol.ui

import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.graphics.PixelFormat
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.teslamotors.protocol.R
import com.teslamotors.protocol.util.toPx

class OverlayController(
    private val context: Context,
    partClickListener: PartClickListener? = null
) {
    private val mFloatBallView: View
    private var mIsOpening = false

    // WindowManager
    private val windowManager: WindowManager =
        context.getSystemService(WINDOW_SERVICE) as WindowManager

    // LayoutInflater
    private val inflater: LayoutInflater =
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    init {
        // inflating the view with the custom layout we created
        mFloatBallView = inflater.inflate(R.layout.cust_window, null)

        val touchListener = ChildViewTouchListener(getLayoutParams(), windowManager)
        touchListener.mPartListener = partClickListener

        mFloatBallView.setOnTouchListener(touchListener)
    }

    fun openOverlay() {
        try {
            // check if the view is already inflated or present in the window
            if (mFloatBallView.windowToken == null) {
                if (mFloatBallView.parent == null) {
                    windowManager.addView(mFloatBallView, getLayoutParams())
                    mIsOpening = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun closeOverlay() {
        try {
            // overlay is opening ?
            if(!mIsOpening) return

            // remove the view from the window
            windowManager.removeView(mFloatBallView)
            // invalidate the view
            mFloatBallView.invalidate()

            // remove all views
            (mFloatBallView.parent as ViewGroup).removeAllViews()

            // the above steps are necessary when you are adding and removing the view
            // simultaneously, it might give some exceptions
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Define the position of the window within the screen set the layout parameters of the window
     */
    private fun getLayoutParams(w: Int = 50, h: Int = 100): WindowManager.LayoutParams {
        // Display it on top of other application windows
        return WindowManager.LayoutParams().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = //刘海屏延伸到刘海里面
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }

            flags =
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

            format = PixelFormat.TRANSPARENT

            // real display width and height
            width = context.toPx(w).toInt()
            height = context.toPx(h).toInt()
        }
    }

}