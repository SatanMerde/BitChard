<div align="center">

<br/>
<br/>

<img src="Logo.png" alt="BitChard app icon" width="200" />

# BitChard

### The Enhanced, Aesthetic YouTube Music Client
*A feature-rich, standalone fork of [BitChord](https://github.com/kushagrasinghx/BitChord)*

<br/>

[![GitHub Repository](https://img.shields.io/badge/GitHub-SatanMerde%2FBitChord-blue?style=for-the-badge&logo=github)](https://github.com/SatanMerde/BitChord)
[![License](https://img.shields.io/github/license/SatanMerde/BitChord?style=for-the-badge&labelColor=0d1117)](https://github.com/SatanMerde/BitChord/blob/main/LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Desktop-orange?style=for-the-badge)](https://github.com/SatanMerde/BitChord)

<br/>

[**Ce qui change / What's New**](#ce-qui-change--whats-new) · [**Features**](#features) · [**Download**](#download) · [**AI DJ**](#ia-dj--ai-dj) · [**Paroles / Lyrics**](#paroles-synchronis%C3%A9es--lyrics) · [**Contributing**](#contributing) · [**Disclaimer**](#disclaimer)

</div>

> [!IMPORTANT]
> **BitChard** is an independent fork created by [SatanMerde](https://github.com/SatanMerde/BitChord). It is designed to run independently from the original BitChord with major UX enhancements, smarter playlist management, AI DJ and advanced syllable-level lyrics. BitChard is not affiliated with Google LLC, YouTube Music, or Spotify.

---

<div align="center">

<img src="Banner.png" alt="BitChard banner" width="100%" />

<h1><a id="ce-qui-change--whats-new"></a>✨ Ce qui change dans BitChard / What makes BitChard different</h1>

</div>

### 🇫🇷 En français

| Fonctionnalité | Description dans BitChard |
|---|---|
| **Coexistence BitChord / BitChard** | Identifiant de package unique (`com.music.bitchard`) et nom dédié. Vous pouvez installer et utiliser **BitChord et BitChard côte à côte** sur le même téléphone sans écrasement ni conflit. |
| **Gestion de la barre de volume** | Nouveau commutateur dans *Paramètres → Apparence*. Lorsqu'elle est désactivée, la barre de volume disparaît proprement et l'interface s'ajuste dynamiquement sans laisser de trou vide. |
| **Playlists importées 100% modifiables** | Modifiez librement vos playlists importées (locales ou Spotify) : **renommer**, **réordonner les titres**, **ajouter des chansons** ou **retirer des morceaux**. Elles sont également intégrées au sélecteur *Ajouter à une playlist*. |
| **Résolution & Correspondance Spotify de haute précision** | Finies les erreurs de morceaux (covers, remix non désirés) et les « pistes introuvables ». Le nouveau moteur de recherche nettoie les titres et bascule automatiquement sur les flux vidéo/audio officiels de YouTube Music. |
| **Compteur d'écoutes des artistes** | Le nombre d'écoutes réelles (*ex: 2.4M écoutes*) s'affiche directement sous chaque titre populaire sur les pages de profil des artistes. |
| **Importation groupée Spotify en 1 clic** | Bouton et interface dédiée **« Tout importer »** dans l'onglet Spotify pour synchroniser en une seule fois toutes vos playlists et tous vos artistes suivis vers vos playlists locales ou votre compte YouTube Music. |
| **IA DJ Intelligente (style Spotify DJ)** | Un DJ virtuel interactif doté de synthèse vocale, d'annonces personnalisées et de mix dynamiques basés sur vos morceaux préférés, vos artistes favoris et des découvertes inédites. |
| **Paroles synchronisées mot à mot / syllabe par syllabe** | Intégration de nouvelles sources de paroles hyper-précises (syllabe et mot à mot, dont NetEase Cloud Music YRC et Musixmatch RichSync) pour un karaoké fluide et ultra-synchronisé. |

---

### 🇬🇧 In English

| Feature | Description in BitChard |
|---|---|
| **Side-by-side Coexistence** | Distinct application ID (`com.music.bitchard`) and dedicated app name. Install and run **BitChord and BitChard concurrently** on the same Android device with zero conflict. |
| **Player Volume Bar Toggle** | Switch in *Settings → Appearance*. When turned off, the volume row collapses cleanly without leaving awkward empty gaps in the player. |
| **Fully Editable Imported Playlists** | Rename, reorder, delete, and add or remove tracks from imported playlists (local storage or Spotify) directly from the app interface. |
| **High-Precision Track Matching** | Eliminates wrong matches (remixes, karaokes) and false "track not found" errors. Features smart query sanitization, fallback to YouTube video streams, and scoring against official audio. |
| **Artist Top Tracks Play Counts** | Displays stream counts (*e.g., 2.4M plays*) alongside popular tracks on artist profile pages. |
| **1-Click Spotify Batch Import** | Batch import all your Spotify playlists and followed artists in a single action with real-time progress indicators. |
| **Smart AI DJ (Spotify-style DJ)** | Interactive virtual DJ featuring voice commentary (TTS), thematic song blocks, smooth transitions, and on-demand mood switching. |
| **Word & Syllable-Level Synced Lyrics** | High-precision word-by-word and syllable-by-syllable synchronized lyrics powered by extended sources including NetEase YRC and Musixmatch RichSync. |

---

<div align="center">

<h1><a id="features"></a>Gamme complète de fonctionnalités / All Features</h1>

<table>
  <tr>
    <td width="50%" valign="top">

#### 🎵 Lecture & Audio
- **Streaming YouTube Music complet** sans publicité ni interruption.
- **Audio Hi-Res Lossless** — flux FLAC/ALAC depuis des modules configurés avec repli YouTube Music.
- **Fondu enchaîné & Automix** avec transitions au tempo et crossfade personnalisable (0 à 12s).
- **Téléchargements hors ligne** avec métadonnées et pochettes intégrées.
- **Égaliseur système & Traitement audio haute précision**.
- **Statistiques audio avancées** (codecs, fréquence, débit, perte).

#### 🔗 Comptes & Écosystème
- **Connexion Google / YouTube Music** pour synchroniser votre bibliothèque personnelle.
- **Intégration Spotify complète** : lecture des playlists, titres aimés et import en masse.
- **Discord Rich Presence** avec affichage du titre en cours et progression.
- **Scrobbling Last.fm & ListenBrainz**.
- **Listen Together (Écoute partagée)** en temps réel avec vos amis.

    </td>
    <td width="50%" valign="top">

#### 🎨 Interface & Expérience
- **Pochettes animées Canvas** (Spotify Canvas & Apple Music Motion).
- **Paroles animées style Apple Music** avec défilement fluide et surlignage mot à mot.
- **Thème dynamique Material You** s'adaptant automatiquement aux couleurs de la pochette.
- **Effet verre dépoli Haze (Glassmorphism)** fluide et réactif.
- **Navigation flottante** et gestuelle moderne.

#### ⚙️ Contrôles & Personnalisation
- **Affichage modulable du lecteur** (masquage de la barre de son, contrôles compacts).
- **Gestionnaire de playlists locales et hybrides**.
- **Minuteur de sommeil** (après le morceau ou durée personnalisée).
- **Vitesse de lecture ajustable** (0.5× à 2.0×) et saut automatique des silences.

    </td>
  </tr>
</table>

</div>

---

<div align="center">

<h1><a id="download"></a>Téléchargement / Download</h1>

Les versions APK compilées sont disponibles dans les [Releases du dépôt](https://github.com/SatanMerde/BitChord/releases) :

- **`BitChard-universal-debug.apk`** : Compatible avec tous les appareils Android (téléphones, tablettes, émulateurs).
- **`BitChard-arm64-v8a-debug.apk`** : Version optimisée pour les smartphones 64-bit récents.

</div>

---

<div align="center">

<h1><a id="license"></a>Licence</h1>

BitChard est distribué sous licence **GNU General Public License v3.0 (GPLv3)**. Voir le fichier [LICENSE](LICENSE) pour plus de détails.

</div>
