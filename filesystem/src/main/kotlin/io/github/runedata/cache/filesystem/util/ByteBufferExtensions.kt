/*
   Copyright 2018 Bart van Helvert

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package io.github.runedata.cache.filesystem.util

import io.github.runedata.cache.filesystem.crypto.Whirlpool
import io.github.runedata.cache.filesystem.crypto.Xtea
import java.nio.ByteBuffer
import java.util.zip.CRC32

/** The modified set of 'extended ASCII' characters used by the client.  */
private val CHARACTERS = charArrayOf(
    '\u20AC', '\u0000', '\u201A', '\u0192', '\u201E', '\u2026', '\u2020', '\u2021', '\u02C6', '\u2030', '\u0160',
    '\u2039', '\u0152', '\u0000', '\u017D', '\u0000', '\u0000', '\u2018', '\u2019', '\u201C', '\u201D', '\u2022',
    '\u2013', '\u2014', '\u02DC', '\u2122', '\u0161', '\u203A', '\u0153', '\u0000', '\u017E', '\u0178'
)

/** Calculates the CRC32 checksum of the specified buffer. */
fun ByteBuffer.getCrcChecksum(): Int {
    val crc = CRC32()
    for (i in 0 until limit()) {
        crc.update(get(i).toInt())
    }
    return crc.value.toInt()
}

/** Gets a null-terminated string from the specified buffer, using a modified ISO-8859-1 character set.*/
fun ByteBuffer.getString(): String {
    val bldr = StringBuilder()
    var b: Int = get().toInt()
    while (b != 0) {
        if (b in 127..159) {
            var curChar = CHARACTERS[b - 128]
            if (curChar.toInt() == 0) {
                curChar = 63.toChar()
            }

            bldr.append(curChar)
        } else {
            bldr.append(b.toChar())
        }
        b = get().toInt()
    }
    return bldr.toString()
}

/** Gets a smart integer from the buffer. */
fun ByteBuffer.getSmartInt(): Int {
    return if (get(position()) < 0) {
        int and 0x7fffffff
    } else {
        short.toInt() and 0xFFFF
    }
}

/** Reads a tri-byte from the specified buffer. */
fun ByteBuffer.getMedium(): Int {
    return get().toInt() and 0xFF shl 16 or (get().toInt() and 0xFF shl 8) or (get().toInt() and 0xFF)
}

/** Writes a tri-byte to the specified buffer. */
fun ByteBuffer.putMedium(value: Int) {
    put((value shr 16).toByte())
    put((value shr 8).toByte())
    put(value.toByte())
}

/** Gets an unsigned byte from the buffer */
fun ByteBuffer.getUnsignedByte(): Int {
    return get().toInt() and 0xFF
}

/** Gets an unsigned short from the buffer */
fun ByteBuffer.getUnsignedShort(): Int {
    return short.toInt() and 0xFFFF
}

fun ByteBuffer.getUnsignedSmart() = if (get(position()).toInt() and 0xFF < 128) {
    getUnsignedByte()
} else {
    getUnsignedShort() - 32768
}

fun ByteBuffer.getLargeSmart() = if(get(position()).toInt() and 0xFF < 0) {
    int and 0x7FFFFFFF
} else {
    val value = getUnsignedShort()
    if (value == 32767) -1 else value
}
fun ByteBuffer.getParams(): MutableMap<Int, Any> {
    val length = getUnsignedByte()
    val params = HashMap<Int, Any>(length)
    for (i in 0 until length) {
        val isString = getUnsignedByte()
        val key = getMedium()
        val value: Any = when (isString) {
            0 -> int
            1 -> getString()
            else -> kotlin.error(isString)
        }
        params[key] = value
    }
    return params
}

fun ByteBuffer.getByteArray(size: Int = limit()): ByteArray {
    val byteArray = ByteArray(size)
    get(byteArray)
    return byteArray
}
fun ByteBuffer.toByteArray(size: Int = limit()): ByteArray {
    val byteArray = ByteArray(size)
    mark()
    get(byteArray)
    reset()
    return byteArray
}

fun ByteBuffer.xteaEncipher(keys: IntArray, start: Int, end: Int): ByteBuffer {
    require(keys.size == 4)
    val numQuads = (end - start) / 8
    for (i in 0 until numQuads) {
        var sum = 0
        var v0 = getInt(start + i * 8)
        var v1 = getInt(start + i * 8 + 4)
        repeat(Xtea.ROUNDS) {
            v0 += (v1 shl 4 xor v1.ushr(5)) + v1 xor sum + keys[sum and 3]
            sum += Xtea.GOLDEN_RATIO
            v1 += (v0 shl 4 xor v0.ushr(5)) + v0 xor sum + keys[sum.ushr(11) and 3]
        }
        putInt(start + i * 8, v0)
        putInt(start + i * 8 + 4, v1)
    }
    return this
}

@Suppress("INTEGER_OVERFLOW")
fun ByteBuffer.xteaDecipher(keys: IntArray, start: Int, end: Int): ByteBuffer {
    require(keys.size == 4)
    val numQuads = (end - start) / 8
    for (i in 0 until numQuads) {
        var sum = Xtea.GOLDEN_RATIO * Xtea.ROUNDS
        var v0 = getInt(start + i * 8)
        var v1 = getInt(start + i * 8 + 4)
        repeat(Xtea.ROUNDS) {
            v1 -= (v0 shl 4 xor v0.ushr(5)) + v0 xor sum + keys[sum.ushr(11) and 3]
            sum -= Xtea.GOLDEN_RATIO
            v0 -= (v1 shl 4 xor v1.ushr(5)) + v1 xor sum + keys[sum and 3]
        }
        putInt(start + i * 8, v0)
        putInt(start + i * 8 + 4, v1)
    }
    return this
}

fun ByteBuffer.getWhirlpoolDigest(): ByteArray {
    val bytes = ByteArray(limit())
    get(bytes)
    return Whirlpool.whirlpool(bytes, 0, bytes.size)
}
