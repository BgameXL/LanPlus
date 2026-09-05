<h1 align="center">
  <img src="https://raw.githubusercontent.com/BgameXL/LanPlus/main/common/src/main/resources/assets/lanplus/textures/lan_logo.png" alt="LAN+" width="280">
</h1>

<p align="center">
  Play Minecraft with friends without the usual hassle. Add friends, invite them into your world, and let them join over the internet with no port forwarding.
</p>

<p align="left">
  <a href="https://www.curseforge.com/minecraft/mc-mods/lan">
    <img alt="CurseForge" height="50" src="https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/available/curseforge_vector.svg"></a>
  <a href="https://github.com/BgameXL/LanPlus">
    <img alt="GitHub" height="50" src="https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/available/github_vector.svg"></a>
  <a href="https://discord.gg/kAKk3wRAVh">
    <img alt="Discord" height="50" src="https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/social/discord-singular_vector.svg"></a>
  <a href="https://ko-fi.com/bgame">
    <img alt="Ko-fi" height="50" src="https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/donate/kofi-singular_vector.svg"></a>
</p>

<p align="center"> <img src="https://raw.githubusercontent.com/BgameXL/LanPlus/main/assets/divider.png" alt="" width="1000"> </p>

## What it does

- **Friends list:** add friends and see who's online and what they're playing, right from the main menu.
- **Invites:** share a short join code or send an invite, and your friend drops into your world.
- **Play over the internet:** a built-in relay lets friends join your singleplayer world from anywhere. No port
  forwarding, no static IP.
- **Host access control:** pick who can join (everyone, friends only, or invite-only).
- **Profiles:** bio, pronouns, links, favorite modpack, playtime, and a background you can customize (Still working on this).
- **Skins:** set a custom skin by URL or upload. It shows up in the UI and in the world.
- **Discord Rich Presence:** show what you're playing and let friends join straight from Discord.

## Good to know

- The online stuff (friends, invites, profiles, relay) runs through the LAN+ backend at `backend.lanplus.dev`.
- If the backend is down or unreachable, the mod just falls back to local-only. Your game keeps working either way.
- Everything's configurable in `config/lanplus-client.toml`, including the backend URL. You can turn off the online
  features or Discord entirely.
- People without LAN+ can still join your world while you're hosting, they just won't have access to profiles.

### Free

> Lan+ is hosted on a VPS that i pay for, and Lan+ will remain free because of my love for this project. If you'd like,
> you can donate to this mod it's your decision after all, but i appreciate it.

<p align="center"> <img src="https://raw.githubusercontent.com/BgameXL/LanPlus/main/assets/divider.png" alt="" width="800"> </p>

## FAQ

**Do my friends need LAN+ to join?**
Nope. Anyone can connect with the address, like a normal server.

**How does XP work?**
You earn it three ways: unlocking advancements, time spent playing modpacks, and time spent playing with friends. It
fills up profile tiers and doesn't touch gameplay at all. There's nothing to spend it on yet, but accessories are on the
way (see below).

**Do I need a premium (paid) Minecraft account?**
The social features work with offline accounts too. Joining someone's Hosted world usually needs a premium
account, unless the host turns on offline support.

**Do I have to port-forward to host over the internet?**
No. The relay gives you a public address without touching your router (Your IP gets masked).

**Is it free? Any cash shop?**
Free, and no monetization. Cosmetics are unlockable, there's no store or currency.

**Is my profile safe?**
Your profile don't hold private stuff. LAN+ signs in through your existing Minecraft session, so there's no
extra account or password to worry about.

## Planned

Still actively working on it. On the list:

- **Unlockable cosmetics and emotes:** spend your XP on in-world cosmetics (accessories and emotes).
- **More minecraft versions**
- **Public world directory:** browse open ("everyone") worlds and hop in without an invite (This means like a Global search).
- **Website:** a place to share your profile, join worlds, and see what cosmetics are available.

<p align="center"> <img src="https://raw.githubusercontent.com/BgameXL/LanPlus/main/assets/divider.png" alt="" width="600"> </p>

## For modpack authors

Want your modpack to show up on players' LAN+ profiles?
See [Integrating your modpack with LAN+](MODPACK_INTEGRATION.md).

## Issues & feedback

Found a bug or have an idea? Open an issue on [GitHub](https://github.com/BgameXL/LanPlus/issues) or come say hi on
our [Discord](https://discord.gg/kAKk3wRAVh).

## A note from Dev

LAN+ is built with help from an AI coding tool (Claude). I use it, but every feature is designed, reviewed, and tested
in-game by me before it ships. Over 70% is made by a human, 30% was made with AI.

## License

[GNU LGPL v3.0](https://github.com/BgameXL/LanPlus/blob/main/LICENSE)