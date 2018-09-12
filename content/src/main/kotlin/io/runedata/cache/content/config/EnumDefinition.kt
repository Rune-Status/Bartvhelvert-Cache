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
import io.runedata.cache.filesystem.util.getString
import io.runedata.cache.filesystem.util.getUnsignedByte
import io.runedata.cache.filesystem.util.getUnsignedShort
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

class EnumDefinition(id: Int) : ConfigEntry(id) {
    var keyType: Char = 0.toChar()
    var valType: Char = 0.toChar()
    var defaultString = "null"
    var defaultInt: Int = 0
    val keyValuePairs = mutableMapOf<Int, Any>()

    override fun decode(buffer: ByteBuffer): EnumDefinition {
        while (true) {
            val opcode = buffer.getUnsignedByte()
            when (opcode) {
                0 -> return this
                1 -> keyType = buffer.getUnsignedByte().toChar()
                2 -> valType = buffer.getUnsignedByte().toChar()
                3 -> defaultString = buffer.getString()
                4 -> defaultInt = buffer.int
                5 -> {
                    val length = buffer.getUnsignedShort()
                    for (i in 0 until length) {
                        val key = buffer.int
                        keyValuePairs[key] = buffer.getString()
                    }
                }
                6 -> {
                    val length = buffer.getUnsignedShort()
                    for (i in 0 until length) {
                        val key = buffer.int
                        keyValuePairs[key] = buffer.int
                    }
                }
                else -> error(opcode)
            }
        }
    }

     companion object {
         private val logger = LoggerFactory.getLogger(this::class.qualifiedName)

         private const val ARCHIVE_INDEX= 8

         fun load(store: CacheStore): Map<Int, EnumDefinition> {
             val refTable = store.getReferenceTable(IndexType.CONFIGS.id)
             val entry = refTable.getEntry(ARCHIVE_INDEX)
             val archive = Archive.decode(store.read(
                 IndexType.CONFIGS.id,
                 ARCHIVE_INDEX
             ).data,
                     refTable.getEntry(ARCHIVE_INDEX)!!.amountOfChildren
             )

             var defCount = 0
             val enumDefs = mutableMapOf<Int, EnumDefinition>()
             for(id in 0 until entry!!.capacity) {
                 val child = entry.getEntry(id) ?: continue
                 enumDefs[id] = EnumDefinition(id).decode(archive.getEntry(child.index))
                 defCount++
             }
             logger.info("Loaded $defCount enum definitions")
             return enumDefs
         }
     }
}