# Integrating your modpack with LAN+

For modpack authors: make your modpack show up on players LAN+ profiles.

## What players see

When someone plays your modpack with LAN+ installed, their profile can show it as:

- **Playing now:** live, while they're in a world playing your modpack.
- **Recently played:** automatic, based on real playtime.
- **Favorite:** if they pin it.

Each shows your modpack **name** and **download link** (optional).

> A later release adds modpack cosmetics (frames, backgrounds, models) that players unlock by playing your modpack. It keys off the same id below, so setting this up now covers that too.

## Setup

Two steps: write a JSON file inside your modpack, then register the id with LAN+ once.

### 1. Ship the file

Put one file in your modpack's config folder:

```
<instance>/config/lanplus-modpack.json
```

```json
{
  "modpackId": "your-pack-id",
  "version": "1.2.0"
}
```

In a CurseForge / Modrinth export, `config/` ships under `overrides/config/`, so the file goes with your modpack automatically.

- **`modpackId`** (required): a stable, unique id. Lowercase letters, digits, `-`, `_` (e.g. `astro-greg`, `start-beyond`). Treat it as permanent, everything keys off it.
- **`version`** (optional): LAN+ ignores it. Include it for your own bookkeeping if you want.

LAN+ trusts the id the file declares, it does not scan your mod list. The file is per-instance, so it applies to every world in that install (correct, since an instance is one modpack). LAN+ reports your modpack only while a player is in their own world (singleplayer or hosting). A guest on someone else's server reports the host's modpack, not their own.

### 2. Register the id

LAN+ turns your `modpackId` into a name and link through a curated registry. There's no public write endpoint, the maintainer adds entries so names and links stay trustworthy. Send the maintainer the following (links and text only, never files):

| Field         | Required    | What it is                                              |
|---------------|-------------|---------------------------------------------------------|
| `modpackId`   | yes         | the exact id from your `lanplus-modpack.json`           |
| `name`        | yes         | display name shown on profiles                          |
| `downloadUrl` | recommended | link to your modpack (CurseForge / Modrinth / your site)|
| `author`      | optional    | your name or handle                                     |
| `team`        | optional    | your team or organization                               |
| `iconUrl`     | optional    | link to an icon (reserved for an upcoming icon display) |
| `description` | optional    | a short blurb                                           |

LAN+ stores only these references. It never hosts your modpack, and never follows the links itself. A player's client opens the link in a browser only on click.

## Checklist

1. Pick a stable `modpackId` (lowercase, `a-z0-9-_`).
2. Ship `config/lanplus-modpack.json` (in `overrides/config/` for a CF/Modrinth export).
3. Send the maintainer at least `modpackId` + `name` + a download link.
4. Load a world with your modpack + LAN+. Your profile shows **Playing -> your name**, and **Recently played** fills in over a few sessions.

## Good to know

- Until your id is registered, LAN+ shows nothing for it.
- Players control visibility: *playing now*, *favorite*, and *recently played* each have their own toggle, plus an invisible mode.