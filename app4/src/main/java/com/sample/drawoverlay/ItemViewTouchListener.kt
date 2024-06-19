package com.sample.drawoverlay

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class ItemViewTouchListener(
    private val layoutParams: WindowManager.LayoutParams,
    private val windowManager: WindowManager
) : View.OnTouchListener {

    private var x = 0
    private var y = 0

    private var currTime: Long = 0
    private var moveTime: Long = 0

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> {
                x = motionEvent.rawX.toInt()
                y = motionEvent.rawY.toInt()

                currTime = System.currentTimeMillis()
            }

            MotionEvent.ACTION_MOVE -> {
                val nowX = motionEvent.rawX.toInt()
                val nowY = motionEvent.rawY.toInt()

                val movedX = nowX - x
                val movedY = nowY - y
                x = nowX
                y = nowY

                layoutParams.apply {
                    x += movedX
                    y += movedY
                }

                //更新悬浮球控件位置
                windowManager.updateViewLayout(view, layoutParams)
            }

            MotionEvent.ACTION_UP -> {
                moveTime = System.currentTimeMillis() - currTime
                if (moveTime < 300) {
                    return false
                } else {
                    return true
                }
            }
        }

        return false
    }
}
