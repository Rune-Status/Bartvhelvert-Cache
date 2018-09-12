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

import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.ArrayList

/** Serves as an access point to the underlying filesystem. */
class FileStore(
    private val dataChannel: FileChannel,
    private val indexChannels: Array<FileChannel>,
    private val metaChannel: FileChannel
) : Closeable {
    /** Reads an archive from this [FileStore] */
    fun readArchive(indexFileId: Int, archiveId: Int) = readArchive(readIndex(indexFileId, archiveId))

    /** Reads an index from this [FileStore] */
    fun readIndex(indexFileId: Int, archiveId: Int): Index {
        if ((indexFileId < 0 || indexFileId >= indexChannels.size) && indexFileId != 255) throw FileNotFoundException()
        val indexFileChannel = if (indexFileId == 255) metaChannel else indexChannels[indexFileId]
        val ptr = archiveId.toLong() * Index.SIZE.toLong()
        if (ptr < 0 || ptr >= indexFileChannel.size()) throw FileNotFoundException()
        val buffer = ByteBuffer.allocate(Index.SIZE)
        indexFileChannel.readFully(buffer, ptr)
        return Index.decode(
            indexFileId,
            archiveId,
            buffer.flip() as ByteBuffer
        )
    }

    /** Reads an archive from this [FileStore] */
    fun readArchive(index: Index): ByteBuffer {
        val rawArchiveData = ByteBuffer.allocate(index.size)
        var amountOfSegmentsRead = 0
        var dataLeftToRead = index.size
        var nextSegmentId = index.startSector.toLong() * DataSegment.SIZE.toLong()
        val tempBuffer = ByteBuffer.allocate(DataSegment.SIZE)
        do {
            tempBuffer.clear()
            dataChannel.readFully(tempBuffer, nextSegmentId)
            val dataSegment = DataSegment.decode(
                index.archiveId,
                tempBuffer.flip() as ByteBuffer
            )
            if (dataLeftToRead > dataSegment.data.size) {
                dataSegment.validate(index.indexFileId, index.archiveId, amountOfSegmentsRead)
                amountOfSegmentsRead++
                rawArchiveData.put(dataSegment.data, 0, dataSegment.data.size)
                dataLeftToRead -= dataSegment.data.size
                nextSegmentId = dataSegment.nextSegment.toLong() * DataSegment.SIZE.toLong()
            } else {
                rawArchiveData.put(dataSegment.data, 0, dataLeftToRead)
                dataLeftToRead = 0
            }
        } while (dataLeftToRead > 0)
        return rawArchiveData.flip() as ByteBuffer
    }

    /** Reads everything in the [FileChannel] starting from [startPosition] */
    private fun FileChannel.readFully(buffer: ByteBuffer, startPosition: Long) {
        var ptr = startPosition
        while (buffer.remaining() > 0) {
            val read = read(buffer, ptr).toLong()
            if (read < -1) {
                throw EOFException()
            } else {
                ptr += read
            }
        }
    }

    fun hasData(): Boolean {
        return dataChannel.size() > 0
    }

    fun getIndexEntries(type: Int): Int {
        if ((type < 0 || type >= indexChannels.size) && type != 255) {
            throw FileNotFoundException()
        }
        return if (type == 255) {
            (metaChannel.size() / Index.SIZE).toInt()
        } else {
            (indexChannels[type].size() / Index.SIZE).toInt()
        }
    }

    fun getIndexFileCount(): Int {
        return indexChannels.size
    }

    override fun close() {
        dataChannel.close()
        for (channel in indexChannels) {
            channel.close()
        }
        metaChannel.close()
    }

    companion object {
        const val DEFAULT_FILE_NAME = "main_file_cache"
        const val DATA_FILE_EXTENSION = ".dat2"
        const val INDEX_FILE_EXTENSION = ".idx"
        const val METACHANNEL_INDEX_ID = 255

        /** Opens a [FileStore] */
        fun open(root: String): FileStore {
            return open(File(root))
        }

        /** Opens a [FileStore] */
        fun open(root: File): FileStore {
            val data = File(root, "$DEFAULT_FILE_NAME$DATA_FILE_EXTENSION")
            if (!data.exists()) throw FileNotFoundException()
            var raf = RandomAccessFile(data, "rw")
            val dataChannel = raf.channel

            val indexChannels = ArrayList<FileChannel>()
            for (i in 0..253) {
                val index = File(root, "$DEFAULT_FILE_NAME$INDEX_FILE_EXTENSION$i")
                if (!index.exists()) break
                raf = RandomAccessFile(index, "rw")
                val indexChannel = raf.channel
                indexChannels.add(indexChannel)
            }
            if (indexChannels.isEmpty()) throw FileNotFoundException()

            val meta = File(root, "$DEFAULT_FILE_NAME$INDEX_FILE_EXTENSION$METACHANNEL_INDEX_ID")
            if (!meta.exists()) throw FileNotFoundException()

            raf = RandomAccessFile(meta, "rw")
            val metaChannel = raf.channel

            return FileStore(dataChannel, indexChannels.toTypedArray(), metaChannel)
        }
    }
}
