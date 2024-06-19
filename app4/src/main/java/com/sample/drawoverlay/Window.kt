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
    private val context: Context, // declaring required variables
    private val partClickListener: PartClickListener? = null
) {
    private val mView: View
    private var mParams: WindowManager.LayoutParams? = null

    private val mWindowManager: WindowManager =
        context.getSystemService(WINDOW_SERVICE) as WindowManager

    // getting a LayoutInflater
    private val layoutInflater: LayoutInflater =
        context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    init {

        // inflating the view with the custom layout we created
        // mView = layoutInflater.inflate(R.layout.popup_window, null)
        mView = layoutInflater.inflate(R.layout.cust_window, null)

        // set onClickListener on the remove button, which removes the view from the window
        // mView.findViewById<View>(R.id.window_close).setOnClickListener { close() }
        // mView.setOnClickListener { close() }

        // Define the position of the window within the screen
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        //     // set the layout parameters of the window
        //     mParams = WindowManager.LayoutParams(
        //         // Shrink the window to wrap the content rather than filling the screen
        //         WindowManager.LayoutParams.WRAP_CONTENT,
        //         WindowManager.LayoutParams.WRAP_CONTENT,
        //         WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,  // Display it on top of other application windows
        //         WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, // Don't let it grab the input focus
        //         PixelFormat.TRANSLUCENT  // Make the underlying application window visible through any transparent parts
        //     )
        // }
        // mParams!!.gravity = Gravity.CENTER

        // val outMetrics = DisplayMetrics()
        // mWindowManager.defaultDisplay.getMetrics(outMetrics)

        val layoutParam = WindowManager.LayoutParams().apply {
            // 显示的位置
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                //刘海屏延伸到刘海里面
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            } else {
                type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }
            flags =
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

            // real display width and height
            width = 150
            height = 300

            format = PixelFormat.TRANSPARENT
        }
        mParams = layoutParam

        val touchListener = ItemViewTouchListener(layoutParam, mWindowManager)
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
                    mWindowManager.addView(mView, mParams)
                }
            }
        } catch (e: Exception) {
            Log.d("Error1", e.toString())
        }
    }

    fun close() {
        try {
            // remove the view from the window
            (context.getSystemService(WINDOW_SERVICE) as WindowManager).removeView(mView)
            // invalidate the view
            mView.invalidate()
            // remove all views
            (mView.parent as ViewGroup).removeAllViews()

            // the above steps are necessary when you are adding and removing
            // the view simultaneously, it might give some exceptions

            Log.d(TAG, "test print log")

        } catch (e: Exception) {
            Log.d("Error2", e.toString())
        }
    }

    companion object {
        private const val TAG = "Window"
    }
}