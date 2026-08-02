# LAN+

LAN+ is a Minecraft mod for playing with friends without the usual hassle. Add friends, invite them into your world, and let them join over the internet with no port forwarding.

## What it does

- **Friends list:** add friends and see who's online and what they're playing, right from the main menu.
- **Invites:** share a short join code or send an invite, and your friend drops into your world.
- **Play over the internet:** a built-in relay lets friends join your singleplayer world from anywhere. No port forwarding, no static IP.
- **Host access control:** pick who can join (everyone, friends only, or invite-only).
- **Profiles:** bio, pronouns, links, favorite modpack, playtime, and a background you can customize.
- **Skins:** set a custom skin by URL or upload. It shows up in the UI and in the world.
- **Discord Rich Presence:** show what you're playing and let friends join straight from Discord.

## Good to know

- The online stuff (friends, invites, profiles, relay) runs through the LAN+ backend at `backend.lanplus.dev`.
- If the backend is down or unreachable, the mod just falls back to local-only. Your game keeps working either way.
- Everything's configurable in `config/lanplus-client.toml`, including the backend URL. You can turn off the online features or Discord entirely.
- People without LAN+ can still join your world while you're hosting, they just won't have profiles.

## FAQ

**Does it work on loaders other than Forge?**
Not yet. Right now it's Forge 1.20.1. Other loaders are planned.

**Do my friends need LAN+ to join?**
Nope. Anyone can connect with the address, like a normal server.

**How does XP work?**
You earn it three ways: unlocking advancements, time spent playing modpacks, and time spent playing with friends. It fills up profile tiers and doesn't touch gameplay at all. There's nothing to spend it on yet, but cosmetics are on the way (see below).

**Do I need a premium (paid) Minecraft account?**
The social features work with offline accounts too. Joining someone's "Open to LAN" world usually needs a premium account, unless the host turns on offline support.

**Do I have to port-forward to host over the internet?**
No. The relay gives you a public address without touching your router.

**Is it free? Any cash shop?**
Free, and no monetization. Cosmetics are unlockable, there's no store or currency.

**Is my profile safe?**
Your profile only holds what you choose to share. LAN+ signs in through your existing Minecraft session, so there's no extra account or password to worry about.

## Planned

Still actively working on it. On the list:

- **Unlockable cosmetics and emotes:** spend your XP on in-world cosmetics (head, hand, and back slots) and emotes.
- **More mod loaders:** beyond Forge 1.20.1.
- **Public world directory:** browse open ("everyone") worlds and hop in without an invite.
- **Discord badges:** show your linked Discord on your profile (intended for the LAN+ server).
- **Website:** a place to share your profile, join worlds, and see what cosmetics are available.

## For modpack authors

Want your modpack to show up on players' LAN+ profiles? See [Integrating your modpack with LAN+](MODPACK_INTEGRATION.md).

## Issues & feedback

Found a bug or have an idea? Open an issue on [GitHub](https://github.com/BgameXL/LanPlus/issues) or come say hi on our [Discord](https://discord.gg/kAKk3wRAVh).

## A note from Dev

LAN+ is built with help from an AI coding tool (Claude). I use it, but every feature is designed, reviewed, and tested in-game by me before it ships.
Over 70% is made by a human, 30% was made with AI

## License

[GNU LGPL v3.0](https://github.com/BgameXL/LanPlus/blob/main/LICENSE)