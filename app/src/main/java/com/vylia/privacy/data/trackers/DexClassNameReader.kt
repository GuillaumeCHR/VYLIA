package com.vylia.privacy.data.trackers

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Extrait les noms des classes définies dans un fichier DEX, sans dépendance externe.
 *
 * Seules les sections nécessaires sont lues (cf. https://source.android.com/docs/core/runtime/dex-format) :
 * en-tête → `class_defs` → `type_ids` → `string_ids` → `string_data_item` (descripteur `Lcom/foo/Bar;`).
 * Partir de `class_defs` (classes embarquées) plutôt que de tous les `type_ids` (qui incluent les
 * classes seulement référencées, dont celles du framework) suit la méthode d'Exodus et évite de
 * signaler un SDK simplement mentionné.
 *
 * Lecture par accès absolus dans un [ByteBuffer] : avec un fichier mappé en mémoire, le DEX n'est
 * jamais copié dans le tas Java.
 */
object DexClassNameReader {

    private const val HEADER_SIZE = 0x70
    private const val STRING_IDS_SIZE = 0x38
    private const val STRING_IDS_OFF = 0x3C
    private const val TYPE_IDS_SIZE = 0x40
    private const val TYPE_IDS_OFF = 0x44
    private const val CLASS_DEFS_SIZE = 0x60
    private const val CLASS_DEFS_OFF = 0x64
    private const val CLASS_DEF_ITEM_SIZE = 32

    /** Noms de classes au format Java (`com.foo.Bar`). */
    fun readClassNames(dex: ByteArray): List<String> = buildList { forEachClassName(ByteBuffer.wrap(dex)) { add(it) } }

    /** Parcourt les classes définies sans les accumuler. Lève [IllegalArgumentException] si le DEX est invalide. */
    fun forEachClassName(dex: ByteBuffer, action: (String) -> Unit) {
        val buffer = dex.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        require(isDex(buffer)) { "Fichier DEX invalide" }
        val stringIdsSize = buffer.getInt(STRING_IDS_SIZE)
        val stringIdsOff = buffer.getInt(STRING_IDS_OFF)
        val typeIdsSize = buffer.getInt(TYPE_IDS_SIZE)
        val typeIdsOff = buffer.getInt(TYPE_IDS_OFF)
        val classDefsSize = buffer.getInt(CLASS_DEFS_SIZE)
        val classDefsOff = buffer.getInt(CLASS_DEFS_OFF)
        requireSection(buffer, stringIdsOff, stringIdsSize, 4)
        requireSection(buffer, typeIdsOff, typeIdsSize, 4)
        requireSection(buffer, classDefsOff, classDefsSize, CLASS_DEF_ITEM_SIZE)

        repeat(classDefsSize) { index ->
            val typeIdx = buffer.getInt(classDefsOff + index * CLASS_DEF_ITEM_SIZE)
            require(typeIdx in 0 until typeIdsSize) { "type_idx hors bornes" }
            val stringIdx = buffer.getInt(typeIdsOff + typeIdx * 4)
            require(stringIdx in 0 until stringIdsSize) { "string_idx hors bornes" }
            action(descriptorToClassName(readMutf8(buffer, buffer.getInt(stringIdsOff + stringIdx * 4))))
        }
    }

    private fun isDex(dex: ByteBuffer): Boolean =
        dex.limit() >= HEADER_SIZE && dex.get(0) == 'd'.code.toByte() && dex.get(1) == 'e'.code.toByte() &&
            dex.get(2) == 'x'.code.toByte() && dex.get(3) == '\n'.code.toByte()

    private fun requireSection(dex: ByteBuffer, offset: Int, count: Int, itemSize: Int) {
        require(count >= 0 && offset >= 0 && offset.toLong() + count.toLong() * itemSize <= dex.limit()) {
            "Section hors du fichier"
        }
    }

    /** `string_data_item` : longueur ULEB128 (en unités UTF-16, ignorée) puis octets MUTF-8 terminés par 0. */
    private fun readMutf8(dex: ByteBuffer, offset: Int): String {
        var position = offset
        while (dex.get(position).toInt() and 0x80 != 0) position++
        position++
        val start = position
        while (dex.get(position) != 0.toByte()) position++
        val bytes = ByteArray(position - start)
        for (i in bytes.indices) bytes[i] = dex.get(start + i)
        // Le MUTF-8 ne diffère de l'UTF-8 que pour U+0000 et les caractères hors BMP, absents des noms de paquets.
        return String(bytes, Charsets.UTF_8)
    }

    private fun descriptorToClassName(descriptor: String): String =
        descriptor.removePrefix("L").removeSuffix(";").replace('/', '.')
}
