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
package io.github.runedata.cache.content.config

import io.github.runedata.cache.content.IndexType
import io.github.runedata.cache.filesystem.Archive
import io.github.runedata.cache.filesystem.CacheStore
import io.github.runedata.cache.filesystem.util.getMedium
import io.github.runedata.cache.filesystem.util.getUnsignedByte
import io.github.runedata.cache.filesystem.util.getUnsignedShort
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

class SequenceDefinition(id: Int) : ConfigEntry(id) {
    var frameIds: IntArray? = null
    var field3048: IntArray? = null
    var frameLengths: IntArray? = null
    var rightHandItem = -1
    var interleaveLeave: IntArray? = null
    var stretches = false
    var forcedPriority = 5
    var maxLoops = 99
    var field3056: IntArray? = null
    var precedenceAnimating = -1
    var leftHandItem = -1
    var replyMode = 2
    var frameStep = -1
    var priority = -1

    override fun decode(buffer: ByteBuffer): SequenceDefinition {
        while (true) {
            val opcode = buffer.getUnsignedByte()
            when (opcode) {
                0 -> {
                    post()
                    return this
                }
                1 -> {
                    val length = buffer.getUnsignedShort()
                    frameLengths = IntArray(length) { buffer.getUnsignedShort() }
                    frameIds = IntArray(length)
                    for (index in 0 until length) {
                        frameIds!![index] = buffer.getUnsignedShort()
                    }
                    for (index in 0 until length) {
                        frameIds!![index] += buffer.getUnsignedShort() shl 16
                    }
                }
                2 -> frameStep = buffer.getUnsignedShort()
                3 -> {
                    val length = buffer.getUnsignedByte()
                    interleaveLeave = IntArray(length + 1) {
                        if (it == length) 9999999
                        else buffer.getUnsignedByte()
                    }
                }
                4 -> stretches = true
                5 -> forcedPriority = buffer.getUnsignedByte()
                6 -> leftHandItem = buffer.getUnsignedShort()
                7 -> rightHandItem = buffer.getUnsignedShort()
                8 -> maxLoops = buffer.getUnsignedByte()
                9 -> precedenceAnimating = buffer.getUnsignedByte()
                10 -> priority = buffer.getUnsignedByte()
                11 -> replyMode = buffer.getUnsignedByte()
                12 -> {
                    val length = buffer.getUnsignedByte()
                    field3048 = IntArray(length)
                    for (index in 0 until length) {
                        field3048!![index] = buffer.getUnsignedShort()
                    }
                    for (index in 0 until length) {
                        field3048!![index] += buffer.getUnsignedShort() shl 16
                    }
                }
                13 -> {
                    val length = buffer.getUnsignedByte()
                    field3056 = IntArray(length) { buffer.getMedium() }
                }
                else -> error(opcode)
            }
        }
    }

    private fun post() {
        if (precedenceAnimating == -1) {
            precedenceAnimating = if (interleaveLeave != null) {
                2
            } else {
                0
            }
        }
        if (priority == -1) {
            priority = if (interleaveLeave != null) {
                2
            } else {
                0
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.qualifiedName)

        private const val ARCHIVE_INDEX = 12

        fun load(store: CacheStore): Map<Int, SequenceDefinition> {
            val refTable = store.getReferenceTable(IndexType.CONFIGS.id)
            val entry = refTable.getEntry(ARCHIVE_INDEX)
            val archive = Archive.decode(store.read(
                IndexType.CONFIGS.id,
                ARCHIVE_INDEX
            ).data,
                    refTable.getEntry(ARCHIVE_INDEX)!!.amountOfChildren
            )

            var defCount = 0
            val seqDefs = mutableMapOf<Int, SequenceDefinition>()
            for(id in 0 until entry!!.capacity) {
                val child = entry.getEntry(id) ?: continue
                seqDefs[id] = SequenceDefinition(id).decode(archive.getEntry(child.index))
                defCount++
            }
            logger.info("Loaded $defCount sequence definitions")
            return seqDefs
        }
    }
}
