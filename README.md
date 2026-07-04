# LAN+

LAN+ is a Minecraft Forge mod that makes playing with friends effortless.

## Features

- **Friends list** — add friends, see who's online and what they're playing, right from the main menu.
- **One-click invites** — share a short join code (or send an invite directly) and friends hop into your world.
- **Play over the internet** — a built-in relay tunnel lets friends join your singleplayer world from anywhere, no port forwarding required.
- **Host access control** — decide who can join: everyone, friends only, or invited players only.
- **Profiles** — bio, pronouns, links, favorite modpack, playtime progression, and customizable profile backgrounds.
- **Skins** — custom skins via URL or upload, visible in the UI and in-world.
- **Discord Rich Presence** — show what you're playing, party size, and let friends join straight from Discord.

## Good to know

- Online features (friends, invites, profiles, relay) are provided by the LAN+ backend at `backend.lanplus.dev`. The mod authenticates using your Minecraft session — no separate account or password.
- If the backend is unreachable, the mod quietly degrades to local-only mode; your game is never affected.
- Everything is configurable in `config/lanplus-client.toml`, including the backend URL and disabling the mod's online features or Discord integration entirely.
- People without Lan+ can also access the user's host as long as they are found hosting. Profiles are not available for this.

## Issues & feedback

Found a bug or have an idea? Open an issue at [github](https://github.com/BgameXL/LanPlus/issues) or join our [Discord](https://discord.gg/r7PeUvMytZ).

## License

[GNU LGPL v3.0](LICENSE)
