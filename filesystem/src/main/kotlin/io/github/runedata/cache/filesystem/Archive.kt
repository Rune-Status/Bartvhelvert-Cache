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

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

/** An [Archive] is a file within the cache that can have multiple member files inside it. */
class Archive(val size: Int, val entries: Array<ByteBuffer?>) {
    fun encode(): ByteBuffer {
        val bout = ByteArrayOutputStream()
        val os = DataOutputStream(bout)
        os.use {
            for (id in entries.indices) {
                val temp = ByteArray(entries[id]!!.limit())
                entries[id]!!.position(0)
                try {
                    entries[id]!!.get(temp)
                } finally {
                    entries[id]!!.position(0)
                }
                it.write(temp)
            }

            var prev = 0
            for (id in entries.indices) {
                val chunkSize = entries[id]!!.limit()
                it.writeInt(chunkSize - prev)
                prev = chunkSize
            }
            bout.write(1)

            // wrap the bytes from the stream in a buffer
            val bytes = bout.toByteArray()
            return ByteBuffer.wrap(bytes)
        }
    }

    fun getEntry(id: Int): ByteBuffer {
        return entries[id]!!
    }

    fun putEntry(id: Int, buffer: ByteBuffer) {
        entries[id] = buffer
    }

    fun size() = entries.size

    companion object {
        /** Decodes the specified [ByteBuffer] into an [Archive]. */
        fun decode(buffer: ByteBuffer, size: Int): Archive {
            buffer.position(buffer.limit() - 1)
            val chunks = buffer.get().toInt() and 0xFF

            val chunkSizes = Array(chunks) { IntArray(size) }
            val sizes = IntArray(size)
            buffer.position(buffer.limit() - 1 - chunks * size * 4)
            for (chunk in 0 until chunks) {
                var chunkSize = 0
                for (id in 0 until size) {
                    val delta = buffer.int
                    chunkSize += delta
                    chunkSizes[chunk][id] = chunkSize
                    sizes[id] += chunkSize
                }
            }

            val entries = Array<ByteBuffer?>(size) {
                ByteBuffer.allocate(sizes[it])
            }

            buffer.position(0)
            for (chunk in 0 until chunks) {
                for (id in 0 until size) {
                    val chunkSize = chunkSizes[chunk][id]
                    val temp = ByteArray(chunkSize)
                    buffer.get(temp)
                    entries[id]!!.put(temp)
                }
            }

            for (id in 0 until size) {
                entries[id]!!.flip()
            }
            return Archive(size, entries)
        }
    }
}
