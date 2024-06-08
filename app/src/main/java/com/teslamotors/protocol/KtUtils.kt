package com.teslamotors.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun Int.to2ByteArray(): ByteArray = byteArrayOf(shr(8).toByte(), toByte())

fun Int.to4ByteArray(): ByteArray = ByteBuffer.allocate(4)
    .order(ByteOrder.BIG_ENDIAN)
    .putInt(this)
    .array();