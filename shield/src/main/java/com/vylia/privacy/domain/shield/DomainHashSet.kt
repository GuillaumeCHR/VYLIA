package com.vylia.privacy.domain.shield

import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Ensemble de domaines compact : une empreinte de 64 bits par domaine, dans un tableau trié, pour une recherche par
 * dichotomie. 230 000 domaines tiennent en 1,8 Mo, contre environ 25 Mo dans un `HashSet<String>`, ce qui compte pour
 * un service VPN qui tourne toute la journée.
 *
 * Deux domaines différents qui auraient la même empreinte ne se distinguent pas : avec n domaines, la probabilité
 * qu'un domaine quelconque soit bloqué à tort est d'environ n / 2⁶⁴, soit 10⁻¹⁴ pour 230 000 domaines.
 */
class DomainHashSet private constructor(private val hashes: LongArray) {

    val size: Int get() = hashes.size

    operator fun contains(domain: String): Boolean = hashes.binarySearch(hash(domain)) >= 0

    fun write(out: DataOutputStream) {
        out.writeInt(hashes.size)
        hashes.forEach(out::writeLong)
    }

    companion object {
        val EMPTY = DomainHashSet(LongArray(0))

        fun of(domains: Collection<String>): DomainHashSet {
            val array = LongArray(domains.size)
            var i = 0
            domains.forEach { array[i++] = hash(it) }
            array.sort()
            return DomainHashSet(dedup(array))
        }

        /** Relit un ensemble écrit par [write] ; un tableau non trié ou trop grand est refusé. */
        fun read(input: DataInputStream, max: Int): DomainHashSet {
            val n = input.readInt()
            require(n in 0..max) { "liste compilée invalide ($n domaines)" }
            val array = LongArray(n) { input.readLong() }
            for (i in 1 until n) require(array[i - 1] < array[i]) { "liste compilée invalide (ordre)" }
            return DomainHashSet(array)
        }

        private fun dedup(sorted: LongArray): LongArray {
            if (sorted.isEmpty()) return sorted
            var n = 1
            for (i in 1 until sorted.size) if (sorted[i] != sorted[n - 1]) sorted[n++] = sorted[i]
            return if (n == sorted.size) sorted else sorted.copyOf(n)
        }

        /** FNV-1a 64 bits sur les caractères du domaine (déjà en minuscules). */
        fun hash(domain: String): Long {
            var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
            for (c in domain) {
                h = h xor c.code.toLong()
                h *= 0x100000001b3L
            }
            return h
        }
    }
}
