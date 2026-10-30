package com.vylia.privacy.data.trackers

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Extrait les noms des classes définies dans un fichier DEX, sans dépendance externe.
 *
 * Seules les sections nécessaires sont lues (cf. https://source.android.com/docs/core/runtime/dex-format) :
 * `class_defs` → `type_ids` → `string_ids` → `string_data_item` (descripteur `Lcom/foo/Bar;`).
 */
object DexClassNameReader {

    private const val HEADER_SIZE = 0x70
    private const val STRING_IDS_OFF = 0x3C
    private const val TYPE_IDS_OFF = 0x44
    private const val CLASS_DEFS_SIZE = 0x60
    private const val CLASS_DEFS_OFF = 0x64
    private const val CLASS_DEF_ITEM_SIZE = 32

    /** Noms de classes au format Java (`com.foo.Bar`). */
    fun readClassNames(dex: ByteArray): List<String> {
        require(isDex(dex)) { "Fichier DEX invalide" }
        val buffer = ByteBuffer.wrap(dex).order(ByteOrder.LITTLE_ENDIAN)
        val stringIdsOff = buffer.getInt(STRING_IDS_OFF)
        val typeIdsOff = buffer.getInt(TYPE_IDS_OFF)
        val classDefsSize = buffer.getInt(CLASS_DEFS_SIZE)
        val classDefsOff = buffer.getInt(CLASS_DEFS_OFF)

        return List(classDefsSize) { index ->
            val typeIdx = buffer.getInt(classDefsOff + index * CLASS_DEF_ITEM_SIZE)
            val stringIdx = buffer.getInt(typeIdsOff + typeIdx * 4)
            val stringDataOff = buffer.getInt(stringIdsOff + stringIdx * 4)
            descriptorToClassName(readMutf8(dex, stringDataOff))
        }
    }

    private fun isDex(dex: ByteArray): Boolean =
        dex.size >= HEADER_SIZE && dex[0] == 'd'.code.toByte() && dex[1] == 'e'.code.toByte() &&
            dex[2] == 'x'.code.toByte() && dex[3] == '\n'.code.toByte()

    /** `string_data_item` : longueur ULEB128 (en unités UTF-16, ignorée) puis octets MUTF-8 terminés par 0. */
    private fun readMutf8(dex: ByteArray, offset: Int): String {
        var position = offset
        while (dex[position].toInt() and 0x80 != 0) position++
        position++
        val start = position
        while (dex[position] != 0.toByte()) position++
        // Le MUTF-8 ne diffère de l'UTF-8 que pour U+0000 et les caractères hors BMP, absents des noms de paquets.
        return String(dex, start, position - start, Charsets.UTF_8)
    }

    private fun descriptorToClassName(descriptor: String): String =
        descriptor.removePrefix("L").removeSuffix(";").replace('/', '.')
}
