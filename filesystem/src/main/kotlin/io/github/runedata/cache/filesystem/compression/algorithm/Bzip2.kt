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
package io.github.runedata.cache.filesystem.compression.algorithm

import io.github.runedata.cache.filesystem.compression.Compression
import org.apache.tools.bzip2.CBZip2InputStream
import org.apache.tools.bzip2.CBZip2OutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object Bzip2 : Compression {
    override fun zip(bytes: ByteArray): ByteArray {
        var bytesToZip = bytes
        ByteArrayInputStream(bytesToZip).use { inStream ->
            val bout = ByteArrayOutputStream()
            CBZip2OutputStream(bout, 1).use { outStream ->
                val buf = ByteArray(4096)
                var len = inStream.read(buf, 0, buf.size)
                while (len != -1) {
                    outStream.write(buf, 0, len)
                    len = inStream.read(buf, 0, buf.size)
                }
            }
            bytesToZip = bout.toByteArray()
            val bzip2 = ByteArray(bytesToZip.size - 2)
            System.arraycopy(bytesToZip, 2, bzip2, 0, bzip2.size)
            return bzip2
        }
    }

    override fun unzip(bytes: ByteArray): ByteArray {
        val bzip2 = ByteArray(bytes.size + 2)
        bzip2[0] = 'h'.toByte()
        bzip2[1] = '1'.toByte()
        System.arraycopy(bytes, 0, bzip2, 2, bytes.size)

        CBZip2InputStream(ByteArrayInputStream(bzip2)).use { inStream ->
            val outputStream = ByteArrayOutputStream()
            outputStream.use { outStream ->
                val buf = ByteArray(4096)
                var len = inStream.read(buf, 0, buf.size)
                while (len != -1) {
                    outStream.write(buf, 0, len)
                    len = inStream.read(buf, 0, buf.size)
                }
            }
            return outputStream.toByteArray()
        }
    }
}
