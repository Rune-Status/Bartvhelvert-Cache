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
package io.runedata.cache.filesystem

import io.runedata.cache.filesystem.compression.algorithm.Bzip2
import io.runedata.cache.filesystem.compression.algorithm.Gzip2
import io.runedata.cache.filesystem.crypto.Xtea
import io.runedata.cache.filesystem.util.getByteArray
import io.runedata.cache.filesystem.util.toByteArray
import io.runedata.cache.filesystem.util.xteaDecipher
import io.runedata.cache.filesystem.util.xteaEncipher
import java.io.IOException
import java.nio.ByteBuffer

/**
 * A [ArchiveRaw] holds an optionally compressed file. This class can be used to decompress and compress containers. A
 * container can also have a two byte trailer which specifies the version of the file within it.
 */
class ArchiveRaw(val compressionType: Int, val data: ByteBuffer, var version: Int = -1)  {
    fun encode(): ByteBuffer {
        return encode(Xtea.NULL_KEYS)
    }

    fun encode(xteaKeys: IntArray): ByteBuffer {
        val compressedData = data.compress()
        val headerSize = 5 + (if (compressionType == COMPRESSION_NONE) 0 else 4) + if (isVersioned()) 2 else 0
        val buffer = ByteBuffer.allocate(headerSize + compressedData.size)
        buffer.put(compressionType.toByte())
        buffer.putInt(compressedData.size)
        if (compressionType != COMPRESSION_NONE) {
            buffer.putInt(data.limit())
        }
        buffer.put(compressedData)
        if (isVersioned()) {
            buffer.putShort(version.toShort())
        }
        return buffer.encryptCompressedData(xteaKeys, compressedData.size).flip() as ByteBuffer
    }

    private fun ByteBuffer.compress(): ByteArray {
        return when (compressionType) {
            COMPRESSION_NONE -> toByteArray()
            COMPRESSION_GZIP -> Gzip2.zip(toByteArray())
            COMPRESSION_BZIP2 -> Bzip2.zip(toByteArray())
            else -> throw IOException("Invalid compression")
        }
    }

    private fun ByteBuffer.encryptCompressedData(xteaKeys: IntArray, compressedLength: Int): ByteBuffer {
        return if (xteaKeys.all { it != 0 }) {
            xteaEncipher(xteaKeys, start = 5, end = compressedLength + if (compressionType == COMPRESSION_NONE) 5 else 9)
        } else {
            return this
        }
    }

    fun isVersioned(): Boolean {
        return version != -1
    }

    fun removeVersion() {
        this.version = -1
    }

    companion object {
        const val COMPRESSION_NONE = 0
        const val COMPRESSION_BZIP2 = 1
        const val COMPRESSION_GZIP = 2

        fun decode(buffer: ByteBuffer, xteaKeys: IntArray = Xtea.NULL_KEYS): ArchiveRaw {
            val compressionType = buffer.get().toInt() and 0xFF
            val compressedSize = buffer.int
            buffer.decryptCompressedData(xteaKeys, compressionType, compressedSize)

            if (compressionType == COMPRESSION_NONE) {
                val data = ByteBuffer.wrap(buffer.getByteArray(compressedSize))
                val version = decodeVersion(buffer)
                return ArchiveRaw(compressionType, data, version)
            } else {
                val uncompressedSize = buffer.int
                val uncompressed = when (compressionType) {
                    COMPRESSION_BZIP2 -> Bzip2.unzip(buffer.getByteArray(compressedSize))
                    COMPRESSION_GZIP -> Gzip2.unzip(buffer.getByteArray(compressedSize))
                    else -> throw IOException("Invalid compression indexId")
                }
                if (uncompressed.size != uncompressedSize) {
                    throw IOException("Uncompressed size mismatch")
                }
                val version = decodeVersion(buffer)
                return ArchiveRaw(compressionType, ByteBuffer.wrap(uncompressed), version)
            }
        }

        private fun decodeVersion(buffer: ByteBuffer) = if (buffer.remaining() >= 2) {
            buffer.short.toInt()
        } else {
            -1
        }

        private fun ByteBuffer.decryptCompressedData(xteaKeys: IntArray, compressionType: Int, compressedLength: Int): ByteBuffer {
            return if (xteaKeys.all { it != 0 }) {
                xteaDecipher(xteaKeys, 5, compressedLength + if (compressionType == COMPRESSION_NONE) 5 else 9)
            } else {
                this
            }
        }
    }
}
