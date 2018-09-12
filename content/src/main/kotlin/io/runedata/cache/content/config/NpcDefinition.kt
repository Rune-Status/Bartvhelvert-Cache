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
package io.runedata.cache.content.config

import io.runedata.cache.content.IndexType
import io.runedata.cache.filesystem.Archive
import io.runedata.cache.filesystem.CacheStore
import io.runedata.cache.filesystem.util.getParams
import io.runedata.cache.filesystem.util.getString
import io.runedata.cache.filesystem.util.getUnsignedByte
import io.runedata.cache.filesystem.util.getUnsignedShort
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

class NpcDefinition(id: Int) : ConfigEntry(id) {
    var colorFind: IntArray? = null
    var anInt2156 = 32
    var name = "null"
    var colorReplace: IntArray? = null
    var models: IntArray? = null
    var models2: IntArray? = null
    var stanceAnimation = -1
    var anInt2165 = -1
    var size = 1
    var walkAnimation = -1
    var textureReplace: IntArray? = null
    var rotate90RightAnimation = -1
    var isClickable = true
    var resizeX = 128
    var contrast = 0
    var rotate180Animation = -1
    var varpIndex = -1
    var options = arrayOfNulls<String>(5)
    var drawMapDot = true
    var combatLevel = -1
    var rotate90LeftAnimation = -1
    var resizeY = 128
    var hasRenderPriority = false
    var ambient = 0
    var headIcon = -1
    var configs: IntArray? = null
    var textureFind: IntArray? = null
    var varp32Index = -1
    var isInteractable = true
    var anInt2189 = -1
    var aBool2190 = false
    var params: MutableMap<Int, Any>? = null

    override fun decode(buffer: ByteBuffer): NpcDefinition {
        while (true) {
            val opcode = buffer.getUnsignedByte()
            when (opcode) {
                0 -> return this
                1 -> {
                    val length = buffer.getUnsignedByte()
                    models = IntArray(length) { buffer.getUnsignedShort() }
                }
                2 -> name = buffer.getString()
                12 -> size = buffer.getUnsignedByte()
                13 -> stanceAnimation = buffer.getUnsignedShort()
                14 -> walkAnimation = buffer.getUnsignedShort()
                15 -> anInt2165 = buffer.getUnsignedShort()
                16 -> anInt2189 = buffer.getUnsignedShort()
                17 -> {
                    walkAnimation = buffer.getUnsignedShort()
                    rotate180Animation = buffer.getUnsignedShort()
                    rotate90RightAnimation = buffer.getUnsignedShort()
                    rotate90LeftAnimation = buffer.getUnsignedShort()
                }
                in 30..34 -> options[opcode - 30] = buffer.getString().takeIf { it != "Hidden" }
                40 -> {
                    val colors = buffer.getUnsignedByte()
                    colorFind = IntArray(colors)
                    colorReplace = IntArray(colors)
                    for (i in 0 until colors) {
                        colorFind!![i] = buffer.getUnsignedShort()
                        colorReplace!![i] = buffer.getUnsignedShort()
                    }
                }
                41 -> {
                    val textures = buffer.getUnsignedByte()
                    textureFind = IntArray(textures)
                    textureReplace = IntArray(textures)
                    for (i in 0 until textures) {
                        textureFind!![i] = buffer.getUnsignedShort()
                        textureReplace!![i] = buffer.getUnsignedShort()
                    }
                }
                60 -> {
                    val length = buffer.getUnsignedByte()
                    models2 = IntArray(length) { buffer.getUnsignedShort() }
                }
                93 -> drawMapDot = false
                95 -> combatLevel = buffer.getUnsignedShort()
                97 -> resizeX = buffer.getUnsignedShort()
                98 -> resizeY = buffer.getUnsignedShort()
                99 -> hasRenderPriority = true
                100 -> ambient = buffer.get().toInt()
                101 -> contrast = buffer.get().toInt()
                102 -> headIcon = buffer.getUnsignedShort()
                103 -> anInt2156 = buffer.getUnsignedShort()
                106 -> {
                    varpIndex = buffer.getUnsignedShort()
                    varp32Index = buffer.getUnsignedShort()
                    val length = buffer.getUnsignedByte()
                    configs = IntArray(length + 2) {
                        if (it == length + 1) -1 else buffer.getUnsignedShort()
                    }
                }
                107 -> isInteractable = false
                109 -> isClickable = false
                111 -> aBool2190 = true
                118 -> {
                    varpIndex = buffer.getUnsignedShort()
                    varp32Index = buffer.getUnsignedShort()
                    val v = buffer.getUnsignedShort()
                    val length = buffer.getUnsignedByte()
                    configs = IntArray(length + 2) {
                        if (it == length + 1) v
                        else buffer.getUnsignedShort()
                    }
                }
                249 -> params = buffer.getParams()
                else -> error(opcode)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.qualifiedName)

        private const val ARCHIVE_INDEX = 9

        fun load(store: CacheStore): Map<Int, NpcDefinition> {
            val refTable = store.getReferenceTable(IndexType.CONFIGS.id)
            val entry = refTable.getEntry(ARCHIVE_INDEX)
            val archive = Archive.decode(store.read(
                IndexType.CONFIGS.id,
                ARCHIVE_INDEX
            ).data,
                    refTable.getEntry(ARCHIVE_INDEX)!!.amountOfChildren
            )

            var defCount = 0
            val npcDefs = mutableMapOf<Int, NpcDefinition>()
            for(id in 0 until entry!!.capacity) {
                val child = entry.getEntry(id) ?: continue
                npcDefs[id] = NpcDefinition(id).decode(archive.getEntry(child.index))
                defCount++
            }
            logger.info("Loaded $defCount npc definitions")
            return npcDefs
        }
    }
}
