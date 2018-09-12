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
package io.runedata.cache.filesystem.compression.algorithm

import io.runedata.cache.filesystem.compression.Compression
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object Gzip2 : Compression {
    override fun zip(bytes: ByteArray): ByteArray {
        val inputStream = ByteArrayInputStream(bytes)
        inputStream.use { inStream ->
            val bout = ByteArrayOutputStream()
            val outputStream = GZIPOutputStream(bout)
            outputStream.use { outStream ->
                val buf = ByteArray(4096)
                var len = inStream.read(buf, 0, buf.size)
                while (len != -1) {
                    outStream.write(buf, 0, len)
                    len = inStream.read(buf, 0, buf.size)
                }
            }
            return bout.toByteArray()
        }
    }

    override fun unzip(bytes: ByteArray): ByteArray {
        val inputStream = GZIPInputStream(ByteArrayInputStream(bytes))
        inputStream.use { inStream ->
            val outputStream = ByteArrayOutputStream()
            outputStream.use { outStream ->
                // copy data between the streams
                val buf = ByteArray(4096)
                var len = inStream.read(buf, 0, buf.size)
                while (len!= -1) {
                    outStream.write(buf, 0, len)
                    len = inStream.read(buf, 0, buf.size)
                }
            }
            return outputStream.toByteArray()
        }
    }
}
