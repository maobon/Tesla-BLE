package com.sample.livedata

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.sample.livedata.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var mainBinding: ActivityMainBinding
    private lateinit var mService:MyService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mainBinding.root)

        MyService.data.observe(this) { data ->
            Log.d(TAG, "onCreate: ac received data=$data")

            mainBinding.tvTextview.text = data
        }

        mainBinding.btnTest.setOnClickListener {
            mService.haha("caonima")
        }
    }

    private val mConnImpl = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mService = (service as MyService.InnerBinder).getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            TODO("Not yet implemented")
        }

    }

    override fun onResume() {
        super.onResume()

        Intent(this@MainActivity, MyService::class.java).apply {
            startService(this)
        }

        bindService(
            Intent(this@MainActivity, MyService::class.java),
            mConnImpl,
            Context.BIND_AUTO_CREATE
        )
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}