package com.teslamotors.protocol.ui

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

    private var actionDownTime: Long = 0
    private val leftTopPoint = IntArray(2)

    var mPartListener: PartClickListener? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> {
                x = motionEvent.rawX.toInt()
                y = motionEvent.rawY.toInt()
                actionDownTime = System.currentTimeMillis()
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

                // 更新悬浮球控件位置
                windowManager.updateViewLayout(view, layoutParams)
            }

            MotionEvent.ACTION_UP -> {
                // true -> consumption -> not continue
                // false -> not consume -> continue pass

                view.getLocationOnScreen(leftTopPoint)
                val yBound = leftTopPoint[1] + view.height / 2
                val currY = motionEvent.rawY.toInt()

                if (System.currentTimeMillis() - actionDownTime < 300) {
                    // click event
                    if (currY < yBound) {
                        // Top part
                        mPartListener?.onTopClick()
                    } else {
                        // bottom part
                        mPartListener?.onBottomClick()
                    }
                    return false
                }
            }
        }
        return true
    }

    companion object {
        private const val TAG = "ItemViewTouchListener"
    }
}
