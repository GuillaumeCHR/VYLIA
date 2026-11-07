package com.vylia.privacy.domain.shield

/**
 * Requête DNS lue sur l'interface du VPN : un paquet IPv4/UDP envoyé par Android au serveur DNS virtuel du bouclier.
 *
 * Seul ce qui est nécessaire est décodé (adresses, ports, nom demandé) ; le reste du message DNS est transmis tel
 * quel au serveur DNS du réseau. Toute lecture est bornée : un paquet mal formé donne `null`, jamais d'exception.
 */
class DnsQuery private constructor(
    private val sourceAddress: ByteArray,
    private val destinationAddress: ByteArray,
    private val sourcePort: Int,
    private val destinationPort: Int,
    /** Message DNS (charge utile UDP). */
    val message: ByteArray,
    /** Nom demandé, en minuscules, sans point final. */
    val name: String,
    val type: Int,
) {
    /** Paquet IPv4/UDP qui renvoie [response] à l'app, comme s'il venait du serveur DNS virtuel. */
    fun reply(response: ByteArray): ByteArray {
        val total = IPV4_HEADER + UDP_HEADER + response.size
        val packet = ByteArray(total)
        packet[0] = 0x45 // IPv4, en-tête de 20 octets
        putShort(packet, 2, total)
        putShort(packet, 6, 0x4000) // ne pas fragmenter
        packet[8] = 64 // TTL
        packet[9] = UDP.toByte()
        destinationAddress.copyInto(packet, 12)
        sourceAddress.copyInto(packet, 16)
        putShort(packet, 10, checksum(packet, 0, IPV4_HEADER))
        putShort(packet, 20, destinationPort)
        putShort(packet, 22, sourcePort)
        putShort(packet, 24, UDP_HEADER + response.size)
        // Somme de contrôle UDP laissée à 0 : facultative en IPv4.
        response.copyInto(packet, IPV4_HEADER + UDP_HEADER)
        return packet
    }

    /** Réponse « ce domaine n'existe pas » (NXDOMAIN) : l'app abandonne la connexion au traqueur, sans attendre. */
    fun blockedResponse(): ByteArray {
        val end = questionEnd(message) ?: HEADER
        val response = message.copyOf(end)
        val rd = message[2].toInt() and 0x01
        val opcode = message[2].toInt() and 0x78
        response[2] = (0x80 or opcode or rd).toByte() // QR = réponse, même opcode, RD repris
        response[3] = (0x80 or RCODE_NXDOMAIN).toByte() // RA, NXDOMAIN
        putShort(response, 4, if (end > HEADER) 1 else 0) // la question, rien d'autre
        putShort(response, 6, 0)
        putShort(response, 8, 0)
        putShort(response, 10, 0)
        return response
    }

    companion object {
        const val DNS_PORT = 53
        private const val UDP = 17
        private const val IPV4_HEADER = 20
        private const val UDP_HEADER = 8
        private const val HEADER = 12
        private const val RCODE_NXDOMAIN = 3
        private const val MAX_NAME = 253

        /** Décode [length] octets de [packet] ; `null` si ce n'est pas une requête DNS standard en IPv4/UDP. */
        fun parse(packet: ByteArray, length: Int): DnsQuery? {
            if (length < IPV4_HEADER + UDP_HEADER + HEADER || length > packet.size) return null
            if (packet[0].toInt() ushr 4 != 4) return null
            val ihl = (packet[0].toInt() and 0x0F) * 4
            val total = getShort(packet, 2)
            if (ihl < IPV4_HEADER || total > length || total < ihl + UDP_HEADER + HEADER) return null
            if (packet[9].toInt() and 0xFF != UDP) return null
            if (getShort(packet, 6) and 0x3FFF != 0) return null // fragment : ignoré
            val destinationPort = getShort(packet, ihl + 2)
            if (destinationPort != DNS_PORT) return null
            val udpLength = getShort(packet, ihl + 4)
            if (udpLength < UDP_HEADER + HEADER || ihl + udpLength > total) return null
            val message = packet.copyOfRange(ihl + UDP_HEADER, ihl + udpLength)
            if (message[2].toInt() and 0x80 != 0) return null // c'est une réponse, pas une requête
            if (getShort(message, 4) != 1) return null // une seule question, comme toute requête réelle
            val (name, end) = readName(message) ?: return null
            if (end + 4 > message.size) return null
            return DnsQuery(
                sourceAddress = packet.copyOfRange(12, 16),
                destinationAddress = packet.copyOfRange(16, 20),
                sourcePort = getShort(packet, ihl),
                destinationPort = destinationPort,
                message = message,
                name = name,
                type = getShort(message, end),
            )
        }

        /** Fin de la section question (après QTYPE et QCLASS), ou `null` si elle est mal formée. */
        private fun questionEnd(message: ByteArray): Int? =
            readName(message)?.second?.let { it + 4 }?.takeIf { it <= message.size }

        /** Nom de la question (sans compression, toujours le cas dans une question) et position qui suit. */
        private fun readName(message: ByteArray): Pair<String, Int>? {
            val name = StringBuilder()
            var i = HEADER
            while (true) {
                if (i >= message.size) return null
                val len = message[i].toInt() and 0xFF
                if (len == 0) break
                if (len > 63) return null // pointeur de compression ou longueur réservée
                if (i + 1 + len > message.size) return null
                if (name.isNotEmpty()) name.append('.')
                for (k in i + 1..i + len) {
                    val c = (message[k].toInt() and 0xFF).toChar()
                    name.append(if (c in 'A'..'Z') c + 32 else c)
                }
                if (name.length > MAX_NAME) return null
                i += 1 + len
            }
            return name.toString() to i + 1
        }

        private fun getShort(b: ByteArray, at: Int): Int = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

        private fun putShort(b: ByteArray, at: Int, value: Int) {
            b[at] = (value ushr 8).toByte()
            b[at + 1] = value.toByte()
        }

        /** Somme de contrôle de l'en-tête IPv4 (complément à un sur 16 bits). */
        internal fun checksum(b: ByteArray, from: Int, length: Int): Int {
            var sum = 0
            var i = from
            while (i < from + length) {
                if (i != from + 10) sum += getShort(b, i) // le champ lui-même compte pour 0
                i += 2
            }
            while (sum ushr 16 != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
            return sum.inv() and 0xFFFF
        }
    }
}
