package com.vylia.privacy.data.trackers

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class DexClassNameReaderTest {

    @Test
    fun `extrait les classes definies`() {
        val dex = buildDex(listOf("Lcom/facebook/ads/AdView;", "Lapp/example/MainActivity;", "LRootClass;"))
        assertEquals(
            listOf("com.facebook.ads.AdView", "app.example.MainActivity", "RootClass"),
            DexClassNameReader.readClassNames(dex),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejette un fichier qui n est pas un DEX`() {
        DexClassNameReader.readClassNames(ByteArray(0x70))
    }

    /** DEX minimal : en-tête, string_ids, type_ids, class_defs puis string_data (une classe par descripteur). */
    private fun buildDex(descriptors: List<String>): ByteArray {
        val count = descriptors.size
        val stringIdsOff = 0x70
        val typeIdsOff = stringIdsOff + 4 * count
        val classDefsOff = typeIdsOff + 4 * count
        val stringDataOff = classDefsOff + 32 * count

        val stringData = ByteArrayOutputStream()
        val stringOffsets = descriptors.map { descriptor ->
            val offset = stringDataOff + stringData.size()
            val bytes = descriptor.toByteArray(Charsets.UTF_8)
            stringData.write(descriptor.length) // ULEB128 sur un octet (< 128)
            stringData.write(bytes)
            stringData.write(0)
            offset
        }

        val buffer = ByteBuffer.allocate(stringDataOff + stringData.size()).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("dex\n035\u0000".toByteArray(Charsets.US_ASCII))
        buffer.putInt(0x38, count).putInt(0x3C, stringIdsOff)
        buffer.putInt(0x40, count).putInt(0x44, typeIdsOff)
        buffer.putInt(0x60, count).putInt(0x64, classDefsOff)
        stringOffsets.forEachIndexed { i, offset -> buffer.putInt(stringIdsOff + 4 * i, offset) }
        repeat(count) { i ->
            buffer.putInt(typeIdsOff + 4 * i, i)
            buffer.putInt(classDefsOff + 32 * i, i)
        }
        buffer.position(stringDataOff)
        buffer.put(stringData.toByteArray())
        return buffer.array()
    }
}
