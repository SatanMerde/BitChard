# 🏛️ Architecture & Déploiement : Serveur Discord "SatanMerde Open Source Hub"

Ce document détaille l'architecture complète, la structure des catégories, salons, rôles, permissions et bots à déployer pour votre communauté Discord dédiée à l'ensemble de vos projets GitHub (**BitChard**, **D-moniakPatches**, **SteaMidra**, **BulletDroid2**, **Movix**, **SpotiFiak**, etc.).

---

## 🎭 1. Hiérarchie des Rôles & Permissions

| Rôle | Couleur | Description & Permissions |
| :--- | :---: | :--- |
| **👑 Fondateur / Créateur** | `#E53935` | Vous-même. Administrateur absolu (`Administrator`). |
| **🛡️ Co-Dev & Maintainers** | `#FF9800` | Développeurs de confiance et mainteneurs de modules (`Manage Channels`, `Kick/Ban`, `Manage Messages`). |
| **🤖 Bots & Automations** | `#607D8B` | Rôle technique pour Carl-bot, Discohook, GitHub Webhook Bot, etc. |
| **🧪 VIP / Beta-Testeurs** | `#9C27B0` | Testeurs clés des APKs et builds sideloadés avant release officielle. Accès aux canaux `#alpha-beta-drops`. |
| **⭐ Contributors** | `#00E676` | Personnes ayant ouvert une PR fusionnée sur l'un des repos GitHub. |
| **🎵 BitChard Fan** | `#FF1744` | Rôle auto-assignable (réaction) pour les utilisateurs de l'app BitChard & BitG. |
| **🦈 D-moniak Fan** | `#2979FF` | Rôle auto-assignable pour les utilisateurs des patchs Morphe/ReVanced. |
| **🎮 Gaming & Tools Fan** | `#FFD600` | Rôle auto-assignable pour SteaMidra, Millennium, BulletDroid, Citra. |
| **🍿 Streaming Fan** | `#00E5FF` | Rôle auto-assignable pour Movix, stream-fusion, CloudStream, Stremio. |
| **👥 Membres** | `#ECEFF1` | Rôle de base accordé automatiquement après acceptation des règles (`Verify`). |

---

## 📁 2. Arborescence des Salons & Catégories

### 📌 1. BIENVENUE & INFOS (Public - Lecture seule)
- `📢・annonces` : Annonces officielles de la communauté et du dev.
- `📜・règlement` : Règles de vie du serveur, charte éthique et avertissements légaux (projets éducatifs/open-source).
- `✨・auto-rôles` : Panneau interactif avec boutons/réactions pour choisir les projets qui intéressent chaque membre.
- `🔗・liens-officiels` : Liens vers vos 33 dépôts GitHub, documentation, Releases et profils.

---

### 🌐 2. HUB COMMUNAUTAIRE (Général)
- `💬・général` : Discussions globales autour du dev, de la tech et des projets.
- `💡・boîte-à-idées` : Suggestions générales pour l'ensemble des dépôts.
- `🤖・commandes-bots` : Espace dédié aux commandes des bots.
- `🔊・Vocal Général` : Salon vocal pour échanger librement.

---

### 🎵 3. BITCHARD & AUDIO (Dédié à BitChard & Music Apps)
*Concerne : BitChard, SpotiFLAC-Mobile, SpotiFiak, spotube, Stash.*
- `📢・bitchard-news` : Annonces dédiées aux versions de BitChard (v1.8.7, futures v1.9, etc.).
- `🤖・bitg-ai-dj` : Discussions et partages de prompts / configurations pour le DJ IA BitG (Gemini, Claude, DeepSeek).
- `🎧・bitchard-support` : Aide à l'installation des APKs (universal, arm64), synchronisation Spotify/Deezer/Apple Music.
- `🐛・bitchard-bug-reports` : Signalement de bugs ou crashs sur l'app.
- `💡・bitchard-suggestions` : Demandes d'ajouts (lecteur, paroles, intégrations).
- `🧪・bitchard-betas` : Salons réservés aux testeurs des APKs en préversion.

---

### 🦈 4. D-MONIAK & ANDROID MODS (Patchs & Tools)
*Concerne : D-moniakPatches, ReVanced, Morphe, uYouEnhanced, vyxel-apps, AuroraStore.*
- `📢・dmoniak-releases` : Sorties des nouveaux patchs Morphe/ReVanced.
- `🛠️・patchs-discussion` : Discussions techniques sur le framework Morphe et le reverse-engineering Android.
- `🐛・dmoniak-bugs` : Rapports sur les patchs incompatibles ou bugs d'injection.
- `💡・nouvelles-cibles` : Suggestions d'applications à patcher.

---

### 🎮 5. GAMING, STEAM & UTILITIES
*Concerne : SteaMidra, Millennium, BulletDroid2, Amethyst-Android, azahar/citra, Winhance.*
- `🎮・steamidra-tools` : Support et discussions autour de SteaMidra (manifests, Lua, DLCs, Steam modding).
- `🔫・bulletdroid-configs` : Échange de configurations et scripts d'exécution mobile.
- `🕹️・emulation-gaming` : Amethyst (Minecraft), azahar/citra (3DS) et optimisation Windows (Winhance).

---

### 🍿 6. STREAMING & EXTENSIONS
*Concerne : Movix-CloudStream, stream-fusion (Stremio), TheFrenchPluginNuvio, streamflix.*
- `🎬・movix-news` : Mises à jour des providers et extensions CloudStream.
- `⚡・stream-fusion` : Discussions autour de l'addon Stremio, des flux et du débridage.
- `📺・plugins-fr` : Nuvio & sources francophones de streaming.

---

### 💻 7. GITHUB SYNC (Automatisé via Webhooks)
- `🚀・github-releases` : Reçoit instantanément une notification dès qu'une Release GitHub est publiée (ex: BitChard v1.8.7).
- `📦・github-commits` : Flux des commits poussés sur les repos principaux (`main`).
- `🎯・github-issues` : Alertes quand un utilisateur ouvre ou ferme une Issue GitHub.

---

## 🤖 3. Configuration des Bots Recommandés

1. **GitHub Discord Webhook (Intégré, sans bot externe)** :
   - Très léger, 100% officiel, aucun bot tiers requis.
   - Créez un Webhook dans `#github-releases` et ajoutez-le dans GitHub Repo (`Settings -> Webhooks`).
2. **Carl-bot** ou **Ticket Tool** :
   - Gestion des rôles par réaction dans `#✨・auto-rôles`.
   - Système de tickets privés pour le support technique ou les bugs sensibles.
3. **AutoMod (Discord Native)** :
   - Filtrage anti-spam, blocage des liens malveillants et invitations Discord non autorisées.
4. **Mee6** ou **Arcane** :
   - Message de bienvenue stylisé avec les liens vers GitHub et attribution automatique du rôle `Membres`.
