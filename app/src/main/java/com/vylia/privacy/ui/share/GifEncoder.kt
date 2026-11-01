package com.vylia.privacy.ui.share

import android.graphics.Bitmap
import java.io.OutputStream

/**
 * Encodeur GIF89a animé minimal (repris de Molkky Board) : aucune bibliothèque, aucun serveur. Palette fixe de
 * 256 couleurs (couleurs de VYLIA, cube 6 × 6 × 6, niveaux de gris) et table de correspondance précalculée :
 * aucune quantification coûteuse par image. Compression LZW standard (codes de 9 à 12 bits).
 */
class GifEncoder(
    private val out: OutputStream,
    private val width: Int,
    private val height: Int,
    /** Délai entre deux images, en centièmes de seconde. */
    private val delayCs: Int,
) {
    private val pixels = IntArray(width * height)
    private val indices = ByteArray(width * height)

    init {
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        short(width)
        short(height)
        // Table de couleurs globale de 256 entrées, résolution 8 bits.
        out.write(GLOBAL_TABLE_FLAGS)
        out.write(0)
        out.write(0)
        PALETTE.forEach { color ->
            out.write(color shr 16 and BYTE)
            out.write(color shr 8 and BYTE)
            out.write(color and BYTE)
        }
        // Boucle infinie (extension NETSCAPE2.0).
        out.write(
            byteArrayOf(0x21, 0xFF.toByte(), 0x0B) + "NETSCAPE2.0".toByteArray(Charsets.US_ASCII) +
                byteArrayOf(3, 1, 0, 0, 0),
        )
    }

    fun addFrame(bitmap: Bitmap) {
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val c = pixels[i]
            indices[i] =
                LOOKUP[(c shr 19 and FIVE_BITS) shl 10 or (c shr 11 and FIVE_BITS shl 5) or (c shr 3 and FIVE_BITS)]
        }
        // Extension de contrôle graphique : délai, pas de transparence.
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 0))
        short(delayCs)
        out.write(0)
        out.write(0)
        // Descripteur d'image : toute la surface, sans table locale.
        out.write(0x2C)
        short(0)
        short(0)
        short(width)
        short(height)
        out.write(0)
        Lzw(out).encode(indices)
    }

    fun finish() {
        out.write(0x3B)
        out.flush()
    }

    private fun short(value: Int) {
        out.write(value and BYTE)
        out.write(value shr 8 and BYTE)
    }

    /** Compression LZW d'une image en codes de longueur variable, par blocs de 255 octets au plus. */
    private class Lzw(
        private val out: OutputStream,
    ) {
        private val keys = IntArray(HASH_SIZE)
        private val codes = IntArray(HASH_SIZE)
        private val block = ByteArray(MAX_BLOCK)
        private var blockLength = 0
        private var bitBuffer = 0
        private var bitCount = 0
        private var codeSize = MIN_CODE_SIZE + 1
        private var maxCode = (1 shl codeSize) - 1
        private var nextCode = END + 1
        private var clearPending = false

        fun encode(data: ByteArray) {
            out.write(MIN_CODE_SIZE)
            keys.fill(EMPTY)
            emit(CLEAR)
            var prefix = data[0].toInt() and BYTE
            for (i in 1 until data.size) {
                val c = data[i].toInt() and BYTE
                val key = (prefix shl 8) or c
                val found = find(key)
                if (found >= 0) {
                    prefix = found
                    continue
                }
                emit(prefix)
                prefix = c
                if (nextCode < MAX_CODES) {
                    insert(key, nextCode++)
                } else {
                    keys.fill(EMPTY)
                    nextCode = END + 1
                    clearPending = true
                    emit(CLEAR)
                }
            }
            emit(prefix)
            emit(END)
            if (bitCount > 0) byte(bitBuffer and BYTE)
            flushBlock()
            out.write(0)
        }

        private fun slot(key: Int): Int {
            var i = (key * HASH_MULTIPLIER) ushr HASH_SHIFT
            while (keys[i] != EMPTY && keys[i] != key) i = (i + 1) and (HASH_SIZE - 1)
            return i
        }

        private fun find(key: Int): Int = slot(key).let { if (keys[it] == key) codes[it] else -1 }

        private fun insert(key: Int, code: Int) {
            val i = slot(key)
            keys[i] = key
            codes[i] = code
        }

        private fun emit(code: Int) {
            bitBuffer = bitBuffer or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                byte(bitBuffer and BYTE)
                bitBuffer = bitBuffer ushr 8
                bitCount -= 8
            }
            if (clearPending) {
                codeSize = MIN_CODE_SIZE + 1
                maxCode = (1 shl codeSize) - 1
                clearPending = false
            } else if (nextCode > maxCode && codeSize < MAX_BITS) {
                codeSize++
                maxCode = if (codeSize == MAX_BITS) MAX_CODES else (1 shl codeSize) - 1
            }
        }

        private fun byte(value: Int) {
            block[blockLength++] = value.toByte()
            if (blockLength == MAX_BLOCK) flushBlock()
        }

        private fun flushBlock() {
            if (blockLength == 0) return
            out.write(blockLength)
            out.write(block, 0, blockLength)
            blockLength = 0
        }

        companion object {
            private const val MIN_CODE_SIZE = 8
            private const val CLEAR = 256
            private const val END = 257
            private const val MAX_BITS = 12
            private const val MAX_CODES = 4_096
            private const val MAX_BLOCK = 255
            private const val HASH_SIZE = 16_384
            private const val HASH_MULTIPLIER = -0x61c88647
            private const val HASH_SHIFT = 18
            private const val EMPTY = -1
        }
    }

    companion object {
        private const val BYTE = 0xFF
        private const val FIVE_BITS = 0x1F
        private const val GLOBAL_TABLE_FLAGS = 0xF7

        /** Couleurs de la marque en tête de palette, puis cube 6 × 6 × 6, puis niveaux de gris. */
        private val PALETTE: IntArray =
            IntArray(256).also { palette ->
                val brand =
                    intArrayOf(
                        0x0F172A, // fond Slate 900
                        0x1E293B, // tuiles Slate 800
                        0x334155,
                        0x475569,
                        0x94A3B8,
                        0xCBD5E1,
                        0xFFFFFF,
                        0x10B981, // émeraude
                        0x38BDF8, // cyan
                        0xF59E0B, // ambre
                        0xEF4444, // rouge
                        0x84CC16,
                        0xF97316,
                        0x7C3AED, // espions
                        0x1E1B4B,
                        0xFFD7B5,
                    )
                brand.copyInto(palette)
                var i = brand.size
                for (r in 0 until 6) {
                    for (g in 0 until 6) {
                        for (b in 0 until 6) {
                            palette[i++] =
                                (r * 51 shl 16) or (g * 51 shl 8) or b * 51
                        }
                    }
                }
                var gray = 0
                while (i < 256) {
                    val v = 8 + gray++ * 10
                    palette[i++] = (v shl 16) or (v shl 8) or v
                }
            }

        /** Couleur 15 bits (5 par composante) → indice de palette le plus proche, calculé une fois. */
        private val LOOKUP: ByteArray =
            ByteArray(1 shl 15) { key ->
                val r = (key shr 10 and FIVE_BITS) * 255 / 31
                val g = (key shr 5 and FIVE_BITS) * 255 / 31
                val b = (key and FIVE_BITS) * 255 / 31
                var best = 0
                var bestDistance = Int.MAX_VALUE
                for (p in PALETTE.indices) {
                    val c = PALETTE[p]
                    val dr = (c shr 16 and BYTE) - r
                    val dg = (c shr 8 and BYTE) - g
                    val db = (c and BYTE) - b
                    val d = dr * dr * 2 + dg * dg * 4 + db * db * 3
                    if (d < bestDistance) {
                        bestDistance = d
                        best = p
                    }
                }
                best.toByte()
            }
    }
}
