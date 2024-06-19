package com.sample.drawoverlay

import android.annotation.SuppressLint
import android.util.Log
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
    private val leftTopPoint = IntArray(2)

    var mPartListener: PartClickListener? = null


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

                // 更新悬浮球控件位置
                windowManager.updateViewLayout(view, layoutParams)
            }

            MotionEvent.ACTION_UP -> {
                // return System.currentTimeMillis() - currTime >= 300
                // true -> consumption -> not continue
                // false -> not consume -> continue pass

                view.getLocationOnScreen(leftTopPoint)
                val yBound = leftTopPoint[1] + view.height / 2

                // val xx = motionEvent.rawX.toInt()
                val yy = motionEvent.rawY.toInt()

                if (System.currentTimeMillis() - currTime < 300) {
                    Log.d(TAG, "onTouch: click event")

                    if (yy < yBound) {
                        Log.d(TAG, "onTouch: 1111111111111")
                        mPartListener?.onTopClick()

                    } else {
                        Log.d(TAG, "onTouch: 222222222222222")
                        mPartListener?.onBottomClick()
                    }


                    return false
                }
            }

            else -> {
                return true
            }
        }

        return true
    }

    companion object {
        private const val TAG = "ItemViewTouchListener"
    }
}
