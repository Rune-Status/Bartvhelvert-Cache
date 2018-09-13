package io.github.runedata.cache.content.config

import io.github.runedata.cache.content.IndexType
import io.github.runedata.cache.filesystem.Archive
import io.github.runedata.cache.filesystem.CacheStore
import io.github.runedata.cache.filesystem.util.getMedium
import io.github.runedata.cache.filesystem.util.getSmartInt
import io.github.runedata.cache.filesystem.util.getString
import io.github.runedata.cache.filesystem.util.getUnsignedByte
import io.github.runedata.cache.filesystem.util.getUnsignedShort
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

class HitMarkDefinition(id: Int) : ConfigEntry(id) {
    var field3353 = -1
    var field3364 = 16777215
    var field3355 = 70
    var field3358 = -1
    var field3357 = -1
    var field3350 = -1
    var field3359 = -1
    var field3365 = 0
    var field3361 = 0
    var field3354 = -1
    var field3363 = ""
    var field3360 = -1
    var field3347 = 0
    var field3362 = -1
    var field3368 = -1
    lateinit var field3366: IntArray

    override fun decode(buffer: ByteBuffer): HitMarkDefinition {
        while(true) {
            val opcode = buffer.getUnsignedByte()
            when(opcode) {
                0 -> return this
                1 -> field3353 = buffer.getSmartInt()
                2 -> field3364 = buffer.getMedium()
                3 -> field3358 = buffer.getSmartInt()
                4 -> field3350 = buffer.getSmartInt()
                5 -> field3357 = buffer.getSmartInt()
                6 -> field3359 = buffer.getSmartInt()
                7 -> field3365 = buffer.short.toInt()
                8 -> field3363 = buffer.getString()
                9 -> field3355 = buffer.getUnsignedShort()
                10 -> field3361 = buffer.short.toInt()
                11 -> field3354 = 0
                12 -> field3360 = buffer.getUnsignedByte()
                13 -> field3347 = buffer.short.toInt()
                14 -> field3354 = buffer.getUnsignedShort()
                17, 18 -> {
                    field3362 = buffer.getUnsignedShort()
                    if (field3362 == 65535) {
                        field3362 = -1
                    }

                    field3368 = buffer.getUnsignedShort()
                    if (field3368 == 65535) {
                        field3368 = -1
                    }

                    var int_1 = -1
                    if (opcode == 18) {
                        int_1 = buffer.getUnsignedShort()
                        if (int_1 == 65535) {
                            int_1 = -1
                        }
                    }

                    val int_2 = buffer.getUnsignedByte()
                    field3366 = IntArray(int_2 + 2)

                    for (int_3 in 0..int_2) {
                        field3366[int_3] = buffer.getUnsignedShort()
                        if (field3366[int_3] == 65535) {
                            field3366[int_3] = -1
                        }
                    }

                    field3366[int_2 + 1] = int_1
                }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.qualifiedName)

        private const val ARCHIVE_INDEX = 32

        fun load(store: CacheStore): Map<Int, HitMarkDefinition> {
            val refTable = store.getReferenceTable(IndexType.CONFIGS.id)
            val entry = refTable.getEntry(ARCHIVE_INDEX)
            val archive = Archive.decode(store.read(
                IndexType.CONFIGS.id,
                ARCHIVE_INDEX
            ).data,
                refTable.getEntry(ARCHIVE_INDEX)!!.amountOfChildren
            )

            var defCount = 0
            val hitMarkDefs = mutableMapOf<Int, HitMarkDefinition>()
            for(id in 0 until entry!!.capacity) {
                val child = entry.getEntry(id) ?: continue
                hitMarkDefs[id] = HitMarkDefinition(id).decode(archive.getEntry(child.index))
                defCount++
            }
            logger.info("Loaded $defCount hitmark definitions")
            return hitMarkDefs
        }
    }
}