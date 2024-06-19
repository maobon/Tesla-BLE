package com.sample.drawoverlay

import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager

class Window(
    private val context: Context,
    partClickListener: PartClickListener? = null
) {
    private val mView: View
    private var mParams: WindowManager.LayoutParams? = null

    // WindowManager
    private val windowManager: WindowManager =
        context.getSystemService(WINDOW_SERVICE) as WindowManager

    // LayoutInflater
    private val layoutInflater: LayoutInflater =
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    init {
        // inflating the view with the custom layout we created
        mView = layoutInflater.inflate(R.layout.cust_window, null)

        // val outMetrics = DisplayMetrics()
        // mWindowManager.defaultDisplay.getMetrics(outMetrics)

        mParams = getLayoutParams()

        val touchListener = ItemViewTouchListener(mParams!!, windowManager)
        touchListener.mPartListener = partClickListener

        mView.setOnTouchListener(touchListener)

        // click event will set new onTouchListener ...
        // child view of ViewGroup can not use setOnClickListener
        // mView.findViewById<ImageView>(R.id.iv_1).setOnClickListener {
        //     Log.d(TAG, "image view - 1 is clicked")
        // }
        // mView.findViewById<ImageView>(R.id.iv_2).setOnClickListener {
        //     Log.d(TAG, "image view - 2 is clicked")
        // }
    }

    fun open() {
        try {
            // check if the view is already inflated or present in the window
            if (mView.windowToken == null) {
                if (mView.parent == null) {
                    windowManager.addView(mView, mParams)
                }
            }
        } catch (e: Exception) {
            Log.d("Error1", e.toString())
        }
    }

    fun close() {
        try {
            // remove the view from the window
            windowManager.removeView(mView)
            // invalidate the view
            mView.invalidate()
            // remove all views
            (mView.parent as ViewGroup).removeAllViews()

            // the above steps are necessary when you are adding and removing the view
            // simultaneously, it might give some exceptions

            Log.d(TAG, "test print log")

        } catch (e: Exception) {
            Log.d("Error2", e.toString())
        }
    }

    /**
     * Define the position of the window within the screen set the layout parameters of the window
     */
    private fun getLayoutParams(w: Int = 150, h: Int = 300): WindowManager.LayoutParams {
        // Display it on top of other application windows
        return WindowManager.LayoutParams().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = //刘海屏延伸到刘海里面
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            } else {
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

    companion object {
        private const val TAG = "Window"
    }
}