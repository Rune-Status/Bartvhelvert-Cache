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
import io.github.runedata.cache.filesystem.util.getUnsignedByte
import io.github.runedata.cache.filesystem.util.getUnsignedShort
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

class SpotAnimDefinition(id: Int) : ConfigEntry(id) {
    var rotation = 0
    var textureReplace: ShortArray? = null
    var textureFind: ShortArray? = null
    var resizeY = 128
    var animationId = -1
    var colorFind: ShortArray? = null
    var colorReplace: ShortArray? = null
    var resizeX = 128
    var modelId: Int = 0
    var ambient = 0
    var contrast = 0

    override fun decode(buffer: ByteBuffer): SpotAnimDefinition {
        while (true) {
            val opcode = buffer.getUnsignedByte()
            when (opcode) {
                0 -> return this
                1 -> modelId = buffer.getUnsignedShort()
                2 -> animationId = buffer.getUnsignedShort()
                4 -> resizeX = buffer.getUnsignedShort()
                5 -> resizeY = buffer.getUnsignedShort()
                6 -> rotation = buffer.getUnsignedShort()
                7 -> ambient = buffer.getUnsignedByte()
                8 -> contrast = buffer.getUnsignedByte()
                40 -> {
                    val colors = buffer.getUnsignedByte()
                    colorFind = ShortArray(colors)
                    colorReplace = ShortArray(colors)
                    for (i in 0 until colors) {
                        colorFind!![i] = buffer.short
                        colorReplace!![i] = buffer.short
                    }
                }
                41 -> {
                    val textures = buffer.getUnsignedByte()
                    textureFind = ShortArray(textures)
                    textureReplace = ShortArray(textures)
                    for (i in 0 until textures) {
                        textureFind!![i] = buffer.short
                        textureReplace!![i] = buffer.short
                    }
                }
                else -> error(opcode)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.qualifiedName)

        private const val ARCHIVE_INDEX = 13

        fun load(store: CacheStore): Map<Int, SpotAnimDefinition> {
            val refTable = store.getReferenceTable(IndexType.CONFIGS.id)
            val entry = refTable.getEntry(ARCHIVE_INDEX)
            val archive = Archive.decode(store.read(
                IndexType.CONFIGS.id,
                ARCHIVE_INDEX
            ).data,
                    refTable.getEntry(ARCHIVE_INDEX)!!.amountOfChildren
            )

            var defCount = 0
            val spotAnimDefs = mutableMapOf<Int, SpotAnimDefinition>()
            for(id in 0 until entry!!.capacity) {
                val child = entry.getEntry(id) ?: continue
                spotAnimDefs[id] = SpotAnimDefinition(id).decode(archive.getEntry(child.index))
                defCount++
            }
            logger.info("Loaded $defCount spot animation definitions")
            return spotAnimDefs
        }
    }
}
