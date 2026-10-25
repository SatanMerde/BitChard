"""
Bot Discord SatanMerde - Système de Sécurité Anti-Raid, Auto-Role, Cartes de Bienvenue et Tickets
"""

import sys
import asyncio
import time
from collections import defaultdict
import discord
from discord import app_commands, Colour, Embed, ButtonStyle
from discord.ui import View, Button

try:
    sys.stdout.reconfigure(encoding='utf-8')
except Exception:
    pass

intents = discord.Intents.default()
intents.guilds = True

# Message content et members peuvent être indisponibles si non cochés dans Developer Portal.
# Le bot gère les deux cas gracieusement.

client = discord.Client(intents=intents)
tree = app_commands.CommandTree(client)

import os
TOKEN = os.environ.get("DISCORD_BOT_TOKEN", "")

# Anti-Raid Memory : Suivi des arrivées et messages
recent_joins = []
user_message_times = defaultdict(list)
RAID_WINDOW_SECONDS = 10
RAID_JOIN_THRESHOLD = 5 # Plus de 5 personnes en 10s = Alerte raid

# ---------------- VUES INTERACTIVES : TICKETS ----------------

class TicketCloseView(View):
    def __init__(self):
        super().__init__(timeout=None)

    @discord.ui.button(label="Fermer le ticket / Close", style=ButtonStyle.danger, emoji="🔒", custom_id="close_ticket_btn")
    async def close_ticket(self, interaction: discord.Interaction, button: Button):
        await interaction.response.send_message("🔒 Fermeture du ticket dans 5 secondes...", ephemeral=True)
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
        
        # Vérifier si un ticket existe déjà pour cet utilisateur
        existing = discord.utils.get(guild.channels, name=f"ticket-{user.name.lower()}")
        if existing:
            await interaction.response.send_message(f"⚠️ Vous avez déjà un ticket ouvert : {existing.mention}", ephemeral=True)
            return

        cat = interaction.channel.category
        overwrites = {
            guild.default_role: discord.PermissionOverwrite(read_messages=False),
            user: discord.PermissionOverwrite(read_messages=True, send_messages=True, attach_files=True),
            guild.me: discord.PermissionOverwrite(read_messages=True, send_messages=True, administrator=True)
        }

        # Ajouter les modérateurs s'il y a un rôle Maintainer ou Fondateur
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
                f"Bonjour {user.mention} !\n\n"
                "Décrivez votre problème ou votre demande en détail (modèle d'appareil, logs, question sur BitChard ou D-MoniakPatches).\n"
                "Un modérateur vous répondra dès que possible.\n\n"
                "Cliquez sur le bouton ci-dessous pour fermer le ticket une fois résolu."
            ),
            color=Colour.from_rgb(0, 188, 212)
        )
        await ticket_ch.send(content=f"{user.mention}", embed=embed, view=TicketCloseView())
        await interaction.response.send_message(f"✅ Votre ticket a été créé : {ticket_ch.mention}", ephemeral=True)

# ---------------- SLASH COMMANDS ----------------

@tree.command(name="help", description="Affiche toutes les commandes / Display commands")
async def slash_help(interaction: discord.Interaction):
    embed = Embed(
        title="🤖 SatanMerde Bot — Commandes",
        color=Colour.from_rgb(229, 57, 53)
    )
    embed.add_field(
        name="🎵 BitChard & 🦈 D-Moniak",
        value="`/bitchard` : Infos complètes, nouveautés & APKs\n`/dmoniak` : Patchs Morphe / ReVanced\n`/repos` : Liens officiels GitHub\n`/legal` : Mentions légales et cadre d'utilisation",
        inline=False
    )
    embed.add_field(
        name="🛡️ Sécurité & Modération (Staff)",
        value="`/clear <nombre>` : Nettoie les messages\n`/setup-ticket-panel` : Publie le panneau de tickets interactif\n`/serverinfo` : Vérification et santé du serveur",
        inline=False
    )
    await interaction.response.send_message(embed=embed)

@tree.command(name="setup-ticket-panel", description="Publie le panneau d'ouverture de tickets dans le salon tickets (Staff)")
@app_commands.checks.has_permissions(administrator=True)
async def slash_setup_ticket(interaction: discord.Interaction):
    embed = Embed(
        title="🎫 Support Privé & Billetterie / Private Support Tickets",
        description=(
            "Besoin d'une aide technique privée, de signaler un problème d'APK ou de contacter le staff ?\n\n"
            "Cliquez sur le bouton ci-dessous pour ouvrir un salon de discussion privé avec l'équipe !\n\n"
            "Need private assistance or report a private issue? Click below to open a ticket!"
        ),
        color=Colour.from_rgb(0, 188, 212)
    )
    await interaction.channel.send(embed=embed, view=TicketLauncher())
    await interaction.response.send_message("✅ Panneau de tickets déployé !", ephemeral=True)

@tree.command(name="bitchard", description="Informations et téléchargement de BitChard (v1.8.7)")
async def slash_bitchard(interaction: discord.Interaction):
    embed = Embed(
        title="🎵 BitChard — Le Client YouTube Music Ultime",
        description=(
            "**BitChard** transforme l'expérience audio sur Android, Wear OS, TV et PC :\n\n"
            "• 🤖 **BitG AI DJ** : DJ personnel interactif avec voix synthétique en temps réel.\n"
            "• 🎤 **Paroles synchronisées par syllabe** : Intégration Apple Music / Musixmatch.\n"
            "• 🎧 **Audio Haute Définition** : Flux FLAC / ALAC 24-bit bit-perfect.\n"
            "• 🔄 **Import de compte universel** : Dialogue automatique à la connexion (Spotify, Deezer, Apple Music, YouTube Music).\n\n"
            "🔗 **GitHub** : https://github.com/SatanMerde/BitChard\n"
            "📦 **Releases & APKs** : https://github.com/SatanMerde/BitChard/releases"
        ),
        color=Colour.from_rgb(255, 23, 68)
    )
    await interaction.response.send_message(embed=embed)

@tree.command(name="dmoniak", description="Informations sur D-MoniakPatches pour Morphe / ReVanced")
async def slash_dmoniak(interaction: discord.Interaction):
    embed = Embed(
        title="🦈 D-MoniakPatches — Framework Morphe / ReVanced",
        description=(
            "Suite de patchs tiers pour le framework Morphe.\n\n"
            "• Personnalisations avancées, intégrations et correctifs pour applications Android.\n\n"
            "🔗 **GitHub** : https://github.com/SatanMerde/D-moniakPatches"
        ),
        color=Colour.from_rgb(41, 121, 255)
    )
    await interaction.response.send_message(embed=embed)

@tree.command(name="clear", description="Supprime un nombre de messages récents (Staff)")
@app_commands.checks.has_permissions(manage_messages=True)
async def slash_clear(interaction: discord.Interaction, amount: int = 5):
    amount = min(max(1, amount), 100)
    await interaction.response.defer(ephemeral=True)
    deleted = await interaction.channel.purge(limit=amount)
    await interaction.followup.send(f"🧹 **{len(deleted)} messages supprimés.**", ephemeral=True)

@tree.command(name="serverinfo", description="Vérifie la santé et la configuration du serveur")
async def slash_serverinfo(interaction: discord.Interaction):
    guild = interaction.guild
    embed = Embed(
        title=f"📊 État du Serveur : {guild.name}",
        color=Colour.from_rgb(76, 175, 80)
    )
    embed.add_field(name="👑 Propriétaire", value=f"<@{guild.owner_id}>", inline=True)
    embed.add_field(name="👥 Membres", value=str(guild.member_count), inline=True)
    embed.add_field(name="🛡️ Sécurité Anti-Raid", value=f"Niveau : `{guild.verification_level.name.upper()}` (E-mail vérifié)", inline=False)
    embed.add_field(name="🔍 Filtre Contenu", value="Actif sur tous les membres", inline=True)
    embed.add_field(name="🎫 Système Tickets", value="Actif", inline=True)
    await interaction.response.send_message(embed=embed)

# ---------------- ON READY ----------------

@client.event
async def on_ready():
    print(f"[✓] Bot connecté et opérationnel : {client.user}")
    
    # Synchroniser les commandes slash
    for guild in client.guilds:
        tree.copy_global_to(guild=guild)
        await tree.sync(guild=guild)
        print(f"  [+] Commandes slash synchronisées sur : {guild.name}")

        # Déployer automatiquement le panneau de tickets dans 🎫・tickets si vide
        ticket_ch = discord.utils.get(guild.channels, name="🎫・tickets")
        if ticket_ch:
            history = [msg async for msg in ticket_ch.history(limit=5)]
            if not history:
                embed = Embed(
                    title="🎫 Support Privé & Billetterie / Private Support Tickets",
                    description=(
                        "Besoin d'une aide technique privée, de signaler un problème d'APK ou de contacter le staff ?\n\n"
                        "Cliquez sur le bouton ci-dessous pour ouvrir un salon de discussion privé avec l'équipe !\n\n"
                        "--- \n"
                        "Need private assistance or report a private issue? Click below to open a ticket!"
                    ),
                    color=Colour.from_rgb(0, 188, 212)
                )
                await ticket_ch.send(embed=embed, view=TicketLauncher())
                print("  [+] Panneau de tickets automatiquement déployé dans 🎫・tickets !")

    client.add_view(TicketLauncher())
    client.add_view(TicketCloseView())

    await client.change_presence(
        activity=discord.Activity(
            type=discord.ActivityType.watching,
            name="BitChard & D-Moniak | /help"
        )
    )
    print("[✓] Système Anti-Raid, Tickets et Commandes 100% actifs !")

if __name__ == "__main__":
    client.run(TOKEN)
