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

import io.github.runedata.cache.filesystem.util.getMedium
import io.github.runedata.cache.filesystem.util.putMedium
import java.nio.ByteBuffer

/** An entry of an index file. An [Index] is a reference to an entry in the data file. */
class Index(val indexFileId: Int, val archiveId: Int, val size: Int, val startSector: Int) {
    fun encode(): ByteBuffer {
        val buffer = ByteBuffer.allocate(SIZE)
        buffer.putMedium(size)
        buffer.putMedium(startSector)
        return buffer.flip() as ByteBuffer
    }

    companion object {
        const val SIZE = 6

        fun decode(indexFileId: Int, archiveId: Int, buffer: ByteBuffer): Index {
            require(buffer.remaining() == SIZE)
            val size = buffer.getMedium()
            val sector = buffer.getMedium()
            return Index(indexFileId, archiveId, size, sector)
        }
    }
}
