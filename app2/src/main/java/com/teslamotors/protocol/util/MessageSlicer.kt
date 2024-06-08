package com.teslamotors.protocol.util

import android.util.Log
import java.io.ByteArrayOutputStream

object MessageSlicer {

    private const val TAG = "MessageSlicer"

    private val byteArrayOutputStream = ByteArrayOutputStream()

    private var appending = false
    private var dataLen = 0

    fun joint(bytes: ByteArray): ByteArray? {

        if (appending) {
            Log.d(TAG, "messages in appending ...")
            byteArrayOutputStream.write(bytes)

        } else {
            Log.d(TAG, "first chunk of messages")
            appending = true
            byteArrayOutputStream.reset()
            dataLen = 0

            val firstChunkData = preProcess(bytes)
            byteArrayOutputStream.write(firstChunkData)
        }

        val currLen = byteArrayOutputStream.toByteArray().size
        if (currLen != dataLen) {
            Log.d(TAG, "appending not complete!! bytes curr len= $currLen")
            appending = true
            return null
        }

        return byteArrayOutputStream.toByteArray()
    }

    private fun preProcess(bytes: ByteArray): ByteArray {
        val lenBytes = bytes.copyOfRange(0, 2)
        dataLen = JUtils.read2BytesToInt(lenBytes)
        Log.i(TAG, "real len= $dataLen")
        return bytes.copyOfRange(2, bytes.size)
    }
}