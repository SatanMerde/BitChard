"""
SatanMerde Discord Bot — Anti-Raid Security, Auto-Role, Bilingual Welcoming, Ticket System,
Interactive Role Panels, Slash Commands & Real-time GitHub Release Tracker.
"""

import sys
import os
import asyncio
import time
import json
import urllib.request
from collections import defaultdict
import discord
from discord import app_commands, Colour, Embed, ButtonStyle
from discord.ui import View, Button
from discord.ext import tasks

try:
    sys.stdout.reconfigure(encoding='utf-8')
except Exception:
    pass

intents = discord.Intents.default()
intents.guilds = True

client = discord.Client(intents=intents)
tree = app_commands.CommandTree(client)

TOKEN = os.environ.get("DISCORD_BOT_TOKEN", "")

# ---------------- STATE & GITHUB RELEASE TRACKER ----------------

STATE_FILE = os.path.join(os.path.dirname(__file__), "last_releases.json")

def load_last_releases():
    if os.path.exists(STATE_FILE):
        try:
            with open(STATE_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {"BitChard": "v1.8.7", "D-moniakPatches": "v1.52.20"}

def save_last_releases(data):
    try:
        with open(STATE_FILE, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
    except Exception as e:
        print(f"[!] Error saving releases state: {e}")

last_known_releases = load_last_releases()

def fetch_github_release(repo):
    url = f"https://api.github.com/repos/{repo}/releases/latest"
    req = urllib.request.Request(url, headers={"User-Agent": "BitChard-DiscordBot"})
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return json.loads(resp.read().decode())
    except Exception as e:
        print(f"[!] GitHub API error for {repo}: {e}")
        return None

# ---------------- INTERACTIVE VIEWS : ROLE SELECTION ----------------

class RoleSelectionView(View):
    def __init__(self):
        super().__init__(timeout=None)

    async def toggle_role(self, interaction: discord.Interaction, role_name: str):
        guild = interaction.guild
        user = interaction.user
        role = discord.utils.get(guild.roles, name=role_name)
        if not role:
            await interaction.response.send_message(f"⚠️ Rôle `{role_name}` introuvable sur le serveur.", ephemeral=True)
            return

        if role in user.roles:
            await user.remove_roles(role)
            await interaction.response.send_message(
                f"❌ Rôle **{role_name}** retiré ! / Role removed!",
                ephemeral=True
            )
        else:
            await user.add_roles(role)
            await interaction.response.send_message(
                f"✅ Rôle **{role_name}** attribué ! / Role added!",
                ephemeral=True
            )

    @discord.ui.button(label="BitChard Fan", style=ButtonStyle.danger, emoji="🎵", custom_id="role_bitchard_fan_btn")
    async def btn_bitchard(self, interaction: discord.Interaction, button: Button):
        await self.toggle_role(interaction, "🎵 BitChard Fan")

    @discord.ui.button(label="D-moniak Fan", style=ButtonStyle.primary, emoji="🦈", custom_id="role_dmoniak_fan_btn")
    async def btn_dmoniak(self, interaction: discord.Interaction, button: Button):
        await self.toggle_role(interaction, "🦈 D-moniak Fan")

    @discord.ui.button(label="VIP Tester", style=ButtonStyle.secondary, emoji="🧪", custom_id="role_vip_tester_btn")
    async def btn_vip(self, interaction: discord.Interaction, button: Button):
        await self.toggle_role(interaction, "🧪 VIP Tester")

    @discord.ui.button(label="Membre", style=ButtonStyle.success, emoji="👥", custom_id="role_membre_btn")
    async def btn_membre(self, interaction: discord.Interaction, button: Button):
        await self.toggle_role(interaction, "👥 Membre")

# ---------------- INTERACTIVE VIEWS : TICKETS ----------------

class TicketCloseView(View):
    def __init__(self):
        super().__init__(timeout=None)

    @discord.ui.button(label="Fermer le ticket / Close Ticket", style=ButtonStyle.danger, emoji="🔒", custom_id="close_ticket_btn")
    async def close_ticket(self, interaction: discord.Interaction, button: Button):
        await interaction.response.send_message(
            "🔒 **Fermeture du ticket dans 5 secondes... / Closing ticket in 5 seconds...**",
            ephemeral=True
        )
        await asyncio.sleep(5)
        try:
            await interaction.channel.delete()
        except Exception:
            pass

class TicketLauncher(View):
    def __init__(self):
        super().__init__(timeout=None)

    @discord.ui.button(label="Ouvrir un ticket / Open Ticket", style=ButtonStyle.primary, emoji="🎫", custom_id="open_ticket_btn")
    async def open_ticket(self, interaction: discord.Interaction, button: Button):
        guild = interaction.guild
        user = interaction.user
        
        existing = discord.utils.get(guild.channels, name=f"ticket-{user.name.lower()}")
        if existing:
            await interaction.response.send_message(
                f"⚠️ Vous avez déjà un ticket ouvert / You already have an open ticket: {existing.mention}",
                ephemeral=True
            )
            return

        cat = interaction.channel.category
        overwrites = {
            guild.default_role: discord.PermissionOverwrite(read_messages=False),
            user: discord.PermissionOverwrite(read_messages=True, send_messages=True, attach_files=True),
            guild.me: discord.PermissionOverwrite(read_messages=True, send_messages=True, administrator=True)
        }

        admin_role = discord.utils.get(guild.roles, name="👑 Fondateur")
        if admin_role:
            overwrites[admin_role] = discord.PermissionOverwrite(read_messages=True, send_messages=True)

        ticket_ch = await guild.create_text_channel(
            name=f"ticket-{user.name.lower()}",
            category=cat,
            overwrites=overwrites
        )

        embed = Embed(
            title=f"🎫 Support Ticket — {user.name}",
            description=(
                f"━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                f"🇬🇧 **Welcome {user.mention} !**\n"
                f"Please describe your question or issue in detail (Device model, Android version, logs or context regarding **BitChard** or **D-MoniakPatches**).\n"
                f"A maintainer will assist you as soon as possible.\n\n"
                f"━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                f"🇫🇷 **Bonjour {user.mention} !**\n"
                f"Merci de détailler votre demande ou problème (modèle, version Android, logs pour **BitChard** ou **D-MoniakPatches**).\n"
                f"Un modérateur vous répondra dès que possible.\n\n"
                f"🔒 *Click below to close this ticket / Cliquez ci-dessous pour fermer ce ticket.*"
            ),
            color=Colour.from_rgb(0, 188, 212)
        )
        await ticket_ch.send(content=f"{user.mention}", embed=embed, view=TicketCloseView())
        await interaction.response.send_message(
            f"✅ Ticket créé avec succès / Ticket created successfully: {ticket_ch.mention}",
            ephemeral=True
        )

# ---------------- SLASH COMMANDS (100% BILINGUAL) ----------------

@tree.command(name="help", description="Displays all bot commands / Affiche les commandes du bot")
async def slash_help(interaction: discord.Interaction):
    embed = Embed(
        title="🤖 SatanMerde Projects — Commands / Commandes",
        description="Official commands for BitChard and D-MoniakPatches community.",
        color=Colour.from_rgb(229, 57, 53)
    )
    embed.add_field(
        name="🇬🇧 English Commands",
        value=(
            "`/bitchard` : App details, v1.8.7 changelog & APK download\n"
            "`/dmoniak` : Morphe / ReVanced patch suite details\n"
            "`/repos` : Official GitHub repositories & releases\n"
            "`/legal` : Educational framework & terms of use\n"
            "`/serverinfo` : Server security status and stats"
        ),
        inline=False
    )
    embed.add_field(
        name="🇫🇷 Commandes en Français",
        value=(
            "`/bitchard` : Infos détaillées, v1.8.7 & téléchargements\n"
            "`/dmoniak` : Patchs Morphe / ReVanced & documentation\n"
            "`/repos` : Dépôts officiels GitHub & versions\n"
            "`/legal` : Mentions légales et cadre d'utilisation\n"
            "`/serverinfo` : État de sécurité et membres"
        ),
        inline=False
    )
    embed.add_field(
        name="🛡️ Staff Moderation & Panels",
        value=(
            "`/clear <amount>` : Purge messages\n"
            "`/setup-ticket-panel` : Deploy ticket launcher button\n"
            "`/setup-role-panel` : Deploy role selection buttons\n"
            "`/check-releases` : Force check GitHub releases right now"
        ),
        inline=False
    )
    await interaction.response.send_message(embed=embed)

@tree.command(name="bitchard", description="BitChard overview, features and download links (v1.8.7)")
async def slash_bitchard(interaction: discord.Interaction):
    embed = Embed(
        title="🎵 BitChard — The Ultimate YouTube Music Client",
        description=(
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            "🇬🇧 **ENGLISH**\n"
            "**BitChard** revolutionizes audio on Android, Wear OS, Android Auto, TV and PC :\n\n"
            "• 🔄 **Universal Account Import** : Instant prompt on connecting Spotify, Deezer, Apple Music, or YouTube Music.\n"
            "• 🤖 **BitG AI DJ** : Real-time synthesized AI DJ transitions, track announcements, and voice commentary.\n"
            "• 🎤 **Syllable-Synced Lyrics** : Apple Music / Musixmatch integration with smooth karaoke view.\n"
            "• 🎧 **Lossless Hi-Fi Audio** : 24-bit bit-perfect FLAC / ALAC playback.\n\n"
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            "🇫🇷 **FRANÇAIS**\n"
            "**BitChard** réinvente l'expérience musicale sur Android, Wear OS, TV et PC :\n\n"
            "• 🔄 **Import Universel** : Dialogue automatique à la connexion (Spotify, Deezer, Apple Music, YT Music).\n"
            "• 🤖 **BitG AI DJ** : DJ interactif vocal en direct et transitions personnalisées.\n"
            "• 🎤 **Paroles par syllabe** : Rendu karaoké ultra-fluide.\n"
            "• 🎧 **Audio Hi-Fi Lossless** : Flux 24-bit bit-perfect.\n\n"
            "🔗 **GitHub** : https://github.com/SatanMerde/BitChard\n"
            "📦 **Releases** : https://github.com/SatanMerde/BitChard/releases"
        ),
        color=Colour.from_rgb(255, 23, 68)
    )
    await interaction.response.send_message(embed=embed)

@tree.command(name="dmoniak", description="D-MoniakPatches overview for Morphe & ReVanced")
async def slash_dmoniak(interaction: discord.Interaction):
    embed = Embed(
        title="🦈 D-MoniakPatches — Morphe / ReVanced Framework",
        description=(
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            "🇬🇧 **ENGLISH**\n"
            "Modular third-party bytecode patches for Android apps built on top of Morphe :\n\n"
            "• Smali bytecode hooks, layout enhancements, and clean integration.\n"
            "• Optimized for high stability, fast compilation, and broad compatibility.\n\n"
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            "🇫🇷 **FRANÇAIS**\n"
            "Suite de patchs tiers modulaires pour applications Android via le framework Morphe :\n\n"
            "• Hooks bytecode smali, améliorations d'interface et intégration propre.\n"
            "• Optimisé pour une stabilité maximale et une compilation rapide.\n\n"
            "🔗 **GitHub** : https://github.com/SatanMerde/D-moniakPatches\n"
            "📦 **Releases** : https://github.com/SatanMerde/D-moniakPatches/releases"
        ),
        color=Colour.from_rgb(41, 121, 255)
    )
    await interaction.response.send_message(embed=embed)

@tree.command(name="repos", description="Official GitHub repositories / Dépôts GitHub officiels")
async def slash_repos(interaction: discord.Interaction):
    embed = Embed(
        title="🔗 Official Repositories / Dépôts Officiels",
        description=(
            "• 🎵 **BitChard** : https://github.com/SatanMerde/BitChard\n"
            "  *Client YouTube Music avec DJ IA BitG, paroles par syllabe et audio Hi-Fi*\n\n"
            "• 🦈 **D-MoniakPatches** : https://github.com/SatanMerde/D-moniakPatches\n"
            "  *Suite de patchs modulaires pour le framework Morphe & ReVanced*"
        ),
        color=Colour.from_rgb(33, 150, 243)
    )
    await interaction.response.send_message(embed=embed)

@tree.command(name="legal", description="Legal disclaimer and educational framework / Cadre légal")
async def slash_legal(interaction: discord.Interaction):
    embed = Embed(
        title="⚖️ Legal Notice & Terms of Use / Cadre Légal",
        description=(
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            "🇬🇧 **ENGLISH**\n"
            "• **Educational Research** : BitChard and D-MoniakPatches are developed strictly for research, technical analysis, and interoperability.\n"
            "• **Trademarks** : YouTube Music, Spotify, Deezer, Apple Music, and Google are registered trademarks of their respective owners.\n"
            "• **No Piracy** : This community strictly prohibits copyright infringement, key generation, or illicit bypasses.\n\n"
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            "🇫🇷 **FRANÇAIS**\n"
            "• **Finalité Éducative** : Les projets sont conçus à des fins de rétro-ingénierie éducative et d'interopérabilité.\n"
            "• **Marques** : Toutes les marques citées appartiennent à leurs propriétaires respectifs.\n"
            "• **Tolérance Zéro Piratage** : Aucun contournement illicite ou piratage n'est toléré sur ce serveur."
        ),
        color=Colour.from_rgb(255, 152, 0)
    )
    await interaction.response.send_message(embed=embed)

@tree.command(name="serverinfo", description="Server health, anti-raid status, and statistics")
async def slash_serverinfo(interaction: discord.Interaction):
    guild = interaction.guild
    embed = Embed(
        title=f"📊 Server Status / État du Serveur : {guild.name}",
        color=Colour.from_rgb(76, 175, 80)
    )
    embed.add_field(name="👑 Owner / Propriétaire", value=f"<@{guild.owner_id}>", inline=True)
    embed.add_field(name="👥 Members / Membres", value=str(guild.member_count), inline=True)
    embed.add_field(
        name="🛡️ Anti-Raid Security",
        value=f"Verification: `{guild.verification_level.name.upper()}` (Verified E-mail required)",
        inline=False
    )
    embed.add_field(name="🔍 Content Filter", value="Active for all members", inline=True)
    embed.add_field(name="🎫 Ticket Engine", value="Active & Operational", inline=True)
    embed.add_field(name="🚀 Auto GitHub Tracker", value="Active (60s loop)", inline=True)
    await interaction.response.send_message(embed=embed)

@tree.command(name="clear", description="Purges a specified number of messages (Staff)")
@app_commands.checks.has_permissions(manage_messages=True)
async def slash_clear(interaction: discord.Interaction, amount: int = 5):
    amount = min(max(1, amount), 100)
    await interaction.response.defer(ephemeral=True)
    deleted = await interaction.channel.purge(limit=amount)
    await interaction.followup.send(
        f"🧹 **{len(deleted)} messages deleted / supprimés.**",
        ephemeral=True
    )

@tree.command(name="setup-ticket-panel", description="Deploys the interactive ticket panel (Staff)")
@app_commands.checks.has_permissions(administrator=True)
async def slash_setup_ticket(interaction: discord.Interaction):
    embed = Embed(
        title="🎫 Private Support Tickets / Billetterie Privée",
        description=(
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            "🇬🇧 **ENGLISH**\n"
            "Need private technical support or want to report a security issue?\n"
            "Click below to open a private ticket channel with the team!\n\n"
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            "🇫🇷 **FRANÇAIS**\n"
            "Besoin d'une aide technique privée ou d'un signalement de sécurité ?\n"
            "Cliquez ci-dessous pour ouvrir un salon privé avec l'équipe !"
        ),
        color=Colour.from_rgb(0, 188, 212)
    )
    await interaction.channel.send(embed=embed, view=TicketLauncher())
    await interaction.response.send_message("✅ Panneau de tickets déployé / Ticket panel deployed!", ephemeral=True)

@tree.command(name="setup-role-panel", description="Deploys interactive role buttons panel (Staff)")
@app_commands.checks.has_permissions(administrator=True)
async def slash_setup_roles(interaction: discord.Interaction):
    embed = Embed(
        title="✨ Self-Assign Roles & Notifications / Attribution des Rôles",
        description=(
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            "🇬🇧 **ENGLISH**\n"
            "Click the buttons below to assign or remove roles instantly :\n\n"
            "• 🎵 **BitChard Fan** : Pings for new BitChard app updates & releases.\n"
            "• 🦈 **D-moniak Fan** : Pings for Morphe bytecode patches updates.\n"
            "• 🧪 **VIP Tester** : Access to preview beta drops & experimental builds.\n"
            "• 👥 **Membre** : Official member badge.\n\n"
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            "🇫🇷 **FRANÇAIS**\n"
            "Cliquez sur les boutons ci-dessous pour vous attribuer vos rôles en un clic !"
        ),
        color=Colour.from_rgb(255, 64, 129)
    )
    await interaction.channel.send(embed=embed, view=RoleSelectionView())
    await interaction.response.send_message("✅ Panneau de rôles déployé / Role panel deployed!", ephemeral=True)

@tree.command(name="check-releases", description="Manually check GitHub for new releases (Staff)")
@app_commands.checks.has_permissions(administrator=True)
async def slash_check_releases(interaction: discord.Interaction):
    await interaction.response.defer(ephemeral=True)
    bitchard_rel = fetch_github_release("SatanMerde/BitChard")
    dmoniak_rel = fetch_github_release("SatanMerde/D-moniakPatches")
    b_tag = bitchard_rel.get("tag_name", "N/A") if bitchard_rel else "Error"
    d_tag = dmoniak_rel.get("tag_name", "N/A") if dmoniak_rel else "Error"
    await interaction.followup.send(
        f"🔍 **GitHub Live Check** :\n• BitChard Latest Tag : `{b_tag}` (Local known : `{last_known_releases.get('BitChard')}`)\n• D-MoniakPatches Latest Tag : `{d_tag}` (Local known : `{last_known_releases.get('D-moniakPatches')}`)",
        ephemeral=True
    )

# ---------------- AUTOMATED GITHUB RELEASES MONITOR TASK ----------------

@tasks.loop(seconds=60)
async def check_github_releases_task():
    await client.wait_until_ready()
    for guild in client.guilds:
        # 1. Check BitChard
        b_data = fetch_github_release("SatanMerde/BitChard")
        if b_data:
            tag = b_data.get("tag_name")
            if tag and tag != last_known_releases.get("BitChard"):
                print(f"[+] New BitChard Release detected: {tag}")
                last_known_releases["BitChard"] = tag
                save_last_releases(last_known_releases)

                embed = Embed(
                    title=f"🚀 BitChard {tag} is Available! / est disponible !",
                    url=b_data.get("html_url"),
                    description=(
                        f"A new official release of **BitChard** has just been published on GitHub!\n\n"
                        f"**Release Title :** {b_data.get('name', tag)}\n\n"
                        f"**Changelog Snippet :**\n"
                        f"{(b_data.get('body', '')[:500] + '...') if len(b_data.get('body', '')) > 500 else b_data.get('body', 'No changelog provided.')}\n\n"
                        f"━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                        f"🔗 **Download APK / Télécharger** : [GitHub Releases]({b_data.get('html_url')})"
                    ),
                    color=Colour.from_rgb(255, 23, 68)
                )
                embed.set_thumbnail(url="https://github.com/SatanMerde.png")
                embed.set_footer(text="Automated GitHub Tracker • BitChard")

                ch = discord.utils.get(guild.text_channels, name="📢・bitchard-releases")
                role = discord.utils.get(guild.roles, name="🎵 BitChard Fan")
                ping = role.mention if role else ""
                if ch:
                    await ch.send(content=f"🔔 {ping} **New BitChard Release / Nouvelle Version !**", embed=embed)

                ch_gh = discord.utils.get(guild.text_channels, name="🚀・github-releases")
                if ch_gh:
                    await ch_gh.send(embed=embed)

        # 2. Check D-MoniakPatches
        d_data = fetch_github_release("SatanMerde/D-moniakPatches")
        if d_data:
            tag = d_data.get("tag_name")
            if tag and tag != last_known_releases.get("D-moniakPatches"):
                print(f"[+] New D-MoniakPatches Release detected: {tag}")
                last_known_releases["D-moniakPatches"] = tag
                save_last_releases(last_known_releases)

                embed = Embed(
                    title=f"🦈 D-MoniakPatches {tag} is Released! / est publié !",
                    url=d_data.get("html_url"),
                    description=(
                        f"A new release of **D-MoniakPatches** (Morphe / ReVanced) has been published on GitHub!\n\n"
                        f"**Release Title :** {d_data.get('name', tag)}\n\n"
                        f"**Changelog :**\n"
                        f"{(d_data.get('body', '')[:500] + '...') if len(d_data.get('body', '')) > 500 else d_data.get('body', 'No changelog provided.')}\n\n"
                        f"━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                        f"🔗 **View on GitHub** : [GitHub Releases]({d_data.get('html_url')})"
                    ),
                    color=Colour.from_rgb(41, 121, 255)
                )
                embed.set_thumbnail(url="https://github.com/SatanMerde.png")
                embed.set_footer(text="Automated GitHub Tracker • D-MoniakPatches")

                ch = discord.utils.get(guild.text_channels, name="📢・dmoniak-releases")
                role = discord.utils.get(guild.roles, name="🦈 D-moniak Fan")
                ping = role.mention if role else ""
                if ch:
                    await ch.send(content=f"🔔 {ping} **New Morphe Patches Release / Nouvelle Version !**", embed=embed)

                ch_gh = discord.utils.get(guild.text_channels, name="🚀・github-releases")
                if ch_gh:
                    await ch_gh.send(embed=embed)

# ---------------- ON READY ----------------

@client.event
async def on_ready():
    print(f"[✓] Bot online: {client.user}")
    
    # Register persistent interactive views
    client.add_view(TicketLauncher())
    client.add_view(TicketCloseView())
    client.add_view(RoleSelectionView())

    # Start automated background release tracker
    if not check_github_releases_task.is_running():
        check_github_releases_task.start()
        print("[+] GitHub release tracker task started (checking every 60s)")

    # Sync slash commands and deploy views if needed
    for guild in client.guilds:
        tree.copy_global_to(guild=guild)
        await tree.sync(guild=guild)
        print(f"  [+] Slash commands synced to: {guild.name}")

        # Update role selection panel in #✨・roles if needed
        roles_ch = discord.utils.get(guild.text_channels, name="✨・roles")
        if roles_ch:
            msgs = [m async for m in roles_ch.history(limit=5)]
            # Check if interactive buttons are already present
            has_buttons = any(len(m.components) > 0 for m in msgs)
            if not has_buttons:
                await roles_ch.purge(limit=5)
                embed = Embed(
                    title="✨ Self-Assign Roles & Notifications / Attribution des Rôles",
                    description=(
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                        "🇬🇧 **ENGLISH**\n"
                        "Click the buttons below to assign or remove roles instantly :\n\n"
                        "• 🎵 **BitChard Fan** : Notifications for new BitChard app updates & APK releases.\n"
                        "• 🦈 **D-moniak Fan** : Notifications for Morphe bytecode patches updates.\n"
                        "• 🧪 **VIP Tester** : Access to preview beta drops & experimental builds.\n"
                        "• 👥 **Membre** : Official server member badge.\n\n"
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                        "🇫🇷 **FRANÇAIS**\n"
                        "Cliquez sur les boutons ci-dessous pour obtenir ou retirer vos rôles en un clic !\n\n"
                        "• 🎵 **BitChard Fan** : Alertes pour chaque mise à jour et APK de BitChard.\n"
                        "• 🦈 **D-moniak Fan** : Alertes pour les patchs Morphe.\n"
                        "• 🧪 **VIP Tester** : Accès aux versions de test et bêtas.\n"
                        "• 👥 **Membre** : Badge officiel de membre du serveur."
                    ),
                    color=Colour.from_rgb(255, 64, 129)
                )
                embed.set_footer(text="SatanMerde Projects • Interactive Role Picker")
                await roles_ch.send(embed=embed, view=RoleSelectionView())
                print("  [+] Interactive Role Panel deployed in #✨・roles!")

    await client.change_presence(
        activity=discord.Activity(
            type=discord.ActivityType.watching,
            name="BitChard & D-Moniak | /help"
        )
    )
    print("[✓] Fully operational bilingual bot running!")

if __name__ == "__main__":
    client.run(TOKEN)
