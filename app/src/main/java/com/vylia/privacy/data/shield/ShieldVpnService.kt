package com.vylia.privacy.data.shield

import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import com.vylia.privacy.MainActivity
import com.vylia.privacy.VyliaApplication
import com.vylia.privacy.domain.shield.Blocklist
import com.vylia.privacy.domain.shield.DnsQuery
import com.vylia.privacy.domain.shield.ShieldStatus
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Bouclier VYLIA : un VPN local qui ne transporte que le DNS.
 *
 * Android envoie toutes ses requêtes DNS au serveur virtuel [DNS_ADDRESS], seule adresse routée dans le tunnel :
 * le reste du trafic ne passe pas par VYLIA. Chaque requête est lue ici ; si le domaine est dans la liste de blocage,
 * le bouclier répond lui-même « ce domaine n'existe pas » ; sinon il la transmet telle quelle au serveur DNS du
 * réseau (celui qu'Android aurait utilisé sans le bouclier) et renvoie sa réponse. Rien ne part vers un serveur
 * de VYLIA, et VYLIA elle-même est exclue du tunnel.
 */
class ShieldVpnService : VpnService() {

    private val controller get() = (application as VyliaApplication).container.shieldController

    @Volatile private var tunnel: ParcelFileDescriptor? = null
    @Volatile private var running = false
    @Volatile private var upstream: List<InetAddress> = emptyList()
    @Volatile private var underlying: Network? = null
    private var reader: Thread? = null
    private var forwarders: ExecutorService? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stop(ShieldStatus.OFF)
            // ACTION_START, ou démarrage par Android (VPN permanent : action SERVICE_INTERFACE).
            else -> start()
        }
        return START_NOT_STICKY
    }

    /** Un autre VPN a été activé, ou l'utilisateur a retiré l'autorisation dans les réglages. */
    override fun onRevoke() {
        stop(ShieldStatus.REVOKED)
    }

    override fun onDestroy() {
        stop(if (running) ShieldStatus.OFF else null)
        super.onDestroy()
    }

    @Synchronized
    private fun start() {
        if (running) return
        running = true
        controller.onStatus(ShieldStatus.STARTING)
        reader = Thread({ run() }, "vylia-shield").apply { start() }
    }

    @Synchronized
    private fun stop(status: ShieldStatus?) {
        running = false
        networkCallback?.let { runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } }
        networkCallback = null
        forwarders?.shutdownNow()
        forwarders = null
        runCatching { tunnel?.close() }
        tunnel = null
        reader = null
        status?.let(controller::onStatus)
        stopSelf()
    }

    private fun run() {
        try {
            val blocklist = controller.blocklist()
            watchNetwork()
            val tunnel = establish() ?: throw IOException("interface VPN refusée (autorisation retirée ?)")
            this.tunnel = tunnel
            forwarders = ThreadPoolExecutor(0, MAX_FORWARDERS, 30, TimeUnit.SECONDS, LinkedBlockingQueue(MAX_PENDING))
            controller.onStatus(ShieldStatus.ON)
            loop(tunnel, blocklist)
        } catch (e: Exception) {
            if (running) {
                Log.w(TAG, "Bouclier arrêté", e)
                stop(ShieldStatus.FAILED)
            }
        }
    }

    private fun establish(): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession("VYLIA")
            .setMtu(MTU)
            .addAddress(TUN_ADDRESS, 32)
            .addDnsServer(DNS_ADDRESS)
            .addRoute(DNS_ADDRESS, 32)
            // Sans route IPv6, Android bloquerait tout le trafic IPv6 : il continue par le réseau normal.
            .allowFamily(OsConstants.AF_INET6)
            // VYLIA hors du tunnel : ses requêtes vers le DNS du réseau ne reviennent pas dans le bouclier.
            .addDisallowedApplication(packageName)
            .setConfigureIntent(
                PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false) // suit le réseau sous-jacent
        underlying?.let { builder.setUnderlyingNetworks(arrayOf(it)) }
        return builder.establish()
    }

    /**
     * Suit le réseau par défaut de VYLIA, qui est hors du tunnel : c'est le vrai réseau (Wi-Fi, données mobiles), et
     * ses serveurs DNS sont ceux à qui transmettre les requêtes non bloquées.
     */
    private fun watchNetwork() {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, link: LinkProperties) {
                underlying = network
                upstream = link.dnsServers.toList()
                runCatching { setUnderlyingNetworks(arrayOf(network)) }
            }

            override fun onLost(network: Network) {
                if (network == underlying) {
                    underlying = null
                    upstream = emptyList()
                }
            }
        }
        connectivity.registerDefaultNetworkCallback(callback)
        networkCallback = callback
    }

    private fun loop(tunnel: ParcelFileDescriptor, blocklist: Blocklist) {
        val input = FileInputStream(tunnel.fileDescriptor)
        val output = FileOutputStream(tunnel.fileDescriptor)
        val buffer = ByteArray(MTU)
        val poll = StructPollfd().apply {
            fd = tunnel.fileDescriptor
            events = OsConstants.POLLIN.toShort()
        }
        while (running) {
            // Attente bornée : l'arrêt du bouclier est vu en moins d'une seconde.
            if (Os.poll(arrayOf(poll), POLL_TIMEOUT_MS) <= 0) continue
            val length = input.read(buffer)
            if (length <= 0) continue
            // Le reste (TCP vers le port 53 ou 853, autres paquets) est abandonné : Android repasse en UDP.
            val query = DnsQuery.parse(buffer, length) ?: continue
            val match = blocklist.match(query.name)
            if (match != null) {
                write(output, query.reply(query.blockedResponse()))
                controller.onBlocked(query.name, match)
            } else {
                forward(output, query)
            }
        }
    }

    private fun forward(output: FileOutputStream, query: DnsQuery) {
        val servers = upstream
        if (servers.isEmpty()) return // pas de réseau : l'app réessaiera
        runCatching {
            forwarders?.execute {
                for (server in servers.take(2)) {
                    val response = runCatching { ask(server, query.message) }.getOrNull() ?: continue
                    write(output, query.reply(response))
                    return@execute
                }
            }
        } // file pleine : la requête est abandonnée, l'app réessaiera
    }

    private fun ask(server: InetAddress, message: ByteArray): ByteArray = DatagramSocket().use { socket ->
        protect(socket)
        socket.soTimeout = UPSTREAM_TIMEOUT_MS
        socket.send(DatagramPacket(message, message.size, InetSocketAddress(server, DnsQuery.DNS_PORT)))
        val buffer = ByteArray(MAX_RESPONSE)
        val packet = DatagramPacket(buffer, buffer.size)
        socket.receive(packet)
        buffer.copyOf(packet.length)
    }

    private fun write(output: FileOutputStream, packet: ByteArray) {
        synchronized(output) {
            if (running) runCatching { output.write(packet) }
        }
    }

    companion object {
        const val ACTION_START = "com.vylia.privacy.shield.START"
        const val ACTION_STOP = "com.vylia.privacy.shield.STOP"
        private const val TAG = "VyliaShield"

        /** Adresses privées propres au tunnel ; seul le serveur DNS virtuel est routé. */
        private const val TUN_ADDRESS = "10.111.222.1"
        private const val DNS_ADDRESS = "10.111.222.2"
        private const val MTU = 1500
        private const val POLL_TIMEOUT_MS = 1_000
        private const val UPSTREAM_TIMEOUT_MS = 5_000
        private const val MAX_RESPONSE = 4_096
        private const val MAX_FORWARDERS = 16
        private const val MAX_PENDING = 256
    }
}
