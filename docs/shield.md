# Bouclier VYLIA (V2) — VPN local de filtrage DNS

Filtrer › Filtrage DNS : un interrupteur qui bloque, sur le téléphone, les requêtes DNS vers les serveurs de traçage,
avec un compteur en direct. Débloque le badge 🧿 « Bouclier actif ».

**Des traqueurs, pas des publicités** (depuis le 2026-11-14) : Google Play interdit aux apps de gêner l'affichage des
pubs d'autres apps. Le bouclier n'embarque donc aucune liste de publicité (EasyList, AdGuard DNS filter, Mobile Ads et
French retirées) et ne bloque pas les régies des apps (traqueurs Exodus « Advertisement », Google AdMob, DoubleClick).
C'est le positionnement d'App Tracking Protection de DuckDuckGo. Une seule version de l'app, Play comme GitHub ; le
filtrage des régies pourra revenir plus tard en option désactivée d'office (`ShieldFilter.AVAILABLE`).

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
  (`filesDir/shield/<id>.gz`), puis `BlocklistBuilder` refait la fusion du script et écrit `compiled2.bin`. `compiled.bin`, compilé avant le
  2026-11-14 avec les listes de publicité, et les extraits des listes retirées (`ShieldListStore.RETIRED`) sont
  effacés au démarrage : la liste embarquée reprend la main jusqu'à la prochaine mise à jour.
  `ShieldController.rebuild()` remplace la liste à chaud : le tunnel n'est pas coupé.

Sources :

| Liste | Licence | Domaines DNS | Rôle |
|---|---|---|---|
| Exodus Privacy (`network_signature`) | ODbL | ~540 | traqueurs nommés ; depuis la base Exodus téléchargée par l'app (`ExodusShieldDomains`), catégories complétées par `exodus_category_overrides.json` |
| EasyPrivacy | GPL v3 / CC BY-SA 3.0 | 43 351 | pistage des sites et des apps |
| AdGuard CNAME trackers | MIT | 306 600 | **proposée** (2,4 Mo compressés) : traqueurs déguisés en sous-domaine, +10 000 domaines |

Retirées le 2026-11-14 (listes de publicité) : AdGuard DNS filter (189 910 domaines, qui reprend EasyList), EasyList,
AdGuard Mobile Ads et AdGuard French. Mesure sur un téléphone de 108 apps : score de l'appareil avec bouclier 83 → 79
(74 sans), traqueurs bloqués 253 → 142 sur 442 ; la perte vient des régies (groupe mobile : −3 points), EasyList et
le DNS filter ne changeaient rien au score. Les jeux et les apps d'info perdent le plus (State.io 75 → 42).

Écartées : Tracking Protection et Base (déjà compilées dans le DNS filter : 103 et 19 domaines en plus), URL
Tracking (paramètres d'URL, rien au niveau DNS), Social Media et Fanboy Social (cosmétiques), Annoyances / Cookies
(cosmétiques), Liste FR (doublon d'AdGuard French). Aucune liste de contrôle parental : le bouclier bloque des
traqueurs, jamais un site pour son contenu (légal).

Règles retenues : `||domaine^`, `||domaine^$important`, hosts, domaine seul ; `$badfilter` annule la règle visée ;
`$third-party` et les autres modificateurs sont écartés (au niveau DNS, ils bloqueraient le site lui-même). Les
exceptions (`@@||d^`, `@@||d^|`, `@@|d^|`) de toutes les listes protègent le domaine et ses sous-domaines, même si un
parent est listé ; `NEVER_BLOCK` protège les plateformes ; `NEVER_BLOCK_ADS` (AdMob, DoubleClick) est toujours en
exception, sous-domaines compris, même si EasyPrivacy les cite.

Mémoire : `DomainHashSet` (empreintes FNV-1a 64 bits triées) : 215 000 domaines en 1,9 Mo au lieu d'environ 25 Mo,
libérés à l'arrêt du bouclier. Total au 2026-11-14 : 43 540 domaines web, aucun mobile.

Les domaines bloqués sans traqueur nommé sont nommés dans l'app grâce à AdGuard companiesdb (CC BY-SA 4.0,
téléchargée seulement) : « Criteo · ciblage ».

Les versions de développement journalisent les domaines autorisés (`adb logcat -s VyliaShield`) : c'est ainsi qu'on
repère un traqueur oublié.

## Groupes de la liste

La liste (format 2) range chaque domaine dans un groupe :

- **web** : pistage des sites, mesure d'audience et profilage des apps (EasyPrivacy, Exodus hors publicité,
  `app-measurement.com`). Gêne rarement les apps. C'est le seul filtrage proposé (`ShieldFilter.AVAILABLE`) : la
  section « Que filtrer ? » de la carte du bouclier ne s'affiche pas.
- **mobile** : vide. Les traqueurs Exodus « Advertisement » sont rangés à part pour en être exclus : ni eux ni leurs
  sous-domaines ne restent dans le groupe web, même si EasyPrivacy les cite. Le format et la recherche
  (`Blocklist.match`, le filtrage mobile l'emporte) restent en place pour une future option.
- **spyware** : indicateurs MVT, toujours bloqués ; **allow** : exceptions, dont `NEVER_BLOCK_ADS`.

## Le bouclier dans le score

Bouclier actif (ni arrêté, ni en pause hors délai, ni contourné), le score de chaque app compte ce qui sort encore
de ce téléphone ; le score sans bouclier reste affiché (fiche, en-tête d'Analyse) : c'est celui de l'app elle-même,
comme dans le classement public. Les permissions ne changent pas.

- Pour chaque traqueur, `TrackerCoverages` compare ses domaines (`network_signature` Exodus, plus le complément
  maison) à la liste en vigueur et aux filtrages actifs : **bloqué** (tous), **en partie**, **non bloqué**, ou
  **aucun serveur connu** (pas de signature réseau : 172 traqueurs Exodus sur 432 au 2026-11-13). Une partie de
  signature qui n'est pas un domaine (adresse IP) compte comme non bloquée ; un domaine de `NEVER_BLOCK` aussi.
- `ExodusShieldDomains.KNOWN_SERVERS` : serveurs connus de traqueurs sans signature utilisable (Amazon Ads, Pangle,
  Appodeal, BidMachine, ironSource, Singular, Adobe, Nielsen ; AdMob à la place de son expression régulière ;
  `an.facebook.com` pour Facebook Ads). Pour le score seulement : jamais ajoutés à la liste, le bouclier bloque la
  même chose avec ou sans eux. Ceux des régies ne sont pas bloqués (le score les compte en entier, « non bloqué »
  plutôt que « aucun serveur connu »), les autres le sont déjà par les listes (test). Mesure du 2026-11-14 sur 108 apps :
  bloqués 53 → 59 traqueurs, sans serveur connu 20 → 12, score de l'appareil avec bouclier 81 → 83 (74 sans).
- Liste d'apps de DuckDuckGo (tracker-blocklists) écartée : licence CC BY-NC-SA 4.0 (non commerciale, incompatible
  avec la GPL et F-Droid), 87 % de ses domaines déjà bloqués, et le reste suppose des exceptions par app que le
  bouclier, qui ne voit que le DNS, ne peut pas faire (`connect.facebook.net`, `s.pinimg.com`, Braze…).
- `PrivacyScorer.withShield(coverage)` : bloqué × `scoring.shield.blocked_factor` (0,3), en partie ×
  `partial_factor` (0,7), le reste en entier. La pénalité des traqueurs, déjà plafonnée (60), baisse dans la même
  proportion que leur poids : sinon une app très pistée (16 traqueurs, ~120 pts) resterait au plafond même avec la
  moitié bloquée. Plafond de sécurité comme pour le score. `ScoreResult.scoreWithoutShield` garde le score sans.
  Un traqueur bloqué compte encore : le SDK collecte toujours sur le téléphone, envoie plus tard ce qu'il a gardé
  (pause, redémarrage, autre VPN), et certaines apps contournent le DNS.
- Un seul moteur partout (`AppContainer.scorer`, via `ShieldExposureRepository.apply`) : listes, fiche, gains de
  Gérer, Suivi, alertes, scan de fond (qui attend la couverture, 10 s au plus). Activer, arrêter ou contourner le
  bouclier renote les apps en mémoire, sans nouveau scan.
- Suivi : l'état du bouclier est gardé avec chaque point (`databases["shield"]`, sans compter comme une mise à jour
  des bases). Arrêté depuis le point précédent, il est la cause d'une baisse (`DropCause.Type.SHIELD_OFF`, la
  notification ouvre Filtrer) si aucun changement d'app n'en explique la moitié.
- Bouclier arrêté : la fiche et l'en-tête montrent « Si vous activez le bouclier : … » et un lien vers Se protéger.
- Gérer › Traqueurs (`ShieldLeaks`) : les apps dont des traqueurs passent malgré le bouclier (en partie, pas du
  tout, ou sans serveur connu), la plus exposée d'abord, avec Désinstaller et, s'il y en a, les alternatives.
- Aide › Score : section « Le bouclier dans le score », facteurs lus dans les règles.
- Les domaines viennent de la base Exodus téléchargée, sinon de `exodus_trackers.json`, qui garde désormais
  `network_signature`.

## Pause réglable

Certaines fonctions des apps dépendent d'un serveur que le bouclier bloque. La pause laisse le VPN en place mais
transmet tout, sans rien compter, puis le filtrage reprend seul à l'heure dite : rien à relancer (Android limite les
démarrages de service en arrière-plan), pas de risque d'oublier de réactiver. Durée choisie avec − et + (5, 10, 15,
30, 45, 60, 90, 120 min), mémorisée ; pendant une pause, − et + la raccourcissent ou la prolongent. L'écran explique
quand s'en servir : code promo / cashback / parrainage non crédité, et diagnostic d'une app qui ne marche plus (si la
pause règle le problème, les derniers blocages montrent le serveur).

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
4. ~~EasyList~~, ~~régies mobiles~~, ~~pause~~, ~~mise à jour des listes depuis l'app~~ (faits, 0.9.5). EasyList, les
   filtres AdGuard de publicité et les régies retirés le 2026-11-14 (Google Play).
6. Filtrage des régies en option, désactivée d'office, une fois l'app acceptée sur Google Play (voir en tête).
5. Version 0.9.0 une fois validé.
