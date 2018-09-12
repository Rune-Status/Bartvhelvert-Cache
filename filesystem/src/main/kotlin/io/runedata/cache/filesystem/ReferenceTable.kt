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

import io.runedata.cache.filesystem.util.getSmartInt
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*

/**
 * Holds details for all the files with a single indexId, such as checksums, versions and Archive members. There are also
 * optional fields for identifier hashes and whirlpool digests.
 */
class ReferenceTable(
    var format: Int,
    var version: Int,
    var flags: Int,
    val entries: MutableMap<Int, Entry>,
    var identifiers: Identifiers? = null
) {
    fun capacity(): Int = if (entries.isEmpty()) 0 else entries.keys.last() + 1

    fun getEntry(id: Int): Entry? {
        return entries[id]
    }

    fun putEntry(id: Int, entry: Entry) {
        entries[id] = entry
    }

    fun getChildEntry(id: Int, child: Int): ChildEntry? {
        val entry = entries[id] ?: return null
        return entry.getEntry(child)
    }

    fun getArchiveSize(): Int {
        var sum: Long = 0
        for (i in 0 until capacity()) {
            val e = entries[i]
            if (e != null) {
                sum += e.uncompressed.toLong()
            }
        }
        return sum.toInt()
    }

    class Entry(val index: Int) {
        var identifier = -1

        var crc: Int = 0

        var compressed: Int = 0

        var uncompressed: Int = 0

        var hash: Int = 0

        var version: Int = 0

        var whirlpool = ByteArray(64)
            set(whirlpool) {
                require(whirlpool.size == 64)
                System.arraycopy(whirlpool, 0, this.whirlpool, 0, whirlpool.size)
            }

        var identifiers: Identifiers? = null

        val childEntries = TreeMap<Int, ChildEntry>()

        val capacity get() = if (childEntries.isEmpty()) 0 else childEntries.lastKey() + 1

        val amountOfChildren get() = childEntries.size

        fun getEntry(id: Int) = childEntries[id]

        fun putEntry(id: Int, entry: ChildEntry) {
            childEntries[id] = entry
        }

        fun removeEntry(id: Int) = childEntries.remove(id)
    }

    class ChildEntry(val index: Int) {
        var identifier = -1
    }

    class Identifiers(identifiers: IntArray) {
        private val table: IntArray

        init {
            val length = identifiers.size
            val halfLength = identifiers.size shr 1

            var size = 1
            var mask = 1
            run {
                var i = 1
                while (i <= length + halfLength) {
                    mask = i
                    size = i shl 1
                    i = i shl 1
                }
            }

            mask = mask shl 1
            size = size shl 1

            table = IntArray(size)

            Arrays.fill(table, -1)

            for (id in identifiers.indices) {
                var i: Int
                i = identifiers[id] and mask - 1
                while (table[i + i + 1] != -1) {
                    i = i + 1 and mask - 1
                }

                table[i + i] = identifiers[id]
                table[i + i + 1] = id
            }
        }

        fun getFile(identifier: Int): Int {
            val mask = (table.size shr 1) - 1
            var i = identifier and mask

            while (true) {
                val id = table[i + i + 1]
                if (id == -1) {
                    return -1
                }

                if (table[i + i] == identifier) {
                    return id
                }

                i = i + 1 and mask
            }
        }

    }

    companion object {
        /** Indicates that a [ReferenceTable] contains identifiers */
        const val MASK_IDENTIFIERS = 0x01

        /** Indicates that a [ReferenceTable] contains whirlpool digests for its entries */
        const val MASK_WHIRLPOOL = 0x02

        /** Indicates that a [ReferenceTable] contains compression sizes.  */
        const val MASK_SIZES = 0x04

        /** Indicates that a [ReferenceTable] contains a indexId of hash.  */
        const val MASK_HASH = 0x08

        /** Decodes the slave checksum table contained in the specified[ByteBuffer]. */
        fun decode(buffer: ByteBuffer): ReferenceTable {
            val format = buffer.get().toInt() and 0xFF
            if (format < 5 || format > 7) throw IOException("Not a valid format")
            val version = if (format >= 6) buffer.int else 0
            val flags = buffer.get().toInt() and 0xFF
            var accumulator = 0
            var size = -1
            val entryIds = IntArray(if (format == 7) buffer.getSmartInt() else buffer.short.toInt() and 0xFFFF)
            for (i in entryIds.indices) {
                val delta = if (format == 7) buffer.getSmartInt() else buffer.short.toInt()and 0xFFFF
                accumulator += delta
                entryIds[i] = accumulator
                if (entryIds[i] > size) {
                    size = entryIds[i]
                }
            }
            size++

            val entries = mutableMapOf<Int, Entry>()
            var index = 0
            for (id in entryIds) {
                entries[id] = Entry(index++)
            }

            val identifiers = if (flags and MASK_IDENTIFIERS != 0) {
                Identifiers(buffer.decodeIdentifiers(size, entries))
            } else {
                null
            }
            buffer.decodeCrc(entries)
            if (flags and MASK_HASH != 0) buffer.decodeHash(entries)
            if (flags and MASK_WHIRLPOOL != 0) buffer.decodeWhirlPool(entries)
            if (flags and MASK_SIZES != 0) buffer.decodeSizes(entries)
            buffer.decodeVersions(entries)
            buffer.decodeChildEntries(format, size, flags, entries)
            return ReferenceTable(format, version, flags, entries, identifiers)
        }

        fun ByteBuffer.decodeChildEntries(format: Int, size: Int, flags: Int, entries: MutableMap<Int, Entry>) {
            val members = arrayOfNulls<IntArray>(size)
            for (id in entries.keys) {
                members[id] = IntArray(if (format >= 7) getSmartInt() else short.toInt() and 0xFFFF)
            }

            for (id in entries.keys) {
                var accumulator = 0
                var childSize = -1
                for (i in 0 until members[id]!!.size) {
                    val delta = if (format >= 7) getSmartInt() else short.toInt() and 0xFFFF
                    accumulator += delta
                    members[id]!![i] = accumulator
                    if (members[id]!![i] > childSize) {
                        childSize = members[id]!![i]
                    }
                }

                var index = 0
                for (child in members[id]!!) {
                    entries[id]!!.childEntries[child] = ChildEntry(index++)
                }
            }

            if (flags and MASK_IDENTIFIERS != 0) {
                for (id in entries.keys) {
                    val identifiersArray = IntArray(members[id]!!.size)
                    for (child in members[id]!!) {
                        val identifier = int
                        identifiersArray[child] = identifier
                        entries[id]!!.childEntries[child]!!.identifier = identifier
                    }
                    entries[id]!!.identifiers =
                            Identifiers(identifiersArray)
                }
            }
        }

        private fun ByteBuffer.decodeIdentifiers(size: Int, entries: MutableMap<Int, Entry>): IntArray {
            val identifiersArray = IntArray(size)
            for ((id, entry) in entries) {
                val identifier = int
                identifiersArray[id] = identifier
                entry.identifier = identifier
            }
            return identifiersArray
        }

        private fun ByteBuffer.decodeCrc(entries: MutableMap<Int, Entry>) {
            for (entry in entries.values) {
                entry.crc = int
            }
        }

        private fun ByteBuffer.decodeHash(entries: MutableMap<Int, Entry>) {
            for (entry in entries.values) {
                entry.hash = int
            }
        }

        private fun ByteBuffer.decodeWhirlPool(entries: MutableMap<Int, Entry>) {
            for (entry in entries.values) {
                get(entry.whirlpool)
            }
        }

        private fun ByteBuffer.decodeSizes(entries: MutableMap<Int, Entry>) {
            for (entry in entries.values) {
                entry.compressed = int
                entry.uncompressed = int
            }
        }

        private fun ByteBuffer.decodeVersions(entries: MutableMap<Int, Entry>) {
            for (entry in entries.values) {
                entry.version = int
            }
        }
    }
}
