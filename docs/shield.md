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

Le domaine et les données vivent dans le module Gradle `:shield`, qui ne dépend pas de `:app` : l'application
implémente `ShieldHost` (elle fournit le `ShieldController`) et passe le rappel du premier blocage (badge). Le module
déclare lui-même ses services dans son manifeste. Une variante sans bouclier (Google Play) retirera ce module et
l'interface qui s'en sert (`ui/shield`, `ui/act/ShieldCard.kt`, onglet DNS privé).

| Couche | Fichier | Rôle |
|---|---|---|
| domaine (pur, testé) | `domain/shield/Blocklist.kt` | liste, recherche par suffixe (`a.ads.com` bloqué par `ads.com`, jamais le TLD) |
| | `domain/shield/DnsPacket.kt` | `DnsQuery` : lecture IPv4/UDP/DNS bornée, réponse NXDOMAIN, paquet de retour |
| | `domain/shield/ShieldState.kt` | état, compteurs, derniers blocages (mémoire seulement) |
| données | `data/shield/ShieldVpnService.kt` | le `VpnService` |
| | `data/shield/ShieldController.kt` | état partagé UI ↔ service ↔ tuile, total persisté, accord VPN |
| | `data/shield/ShieldTileService.kt` | tuile des réglages rapides : même état et même appui que la pastille |
| | `data/shield/ShieldHost.kt` | contrat avec l'app hôte |
| interface (`:app`) | `ui/shield/ShieldControl.kt`, `ui/act/ShieldCard.kt` | interrupteur, compteur, avertissements, derniers blocages |
| outil | `tools/update_shield_blocklist.py` | génère `shield/src/main/assets/shield_blocklist.json` |

## Liste de blocage

Deux chemins, qui produisent la même liste :

- **Liste embarquée** : `python3 tools/update_shield_blocklist.py` (poste de dev) produit
  `shield/src/main/assets/shield_blocklist.json`, utilisée tant qu'aucune liste n'a été téléchargée.
- **Listes téléchargées par l'app** (Paramètres › Bases de données, « Tout mettre à jour », ou avant le scan
  périodique) : `ShieldListStore` analyse chaque liste (`AdblockListParser`), n'en garde qu'un extrait compressé
  (`filesDir/shield/<id>.gz`), puis `BlocklistBuilder` refait la fusion du script et écrit `compiled.bin`.
  `ShieldController.rebuild()` remplace la liste à chaud : le tunnel n'est pas coupé.

Sources (mesures du 2026-11-08) :

| Liste | Licence | Domaines DNS | Rôle |
|---|---|---|---|
| Exodus Privacy (`network_signature`) | ODbL | ~540 | traqueurs nommés ; depuis la base Exodus téléchargée par l'app (`ExodusShieldDomains`), catégories complétées par `exodus_category_overrides.json` |
| AdGuard DNS filter | GPL v3 | 189 910 | base, compilée par AdGuard pour le DNS |
| EasyList, EasyPrivacy | GPL v3 / CC BY-SA 3.0 | 44 851, 43 351 | EasyPrivacy apporte 11 000 domaines absents du DNS filter |
| AdGuard Mobile Ads | GPL v3 | 925 | définit le groupe **mobile** |
| AdGuard French | GPL v3 | 5 308 | sites français |
| AdGuard CNAME trackers | MIT | 306 600 | **proposée** (2,4 Mo compressés) : +10 000 domaines |

Écartées : Tracking Protection et Base (déjà compilées dans le DNS filter : 103 et 19 domaines en plus), URL
Tracking (paramètres d'URL, rien au niveau DNS), Social Media et Fanboy Social (cosmétiques), Annoyances / Cookies
(cosmétiques), Liste FR (doublon d'AdGuard French). Aucune liste de contrôle parental : le bouclier bloque des
traqueurs, jamais un site pour son contenu (légal).

Règles retenues : `||domaine^`, `||domaine^$important`, hosts, domaine seul ; `$badfilter` annule la règle visée ;
`$third-party` et les autres modificateurs sont écartés (au niveau DNS, ils bloqueraient le site lui-même). Les
exceptions (`@@||d^`, `@@||d^|`, `@@|d^|`) de toutes les listes protègent le domaine et ses sous-domaines, même si un
parent est listé ; `NEVER_BLOCK` protège les plateformes.

Mémoire : `DomainHashSet` (empreintes FNV-1a 64 bits triées) : 215 000 domaines en 1,9 Mo au lieu d'environ 25 Mo,
libérés à l'arrêt du bouclier. Total au 2026-11-08 : 205 036 domaines web, 1 046 mobiles.

Les domaines bloqués sans traqueur nommé sont nommés dans l'app grâce à AdGuard companiesdb (CC BY-SA 4.0,
téléchargée seulement) : « Criteo · ciblage ».

Les versions de développement journalisent les domaines autorisés (`adb logcat -s VyliaShield`) : c'est ainsi qu'on
repère une régie oubliée en jouant à un jeu.

## Filtrage web et filtrage mobile

La liste (format 2) range chaque domaine dans un groupe, activable séparément (au moins un reste actif) :

- **mobile** : régies publicitaires des apps et des jeux (AdGuard Mobile Ads, traqueurs Exodus « Advertisement ») ;
  les bloquer coupe aussi les vidéos récompensées ;
- **web** : tout le reste, soit pubs et pistage des sites (EasyList, EasyPrivacy) et mesure d'audience / profilage
  des apps (Exodus hors publicité, `app-measurement.com`). Gêne rarement les apps.

Le réglage mobile l'emporte : un domaine présent dans les deux (Google AdMob, DoubleClick), ou dont un parent est
mobile, passe quand le filtrage mobile est coupé. C'est ce qui permet à un joueur de garder le filtrage web sans
pauses répétées. Au 2026-11-01 : 86 186 domaines web, 1 044 mobile.

## Pause réglable

Les vidéos récompensées sont des pubs servies par les régies bloquées : elles tournent dans le vide (constaté sur
State.io et Mr Autofire). La pause laisse le VPN en place mais transmet tout, sans rien compter, puis le filtrage
reprend seul à l'heure dite : rien à relancer (Android limite les démarrages de service en arrière-plan), pas de
risque d'oublier de réactiver. Durée choisie avec − et + (5, 10, 15, 30, 45, 60, 90, 120 min), mémorisée ; pendant
une pause, − et + la raccourcissent ou la prolongent. L'écran explique quand s'en servir : vidéo récompensée (dans
un jeu par exemple), lien d'e-mail ou de pub suivi, code promo / cashback / parrainage, résultat sponsorisé, et
diagnostic d'une app qui ne marche plus (si la pause règle le problème, les derniers blocages montrent le serveur).

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
3. ~~Crédits~~ (faits). Politique de confidentialité (vylia-site) : dire que le bouclier télécharge ses listes.
4. ~~EasyList~~, ~~régies mobiles~~, ~~pause~~, ~~mise à jour des listes depuis l'app~~ (faits, 0.9.5).
5. Version 0.9.0 une fois validé.
