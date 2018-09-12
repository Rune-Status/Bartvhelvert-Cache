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

import io.runedata.cache.filesystem.util.getCrcChecksum
import io.runedata.cache.filesystem.crypto.Djb2
import io.runedata.cache.filesystem.crypto.Xtea
import io.runedata.cache.filesystem.util.getWhirlpoolDigest
import java.io.Closeable
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.util.HashMap

/** Serves as an API access point for editing the cache */
class CacheStore(val fileStore: FileStore, val xteas: Map<Int, IntArray>) : Closeable {
    val referenceTables = Array(fileStore.getIndexFileCount()) { indexFileId ->
        ReferenceTable.decode(
            ArchiveRaw.decode(
                fileStore.readArchive(255, indexFileId),
                Xtea.NULL_KEYS
            ).data
        )
    }

    /** Reads raw uncompressed and decrypted data from the cache */
    fun read(indexFileId: Int, archiveId: Int, xteaKeys: IntArray = Xtea.NULL_KEYS): ArchiveRaw {
        if (indexFileId == 255) throw IOException("Reference tables can only be readArchive with the low level FileStore API!")
        return ArchiveRaw.decode(
            fileStore.readArchive(indexFileId, archiveId),
            xteaKeys
        )
    }

    /** Reads a archiveId contained in an Archive in the cache. */
    fun read(indexFileId: Int, archiveId: Int, archiveMember: Int, xteaKeys: IntArray = Xtea.NULL_KEYS): ByteBuffer? {
        val entry = referenceTables[indexFileId].getEntry(archiveId)
        if (archiveMember < 0 || archiveMember >= entry!!.capacity) throw FileNotFoundException()
        val rawArchive = read(indexFileId, archiveId, xteaKeys)
        val archive = Archive.decode(rawArchive.data, entry.capacity)
        return archive.getEntry(archiveMember)
    }

    /** Computes the [ChecksumTable] for this cache. The checksum table forms part of the so-called "update keys". */
    fun createChecksumTable(): ChecksumTable {
        val indexFileCount = fileStore.getIndexFileCount()
        val table = ChecksumTable(indexFileCount)
        for (indexFileId in 0 until indexFileCount) {
            var crc = 0
            var version = 0
            var files = 0
            var archiveSize = 0
            var whirlpool = ByteArray(64)
            if (fileStore.hasData()) {
                val buf = fileStore.readArchive(255, indexFileId)
                if (buf.limit() > 0) {
                    val ref = referenceTables[indexFileId]
                    crc = buf.getCrcChecksum()
                    version = ref.version
                    files = ref.capacity()
                    archiveSize = ref.getArchiveSize()
                    buf.position(0)
                    whirlpool = buf.getWhirlpoolDigest()
                }
            }
            table.setEntry(indexFileId,
                ChecksumTable.Entry(crc, version, files, archiveSize, whirlpool)
            )
        }

        /* return the table */
        return table
    }

    /** Gets the number of files of the specified indexId. */
    fun getFileCount(type: Int): Int {
        return fileStore.getIndexEntries(type)
    }

    /** Gets the number of index files, not including the meta index file. */
    fun getTypeCount(): Int {
        return fileStore.getIndexFileCount()
    }

    /** Gets the reference table by indexId index. */
    fun getReferenceTable(type: Int): ReferenceTable {
        return referenceTables[type]
    }

    /** Gets a file archiveId from the cache by name. */
    fun getFileId(type: Int, name: String): Int {
        if (!identifiers.containsKey(name)) {
            val table = referenceTables[type]
            identifiers[name] = table.identifiers!!.getFile(Djb2.hash(name))
        }
        val i = identifiers[name]
        return i ?: -1
    }

    override fun close() {
        fileStore.close()
    }

    companion object {
        /** Strings used to identify an [Archive] */
        private val identifiers = HashMap<String, Int>()
    }
}
