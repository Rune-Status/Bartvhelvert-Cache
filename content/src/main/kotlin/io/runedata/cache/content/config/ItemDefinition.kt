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

class ItemDefinition(id: Int) : ConfigEntry(id) {
    var name: String = "null"
    var resizeX: Int = 128
    var resizeY: Int = 128
    var resizeZ: Int = 128
    var xan2d: Int = 0
    var yan2d: Int = 0
    var zan2d: Int = 0
    var price: Int = 1
    var isTradeable: Boolean = false
    var isStackable: Int = 0
    var inventoryModel: Int = 0
    var isMembersOnly: Boolean = false
    var colorFind: ShortArray? = null
    var colorReplace: ShortArray? = null
    var textureFind: ShortArray? = null
    var textureReplace: ShortArray? = null
    var zoom2d: Int = 200000
    var xOffset2d: Int = 0
    var yOffset2d: Int = 0
    var ambient: Int = 0
    var contrast: Int = 0
    var countCo: IntArray? = null
    var countObj: IntArray? = null
    var options = arrayOf(null, null, "Take", null, null)
    var interfaceOptions= arrayOf(null, null, null, null, "Drop")
    var maleModel0: Int = -1
    var maleModel1: Int = -1
    var maleModel2: Int = -1
    var maleOffset: Int = 0
    var maleHeadModel: Int = -1
    var maleHeadModel2: Int = -1
    var femaleModel0: Int = -1
    var femaleModel1: Int = -1
    var femaleModel2: Int = -1
    var femaleOffset: Int = 0
    var femaleHeadModel: Int = -1
    var femaleHeadModel2: Int = -1
    var notedID: Int = -1
    var notedTemplate: Int = -1
    var team: Int = 0
    var shiftClickDropIndex: Int = -2
    var boughtId: Int = -1
    var boughtTemplateId: Int = -1
    var placeholderId: Int = -1
    var placeholderTemplateId: Int = -1
    var params: MutableMap<Int, Any>? = null

    override fun decode(buffer: ByteBuffer): ItemDefinition {
        while (true) {
            val opcode = buffer.getUnsignedByte()
            when (opcode) {
                0 -> return this
                1 -> inventoryModel = buffer.getUnsignedShort()
                2 -> name = buffer.getString()
                4 -> zoom2d = buffer.getUnsignedShort()
                5 -> xan2d = buffer.getUnsignedShort()
                6 -> yan2d = buffer.getUnsignedShort()
                7 -> xOffset2d = buffer.short.toInt()
                8 -> yOffset2d = buffer.short.toInt()
                11 -> isStackable = 1
                12 -> price = buffer.int
                16 -> isMembersOnly = true
                23 -> {
                    maleModel0 = buffer.getUnsignedShort()
                    maleOffset = buffer.getUnsignedByte()
                }
                24 -> maleModel1 = buffer.getUnsignedShort()
                25 -> {
                    femaleModel0 = buffer.getUnsignedShort()
                    femaleOffset = buffer.getUnsignedByte()
                }
                26 -> femaleModel1 = buffer.getUnsignedShort()
                in 30..34 -> options[opcode - 30] = buffer.getString().takeIf { it != "Hidden" }
                in 35..39 -> interfaceOptions[opcode - 35] = buffer.getString()
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
                42 -> shiftClickDropIndex = buffer.getUnsignedByte()
                65 -> isTradeable = true
                78 -> maleModel2 = buffer.getUnsignedShort()
                79 -> femaleModel2 = buffer.getUnsignedShort()
                90 -> maleHeadModel = buffer.getUnsignedShort()
                91 -> femaleHeadModel = buffer.getUnsignedShort()
                92 -> maleHeadModel2 = buffer.getUnsignedShort()
                93 -> femaleHeadModel2 = buffer.getUnsignedShort()
                95 -> zan2d = buffer.getUnsignedShort()
                97 -> notedID = buffer.getUnsignedShort()
                98 -> notedTemplate = buffer.getUnsignedShort()
                in 100..109 -> {
                    if (countObj == null) {
                        countObj = IntArray(10)
                        countCo = IntArray(10)
                    }
                    countObj!![opcode - 100] = buffer.getUnsignedShort()
                    countCo!![opcode - 100] = buffer.getUnsignedShort()
                }
                110 -> resizeX = buffer.getUnsignedShort()
                111 -> resizeY = buffer.getUnsignedShort()
                112 -> resizeZ = buffer.getUnsignedShort()
                113 -> ambient = buffer.getUnsignedByte()
                114 -> contrast = buffer.getUnsignedByte()
                115 -> team = buffer.getUnsignedByte()
                139 -> boughtId = buffer.getUnsignedShort()
                140 -> boughtTemplateId = buffer.getUnsignedShort()
                148 -> placeholderId = buffer.getUnsignedShort()
                149 -> placeholderTemplateId = buffer.getUnsignedShort()
                249 -> params = buffer.getParams()
                else -> error(opcode)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.qualifiedName)

        private const val ARCHIVE_INDEX = 10

        fun load(store: CacheStore): Map<Int, ItemDefinition> {
            val refTable = store.getReferenceTable(IndexType.CONFIGS.id)
            val entry = refTable.getEntry(ARCHIVE_INDEX)
            val archive = Archive.decode(store.read(
                IndexType.CONFIGS.id,
                ARCHIVE_INDEX
            ).data,
                    refTable.getEntry(ARCHIVE_INDEX)!!.amountOfChildren
            )

            var defCount = 0
            val itemDefs = mutableMapOf<Int, ItemDefinition>()
            for(id in 0 until entry!!.capacity) {
                val child = entry.getEntry(id) ?: continue
                itemDefs[id] = ItemDefinition(id).decode(archive.getEntry(child.index))
                defCount++
            }
            logger.info("Loaded $defCount item definitions")
            return itemDefs
        }
    }
}
