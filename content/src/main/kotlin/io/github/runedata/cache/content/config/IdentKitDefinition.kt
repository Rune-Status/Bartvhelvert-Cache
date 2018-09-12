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

class IdentKitDefinition(id: Int) : ConfigEntry(id) {
    var colorFind: ShortArray? = null
    var colorReplace: ShortArray? = null
    var textureFind: ShortArray? = null
    var textureReplace: ShortArray? = null
    var bodyPartId = -1
    var modelIds: IntArray? = null
    var models = intArrayOf(-1, -1, -1, -1, -1)
    var nonSelectable = false

    override fun decode(buffer: ByteBuffer): IdentKitDefinition {
        while (true) {
            val opcode = buffer.getUnsignedByte()
            when (opcode) {
                0 -> return this
                1 -> bodyPartId = buffer.getUnsignedByte()
                2 -> {
                    val length = buffer.getUnsignedByte()
                    modelIds = IntArray(length) { buffer.getUnsignedShort() }
                }
                3 -> nonSelectable = true
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
                in 60..69 -> models[opcode - 60] = buffer.getUnsignedShort()
                else -> error(opcode)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.qualifiedName)

        private const val ARCHIVE_INDEX = 3

        fun load(store: CacheStore): Map<Int, IdentKitDefinition> {
            val refTable = store.getReferenceTable(IndexType.CONFIGS.id)
            val entry = refTable.getEntry(ARCHIVE_INDEX)
            val archive = Archive.decode(store.read(
                IndexType.CONFIGS.id,
                ARCHIVE_INDEX
            ).data,
                    refTable.getEntry(ARCHIVE_INDEX)!!.amountOfChildren
            )

            var defCount = 0
            val idkDefs = mutableMapOf<Int, IdentKitDefinition>()
            for(id in 0 until entry!!.capacity) {
                val child = entry.getEntry(id) ?: continue
                idkDefs[id] = IdentKitDefinition(id).decode(archive.getEntry(child.index))
                defCount++
            }
            logger.info("Loaded $defCount identity kit definitions")
            return idkDefs
        }
    }
}