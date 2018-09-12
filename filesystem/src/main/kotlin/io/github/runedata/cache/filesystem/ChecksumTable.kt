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
package io.github.runedata.cache.filesystem

import io.github.runedata.cache.filesystem.crypto.Rsa
import io.github.runedata.cache.filesystem.crypto.Whirlpool
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer


class ChecksumTable(val size: Int) {
    private var entries: Array<Entry?> = arrayOfNulls(size)

    fun encode(): ByteBuffer {
        return encode(false)
    }

    fun encode(whirlpool: Boolean): ByteBuffer {
        return encode(whirlpool, null, null)
    }

    fun encode(whirlpool: Boolean, modulus: BigInteger?, privateKey: BigInteger?): ByteBuffer {
        val bout = ByteArrayOutputStream()
        val os = DataOutputStream(bout)
        os.use { it ->
            if (whirlpool) {
                it.write(entries.size)
            }

            for (i in entries.indices) {
                val entry = entries[i]
                it.writeInt(entry!!.crc)
                it.writeInt(entry.version)
                if (whirlpool) {
                    it.writeInt(entry.fileCount)
                    it.writeInt(entry.size)
                    it.write(entry.whirlpool)
                }
            }

            if (whirlpool) {
                var bytes = bout.toByteArray()
                var temp = ByteBuffer.allocate(65)
                temp.put(0.toByte())
                temp.put(Whirlpool.whirlpool(bytes, 0, bytes.size))
                temp.flip()

                if (modulus != null && privateKey != null) {
                    temp = Rsa.crypt(temp, modulus, privateKey)
                }

                bytes = ByteArray(temp.limit())
                temp.get(bytes)
                it.write(bytes)
            }

            val bytes = bout.toByteArray()
            return ByteBuffer.wrap(bytes)
        }
    }

    fun getEntrySize() = entries.size

    fun setEntry(id: Int, entry: Entry) {
        if (id < 0 || id >= entries.size) {
            throw IndexOutOfBoundsException()
        }
        entries[id] = entry
    }

    fun getEntry(id: Int): Entry {
        if (id < 0 || id >= entries.size) {
            throw IndexOutOfBoundsException()
        }
        return entries[id]!!
    }

    class Entry(val crc: Int, val version: Int, val fileCount: Int, val size: Int, val whirlpool: ByteArray) {
        init {
            require(whirlpool.size == 64)
        }
    }

    companion object {
        fun decode(buffer: ByteBuffer): ChecksumTable {
            return decode(buffer, false)
        }

        fun decode(buffer: ByteBuffer, whirlpool: Boolean): ChecksumTable {
            return decode(buffer, whirlpool, null, null)
        }

        fun decode(buffer: ByteBuffer, whirlpool: Boolean, mod: BigInteger?, publicKey: BigInteger?): ChecksumTable {
            val size = if (whirlpool) buffer.get().toInt() and 0xFF else buffer.limit() / 8
            val table = ChecksumTable(size)
            var masterDigest: ByteArray? = null
            if (whirlpool) {
                val temp = ByteArray(size * 80 + 1)
                buffer.position(0)
                buffer.get(temp)
                masterDigest = Whirlpool.whirlpool(temp, 0, temp.size)
            }

            buffer.position(if (whirlpool) 1 else 0)
            for (i in 0 until size) {
                val crc = buffer.int
                val version = buffer.int
                val files = if (whirlpool) buffer.int else 0
                val archiveSize = if (whirlpool) buffer.int else 0
                val digest = ByteArray(64)
                if (whirlpool) {
                    buffer.get(digest)
                }
                table.entries[i] =
                        Entry(
                            crc,
                            version,
                            files,
                            archiveSize,
                            digest
                        )
            }

            if (whirlpool) {
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                var temp = ByteBuffer.wrap(bytes)

                if (mod != null && publicKey != null) {
                    temp = Rsa.crypt(buffer, mod, publicKey)
                }

                if (temp.limit() != 65) throw IOException("Decrypted data length mismatch")
                for (i in 0..63) {
                    if (temp.get(i + 1) != masterDigest!![i]) throw IOException("Whirlpool digest mismatch")
                }
            }
            return table
        }
    }
}
