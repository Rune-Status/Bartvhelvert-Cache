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
package io.runedata.cache.content

import io.runedata.cache.filesystem.CacheStore
import io.runedata.cache.filesystem.util.getUnsignedByte
import io.runedata.cache.filesystem.util.getUnsignedSmart
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.zip.ZipException

class RegionDefinition(val regionX: Int, val regionY: Int) {
    val id get() = (regionX shl 8) or (regionY and 0xFF)
    val tileHeights = Array(4) { Array(104) { IntArray(104) } }
    val renderRules = Array(4) { Array(104) { ByteArray(104) } }
    val overlayIds = Array(4) { Array(104) { ByteArray(104) } }
    val overlayPaths = Array(4) { Array(104) { ByteArray(104) } }
    val overlayRotations = Array(4) { Array(104) { ByteArray(104) } }
    val underlayIds = Array(4) { Array(104) { ByteArray(104) } }

    val mapObjects = mutableListOf<MapObjectDefinition>()

    fun decodeLandscape(buffer: ByteBuffer) {
        for(z in 0 until AMOUNT_OF_LAYERS) {
            for(x in 0 until SIZE) {
                for(y in 0 until SIZE) {
                    while (true) {
                        val attributeId = buffer.get().toInt() and 0xFF
                        if (attributeId == 0) {
                            if (z == 0) {
                                //TODO: add height calculation
                            } else {
                                tileHeights[z][x][y] = tileHeights[z - 1][x][y] - 240
                            }
                            break
                        } else if (attributeId == 1) {
                            var height = buffer.getUnsignedByte()
                            if (height == 1) {
                                height = 0
                            }
                            if (z == 0) {
                                tileHeights[0][x][y] = -height shl 3
                            } else {
                                tileHeights[z][x][y] = tileHeights[z - 1][x][y] - height shl 3
                            }
                            break
                        } else if (attributeId <= 49) {
                            overlayIds[z][x][y] = buffer.get()
                            overlayPaths[z][x][y] = ((attributeId - 2) / 4).toByte()
                            overlayRotations[z][x][y] = ((attributeId - 2) and 0x3).toByte()
                        } else if (attributeId <= 81) {
                            renderRules[z][x][y] = (attributeId - 49).toByte()
                        } else {
                            underlayIds[z][x][y] = (attributeId - 81).toByte()
                        }
                    }
                }
            }
        }
    }

    fun decodeObjects(buffer: ByteBuffer) {
        var id = -1
        var offset = buffer.getUnsignedSmart()
        while (offset != 0) {
            id += offset
            var positionHash = 0
            var positionOffset = buffer.getUnsignedSmart()
            while (positionOffset != 0) {
                positionHash += positionOffset - 1
                val localY = positionHash and 0x3F
                val localX = (positionHash shr 6) and 0x3F
                var z = (positionHash shr 12) and 0x3
                if(renderRules[1][localX][localY] == BRIDGE_TILE_MASK) {
                    z--
                } //TODO check if this is valid
                if(z < 0) {
                    buffer.get()
                } else {
                    val attributes = buffer.get().toInt() and 0xFF
                    val orientation = attributes and 0x3
                    val type = attributes shr 2
                    mapObjects.add(
                        MapObjectDefinition(
                            id,
                            z,
                            localX,
                            localY,
                            type,
                            orientation
                        )
                    )
                }
                positionOffset = buffer.getUnsignedSmart()
            }
            offset = buffer.getUnsignedSmart()
        }
    }

    class MapObjectDefinition(
        val id: Int,
        val z: Int,
        val localX: Int,
        val localY: Int,
        val type: Int,
        val orientation: Int
    )

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.qualifiedName)

        const val BLOCKED_TILE_MASK: Byte = 0x1
        const val BRIDGE_TILE_MASK: Byte = 0x2
        const val ROOF_TILE_MASK: Byte = 0x4
        const val AMOUNT_OF_LAYERS = 4
        const val SIZE = 64

        fun load(store: CacheStore): List<RegionDefinition> {
            val regionDefs = mutableListOf<RegionDefinition>()
            for(xtea in store.xteas) {
                val regionId = xtea.key
                val x = getXFromId(regionId)
                val y = getYFromId(regionId)
                val mapFileId = store.getFileId(5, "m" + x + "_" + y)
                val landFileId = store.getFileId(5, "l" + x + "_" + y)
                if(mapFileId == -1 || landFileId == -1) {
                    continue
                }
                val regionDef = RegionDefinition(x, y).apply {
                    decodeLandscape(store.read(IndexType.REGIONS.id, mapFileId).data)
                }
                try {
                    regionDef.decodeObjects(store.read(IndexType.REGIONS.id, landFileId, xtea.value).data)
                } catch(e: ZipException) {
                    logger.warn("Could not load region definition: $regionId")
                    continue
                }
                regionDefs.add(regionDef)
            }
            return regionDefs
        }

        fun getXFromId(regionId: Int) = regionId shr 8

        fun getYFromId(regionId: Int) = regionId and 0xFF
    }
}