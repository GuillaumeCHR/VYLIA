# Bouclier VYLIA (V2) — VPN local de filtrage DNS

Branche `feature/shield-local-vpn`. Agir › Réduire › Traçage pub : un interrupteur qui bloque, sur le téléphone, les
requêtes DNS vers les serveurs de traçage, avec un compteur en direct. Débloque le badge 🧿 « Bouclier actif ».

## Principe

```
 app ──DNS──▶ 10.111.222.2 (serveur virtuel, seule route du tunnel)
                    │
            ShieldVpnService (lit le tunnel)
              │ domaine dans la liste ?            │ non
              ▼ oui                                ▼
     réponse NXDOMAIN écrite             requête transmise telle quelle au DNS
     dans le tunnel + compteur           du vrai réseau (socket protégée), réponse
                                         recopiée dans le tunnel
 Le reste du trafic (TCP, UDP hors DNS, IPv6) ne passe pas par le tunnel.
```

- **Tunnel DNS seul** : `addRoute(10.111.222.2/32)` et `addDnsServer(10.111.222.2)`. Pas de pile TCP/IP à écrire,
  pas de copie du trafic des apps, impact batterie limité aux requêtes DNS. `allowFamily(AF_INET6)` évite qu'Android
  coupe l'IPv6 faute de route IPv6 dans le tunnel.
- **VYLIA hors du tunnel** (`addDisallowedApplication`) : son réseau par défaut reste le vrai réseau. Un
  `registerDefaultNetworkCallback` suit ses serveurs DNS (Wi-Fi ↔ données mobiles) et `setUnderlyingNetworks` garde
  à Android la bonne information de réseau facturé ou non.
- **Blocage** : réponse NXDOMAIN qui reprend la question seule (pas d'EDNS) ; l'app abandonne sans attendre de délai.
- **Transmission** : jusqu'à 16 fils, file bornée à 256 requêtes, 5 s par serveur, deux serveurs essayés. File pleine
  ou pas de réseau : la requête est abandonnée, l'app réessaie comme sur un réseau qui perd un paquet.
- **Arrêt** : lecture par `Os.poll` avec 1 s d'attente au plus, pour voir l'arrêt sans dépendre de la fermeture du fd.

## Fichiers

| Couche | Fichier | Rôle |
|---|---|---|
| domaine (pur, testé) | `domain/shield/Blocklist.kt` | liste, recherche par suffixe (`a.ads.com` bloqué par `ads.com`, jamais le TLD) |
| | `domain/shield/DnsPacket.kt` | `DnsQuery` : lecture IPv4/UDP/DNS bornée, réponse NXDOMAIN, paquet de retour |
| | `domain/shield/ShieldState.kt` | état, compteurs, derniers blocages (mémoire seulement) |
| données | `data/shield/ShieldVpnService.kt` | le `VpnService` |
| | `data/shield/ShieldController.kt` | état partagé UI ↔ service, total persisté, badge, accord VPN |
| interface | `ui/act/ShieldCard.kt` | interrupteur, compteur, avertissements, derniers blocages |
| outil | `tools/update_shield_blocklist.py` | génère `assets/shield_blocklist.json` |

## Liste de blocage

`python3 tools/update_shield_blocklist.py` (poste de dev ; l'app ne télécharge rien pour le bouclier) fusionne :

- **Exodus Privacy** (`network_signature`, ODbL) : catégories Advertisement, Analytics, Profiling, Identification
  seulement (catégories complétées comme pour le score). Le nom du traqueur est gardé et affiché à chaque blocage.
- **EasyPrivacy** (GPL v3, compatible avec la licence de VYLIA) : seules les règles `||domaine^` sans option ni
  chemin, moins les exceptions `@@||domaine^`.
- **Jamais bloqués** : les domaines de plateformes (`google.com`, `facebook.com`, `amazonaws.com`…), qu'une
  signature trop large bloquerait en entier ; un sous-domaine précis reste bloquable.

Au 2026-11-01 : 540 domaines Exodus, 43 318 EasyPrivacy, 43 682 après fusion, environ 1 Mo (compressé dans l'APK).
Le test `BlocklistTest` vérifie que la liste embarquée ne bloque pas les grandes plateformes.

## Vie privée

- Seul le total de requêtes bloquées est écrit (SharedPreferences `vylia_shield`, effacé par « Supprimer mes
  données ») ; les domaines bloqués ne restent qu'en mémoire (20 au plus) et disparaissent à la fermeture de l'app.
- Aucune nouvelle permission déclarée : `BIND_VPN_SERVICE` protège le service (seul Android peut s'y lier), ce
  n'est pas une permission demandée par l'app. INTERNET l'était déjà. Le score de VYLIA par elle-même ne change pas.
- Pas de service au premier plan : Android se lie lui-même au service VPN, ce qui le garde en vie. À confirmer sur
  la durée sur téléphone (voir ci-dessous) ; sinon, ajouter `startForeground` avec le type `systemExempted`.

## Limites connues (affichées à l'utilisateur quand c'est utile)

- **DNS privé réglé sur un service** (mode « nom d'hôte ») : Android envoie ses requêtes en DoT directement à ce
  service, le bouclier n'en voit aucune. L'écran le signale ; il faut choisir l'un ou l'autre. En mode
  « Automatique » (réglage d'origine), Android essaie le DoT vers le serveur virtuel, échoue et repasse en UDP.
- **Un seul VPN à la fois** sous Android : activer le bouclier remplace un autre VPN (averti) ; un autre VPN qui
  s'active arrête le bouclier (état « Arrêté », `onRevoke`).
- Les apps qui font leur propre DNS chiffré (DoH de certains navigateurs) ou visent une IP en dur y échappent.
- Le repli TCP du DNS (réponses tronquées) n'est pas transmis.
- Redémarrage : le bouclier ne se relance pas seul ; « VPN permanent » d'Android le fait (`SUPPORTS_ALWAYS_ON`).
  « Bloquer les connexions sans VPN » doit rester décoché : seul le DNS passe par le tunnel, tout le reste serait
  coupé (et VYLIA, exclue du tunnel, ne joindrait plus le DNS du réseau). L'écran le dit.

## État et suite

Fait : tunnel, filtrage, transmission, compteur en direct, derniers blocages, badge, effacement, avertissements,
tests unitaires (liste, paquets, badge), essai sur téléphone (accord VPN, démarrage, arrêt, avertissement DNS privé).

À faire :

1. ~~Validation sur téléphone~~ (fait le 2026-11-01, après correction de la transmission : le réseau par défaut
   renvoyait le tunnel lui-même) : blocage, résolution, State.io et Mr Autofire OK. Reste : consommation sur une
   journée, passage Wi-Fi ↔ 4G, `app-measurement.com` (Firebase Analytics) absent des listes.
2. Liste d'exceptions par l'utilisateur (« ne plus bloquer ce domaine ») depuis les derniers blocages.
3. Crédits : ajouter EasyPrivacy (GPL v3) dans À propos à côté d'Exodus ; mentionner le bouclier dans la politique
   de confidentialité.
4. Option : EasyList (publicités) en plus d'EasyPrivacy ; mise à jour de la liste sur demande, comme la liste DNS.
5. Version 0.9.0 une fois validé.
