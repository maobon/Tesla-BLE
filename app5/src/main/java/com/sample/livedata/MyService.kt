package com.sample.livedata

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.lifecycle.MutableLiveData

class MyService : Service() {

    private val mInnerBinder by lazy {
        InnerBinder()
    }

    inner class InnerBinder : Binder() {
        fun getService() = this@MyService
    }

    override fun onBind(intent: Intent): IBinder {
        return mInnerBinder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: Service start")

        Handler(Looper.getMainLooper()).postDelayed({
            data.postValue("Hello")
        }, 5 * 1000L)

        return super.onStartCommand(intent, flags, startId)
    }

    fun haha(string: String) {
        val strBuilder = StringBuilder()
        strBuilder.append(data.value)
        strBuilder.append("\n$string")
        data.postValue(strBuilder.toString())
    }

    companion object {
        private const val TAG = "MyService"
        val data = MutableLiveData<String>()
    }
}