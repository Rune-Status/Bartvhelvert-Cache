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
import io.github.runedata.cache.filesystem.util.getLargeSmart
import io.github.runedata.cache.filesystem.util.getUnsignedByte
import io.github.runedata.cache.filesystem.util.getUnsignedShort
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

class HitbarDefinition(id: Int) : ConfigEntry(id) {
    var field3310 = 255
    var field3307 = 255
    var field3312 = -1
    var field3308 = 1
    var field3313 = 70
    var field3315 = -1
    var field3316 = -1
    var healthScale = 30
    var field3318 = 0

    override fun decode(buffer: ByteBuffer): HitbarDefinition {
        while (true) {
            val opcode = buffer.getUnsignedByte()
            when(opcode) {
                0 -> return this
                1 -> buffer.getUnsignedShort()
                2 -> field3310 = buffer.getUnsignedByte()
                3 -> field3307 = buffer.getUnsignedByte()
                4 -> field3312 = 0
                5 -> field3313 = buffer.getUnsignedShort()
                6 -> buffer.getUnsignedByte()
                7 -> field3315 = buffer.getLargeSmart()
                8 -> field3316 = buffer.getLargeSmart()
                11 -> field3312 = buffer.getUnsignedShort()
                14 -> healthScale = buffer.getUnsignedByte()
                15 -> field3318 = buffer.getUnsignedByte()
                else -> error(opcode)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.qualifiedName)

        private const val ARCHIVE_INDEX = 33

        fun load(store: CacheStore): Map<Int, HitbarDefinition> {
            val refTable = store.getReferenceTable(IndexType.CONFIGS.id)
            val entry = refTable.getEntry(ARCHIVE_INDEX)
            val archive = Archive.decode(store.read(
                IndexType.CONFIGS.id,
                ARCHIVE_INDEX
            ).data,
                refTable.getEntry(ARCHIVE_INDEX)!!.amountOfChildren
            )

            var defCount = 0
            val hitBarDefs = mutableMapOf<Int, HitbarDefinition>()
            for(id in 0 until entry!!.capacity) {
                val child = entry.getEntry(id) ?: continue
                hitBarDefs[id] = HitbarDefinition(id).decode(archive.getEntry(child.index))
                defCount++
            }
            logger.info("Loaded $defCount hitbar definitions")
            return hitBarDefs
        }
    }

}