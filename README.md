# LAN+

LAN+ is a Minecraft mod that makes playing with friends effortless.

## Features

- **Friends list** — add friends, see who's online and what they're playing, right from the main menu.
- **One-click invites** — share a short join code (or send an invite directly) and friends hop into your world.
- **Play over the internet** — a built-in relay tunnel lets friends join your singleplayer world from anywhere, no port forwarding required.
- **Host access control** — decide who can join: everyone, friends only, or invited players only.
- **Profiles** — bio, pronouns, links, favorite modpack, playtime progression, and customizable profile backgrounds.
- **Skins** — custom skins via URL or upload, visible in the UI and in-world.
- **Discord Rich Presence** — show what you're playing, party size, and let friends join straight from Discord.

## Good to know

- Online features (friends, invites, profiles, relay) are provided by the LAN+ backend at `backend.lanplus.dev`.
- If the backend is unreachable, the mod quietly degrades to local-only mode; your game is never affected.
- Everything is configurable in `config/lanplus-client.toml`, including the backend URL and disabling the mod's online features or Discord integration entirely.
- People without Lan+ can also access the user's host as long as they are found hosting. Profiles are not available for this.

## FAQ

**Does LAN+ support loaders other than Forge?**
Not yet — LAN+ targets Forge 1.20.1 first. Support for other loaders is planned.

**Do my friends need LAN+ installed to join?**
No. Anyone can connect to your world with its address, like a normal server.

**How does XP work?**
You earn XP three ways: unlocking in-game advancements, time spent playing modpacks, and social time (playing alongside friends). XP fills profile tiers and never affects gameplay. For now it's just progression — there's nothing to spend it on yet. Unlockable cosmetics are planned (see below).

**Do I need a premium (paid) Minecraft account?**
The social features work with offline accounts too. Joining a host's "Open to LAN" world normally requires a premium account, unless the host enables offline support.

**Do I have to port-forward to host over the internet?**
No. The built-in relay tunnel gives you a public address without touching your router.

**Is it free? Is there a cash shop?**
Free, with no monetization. Cosmetics are unlockable — there's no store or currency.

**Is my profile safe?**
Yes. Your profile only holds what you choose to share. LAN+ signs in through your existing Minecraft session, so there's no separate account or password to leak.

## Planned

LAN+ is actively developed. On the roadmap:

- **Unlockable cosmetics & emotes** — spend the XP you earn on in-world cosmetics (head, hand, and back slots) and emotes.
- **More mod loaders** — support beyond Forge 1.20.1.
- **Public world directory** – browse open ("everyone") worlds and hop in, no invite needed.
- **Discord badges** — show your linked Discord on your profile (This one is only intended for Lan+ Server).
- **Website** — A place to share your profile, join worlds and current cosmetics available in Lan+.

## For modpack authors

Want your modpack to show up on players' LAN+ profiles? See [Integrating your modpack with LAN+](MODPACK_INTEGRATION.md).

## Issues & feedback

Found a bug or have an idea? Open an issue at [github](https://github.com/BgameXL/LanPlus/issues) or join our [Discord](https://discord.gg/r7PeUvMytZ).

## License

[GNU LGPL v3.0](https://github.com/BgameXL/LanPlus/blob/main/LICENSE)
