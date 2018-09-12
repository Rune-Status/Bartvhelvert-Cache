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

import io.runedata.cache.filesystem.util.getMedium
import io.runedata.cache.filesystem.util.putMedium
import java.io.IOException
import java.nio.ByteBuffer

/**
 * A segment of data consisting of 520 bytes. A [DataSegment] consists of a header + raw data. There are two types of
 * segments, normal and extended. The normal segements are used for lower segment ids and the extended segments are used
 * for higher segment ids. Data segments reside in the data file.
 */
class DataSegment(
    val indexId: Int,
    val archiveId: Int,
    val positionInArchive: Int,
    val nextSegment: Int,
    val data: ByteArray
) {
    val isExtended get() = isExtended(archiveId)

    fun encode(): ByteBuffer {
        val buffer = ByteBuffer.allocate(SIZE)
        if (archiveId > 0xFFFF) {
            buffer.putInt(archiveId)
        } else {
            buffer.putShort(archiveId.toShort())
        }
        buffer.putShort(positionInArchive.toShort())
        buffer.putMedium(nextSegment)
        buffer.put(indexId.toByte())
        buffer.put(data)
        return buffer.flip() as ByteBuffer
    }

    fun validate(indexId: Int, archiveId: Int, chunk: Int) {
        if (this.indexId != indexId) throw IOException("Index id mismatch")
        if (this.archiveId != archiveId) throw IOException("Archive id mismatch")
        if (this.positionInArchive != chunk) throw IOException("Chunk id mismatch")
    }

    companion object {
        const val HEADER_SIZE = 8

        const val DATA_SIZE = 512

        const val EXTENDED_DATA_SIZE = 510

        const val EXTENDED_HEADER_SIZE = 10

        const val SIZE = HEADER_SIZE + DATA_SIZE

        fun isExtended(archiveId: Int) = archiveId > 0xFFFF

        fun decode(archiveId: Int, buffer: ByteBuffer): DataSegment = if(isExtended(
                archiveId
            )
        ) {
            decodeExtended(buffer)
        } else {
            decode(buffer)
        }

        fun decode(buffer: ByteBuffer): DataSegment {
            val id = buffer.short.toInt() and 0xFFFF
            val chunk = buffer.short.toInt() and 0xFFFF
            val nextSector = buffer.getMedium()
            val type = buffer.get().toInt() and 0xFF
            val data = ByteArray(DATA_SIZE)
            buffer.get(data)
            return DataSegment(type, id, chunk, nextSector, data)
        }

        fun decodeExtended(buffer: ByteBuffer): DataSegment {
            val id = buffer.int
            val chunk = buffer.short.toInt() and 0xFFFF
            val nextSector = buffer.getMedium()
            val type = buffer.get().toInt() and 0xFF
            val data = ByteArray(EXTENDED_DATA_SIZE)
            buffer.get(data)
            return DataSegment(type, id, chunk, nextSector, data)
        }
    }
}
