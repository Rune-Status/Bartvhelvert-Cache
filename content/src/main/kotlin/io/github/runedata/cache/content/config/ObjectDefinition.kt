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
import io.github.runedata.cache.filesystem.util.getParams
import io.github.runedata.cache.filesystem.util.getString
import io.github.runedata.cache.filesystem.util.getUnsignedByte
import io.github.runedata.cache.filesystem.util.getUnsignedShort
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

class ObjectDefinition(id: Int) : ConfigEntry(id) {
    var textureFind: IntArray? = null
    var decorDisplacement = 16
    var isHollow = false
    var name = "null"
    var objectModels: IntArray? = null
    var objectTypes: IntArray? = null
    var colorFind: IntArray? = null
    var mapIconId = -1
    var textureReplace: IntArray? = null
    var width = 1
    var length = 1
    var anInt2083 = 0
    var anIntArray2084: IntArray? = null
    var offsetX = 0
    var nonFlatShading = false
    var anInt2088 = -1
    var animationID = -1
    var varpID = -1
    var ambient = 0
    var contrast = 0
    var options = arrayOfNulls<String>(5)
    var clipType = 2
    var mapSceneID = -1
    var colorReplace: IntArray? = null
    var isClipped = true
    var modelSizeX = 128
    var modelSizeHeight = 128
    var modelSizeY = 128
    var offsetHeight = 0
    var offsetY = 0
    var obstructsGround = false
    var contouredGround = -1
    var supportItems = -1
    var configChangeDest: IntArray? = null
    var isMirrored = false
    var configId = -1
    var ambientSoundId = -1
    var modelClipped = false
    var anInt2112 = 0
    var anInt2113 = 0
    var impenetrable = true
    var accessBlock = 0
    var params: MutableMap<Int, Any>? = null

    override fun decode(buffer: ByteBuffer): ObjectDefinition {
        while (true) {
            val opcode = buffer.getUnsignedByte()
            when (opcode) {
                0 -> {
                    post()
                    return this
                }
                1 -> {
                    val length = buffer.getUnsignedByte()
                    if (length > 0) {
                        objectTypes = IntArray(length)
                        objectModels = IntArray(length)
                        for (index in 0 until length) {
                            objectModels!![index] = buffer.getUnsignedShort()
                            objectTypes!![index] = buffer.getUnsignedByte()
                        }
                    }
                }
                2 -> name = buffer.getString()
                5 -> {
                    val length = buffer.getUnsignedByte()
                    if (length > 0) {
                        objectTypes = null
                        objectModels = IntArray(length) { buffer.getUnsignedShort() }
                    }
                }
                14 -> width = buffer.getUnsignedByte()
                15 -> length = buffer.getUnsignedByte()
                17 -> {
                    clipType = 0
                    impenetrable = false
                }
                18 -> impenetrable = false
                19 -> anInt2088 = buffer.getUnsignedByte()
                21 -> contouredGround = 0
                22 -> nonFlatShading = false
                23 -> modelClipped = true
                24 -> animationID = buffer.getUnsignedShort()
                27 -> clipType = 1
                28 -> decorDisplacement = buffer.getUnsignedByte()
                29 -> ambient = buffer.get().toInt()
                39 -> contrast = buffer.get().toInt()
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
                62 -> isMirrored = true
                64 -> isClipped = false
                65 -> modelSizeX = buffer.getUnsignedShort()
                66 -> modelSizeHeight = buffer.getUnsignedShort()
                67 -> modelSizeY = buffer.getUnsignedShort()
                68 -> mapSceneID = buffer.getUnsignedShort()
                69 -> accessBlock = buffer.get().toInt()
                70 -> offsetX = buffer.getUnsignedShort()
                71 -> offsetHeight = buffer.getUnsignedShort()
                72 -> offsetY = buffer.getUnsignedShort()
                73 -> obstructsGround = true
                74 -> isHollow = true
                75 -> supportItems = buffer.getUnsignedByte()
                77 -> {
                    varpID = buffer.getUnsignedShort()
                    configId = buffer.getUnsignedShort()
                    val length = buffer.getUnsignedByte()
                    configChangeDest = IntArray(length + 2) {
                        if (it == length + 1) -1
                        else buffer.getUnsignedShort()
                    }
                }
                78 -> {
                    ambientSoundId = buffer.getUnsignedShort()
                    anInt2083 = buffer.getUnsignedByte()
                }
                79 -> {
                    anInt2112 = buffer.getUnsignedShort()
                    anInt2113 = buffer.getUnsignedShort()
                    anInt2083 = buffer.getUnsignedByte()
                    val length = buffer.getUnsignedByte()
                    anIntArray2084 = IntArray(length) { buffer.getUnsignedShort() }
                }
                81 -> contouredGround = buffer.getUnsignedByte()
                82 -> mapIconId = buffer.getUnsignedShort()
                92 -> {
                    varpID = buffer.getUnsignedShort()
                    configId = buffer.getUnsignedShort()
                    val v = buffer.getUnsignedShort()
                    val length = buffer.getUnsignedByte()
                    configChangeDest = IntArray(length + 2) {
                        if (it == length + 1) v
                        else buffer.getUnsignedShort()
                    }
                }
                249 -> params = buffer.getParams()
                else -> error(opcode)
            }
        }
    }

    private fun post() {
        if (anInt2088 == -1) {
            anInt2088 = 0
            if (this.objectModels != null && (this.objectTypes == null || objectTypes!![0] == 10)) {
                anInt2088 = 1
            }
            for (var1 in 0..4) {
                if (options[var1] != null) {
                    anInt2088 = 1
                }
            }
        }
        if (supportItems == -1) {
            supportItems = if (clipType != 0) 1 else 0
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.qualifiedName)

        private const val ARCHIVE_INDEX = 6

        fun load(store: CacheStore): Map<Int, ObjectDefinition> {
            val refTable = store.getReferenceTable(IndexType.CONFIGS.id)
            val entry = refTable.getEntry(ARCHIVE_INDEX)
            val archive = Archive.decode(store.read(
                IndexType.CONFIGS.id,
                ARCHIVE_INDEX
            ).data,
                    refTable.getEntry(ARCHIVE_INDEX)!!.amountOfChildren
            )

            var defCount = 0
            val objDefs = mutableMapOf<Int, ObjectDefinition>()
            for(id in 0 until entry!!.capacity) {
                val child = entry.getEntry(id) ?: continue
                objDefs[id] = ObjectDefinition(id).decode(archive.getEntry(child.index))
                defCount++
            }
            logger.info("Loaded $defCount object definitions")
            return objDefs
        }
    }
}
