package com.teslamotors.protocol.util

import com.teslamotors.protocol.vcsec.FromVCSECMessage
import java.io.ByteArrayOutputStream

object MessageUtil {

    const val TAG = "MessageUtil"

    private val byteArrayOutputStream = ByteArrayOutputStream()

    private var inAppending = false
    private var length = 0

    /**
     *
     */
    fun autoChaCha(value: ByteArray): FromVCSECMessage? {

        if (value.isEmpty()) return null

        if (inAppending) {
            byteArrayOutputStream.write(value)

        } else {
            byteArrayOutputStream.reset()
            length = 0

            // get real data len
            val lengthByte = ByteArray(2)
            System.arraycopy(value, 0, lengthByte, 0, 2)
            length = JUtils.read2BytesToInt(lengthByte) // Int

            // slice
            val dataSlice = ByteArray(value.size - 2)
            System.arraycopy(value, 2, dataSlice, 0, value.size - 2)

            byteArrayOutputStream.write(dataSlice)
        }

        val ret = byteArrayOutputStream.toByteArray()
        inAppending = isInAppending(ret, length)

        return when (inAppending) {
            true -> null
            else -> FromVCSECMessage.parseFrom(ret)
        }
    }

    private fun isInAppending(bytes: ByteArray, length: Int): Boolean = (bytes.size != length)

    /**
     *
     */
    fun round(vararg value: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()

        if (value.size == 1) {
            slice(baos, value[0])
        } else {
            for ((index, v) in value.withIndex()) {
                if (index == 0) {
                    slice(baos, v)
                } else {
                    baos.write(value[index])
                }
            }
        }

        val lengthByte = ByteArray(2)
        System.arraycopy(value[0], 0, lengthByte, 0, 2)
        val dataLen = JUtils.read2BytesToInt(lengthByte) // Int

        val ret = baos.toByteArray()

        if (dataLen != ret.size)
            throw IllegalArgumentException("data error. data length not equal with byte array size")

        return ret
    }

    private fun slice(baos: ByteArrayOutputStream, data: ByteArray) {
        val dataSlice = ByteArray(data.size - 2)
        System.arraycopy(data, 2, dataSlice, 0, data.size - 2)
        baos.write(dataSlice)
    }

    private fun getRealDataLength(bytes: ByteArray): Int {
        val b = ByteArray(2)
        System.arraycopy(bytes, 0, b, 0, 2)
        val length = JUtils.read2BytesToInt(b)
        return if (length > 20 * 15) 0 else length
    }

    fun calDataNumbers(bytes: ByteArray): Int {
        val numbers = getRealDataLength(bytes) / 20
        return if (numbers == 0) 1 else numbers + 1
    }
}