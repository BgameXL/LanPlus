# Integrating your modpack with LAN+

This guide is for **modpack authors**. It explains how to make your pack show up on players'
LAN+ profiles, and exactly what you need to send to get listed.
## What players get

When someone plays your modpack with LAN+ installed, their LAN+ profile can show your pack as:

- **Playing now** — live, while they are in a world running your pack.
- **Recently played** — automatic; the pack they played most recently.
- **Favorite** — if they pin it as their favorite (a badge they choose to show off).

In every case players see your pack's **name** and an optional **download link**. That link is a doorway back to your pack — free discovery
every time one of your players opens their profile in front of friends.

> A later LAN+ release adds modpack-specific profile cosmetics (frames, backgrounds, models, etc.) that players
> unlock by playing your pack. The same id described below is the hook for that, so setting this up now
> also future-proofs you for it.

## Two steps

1. **Ship a tiny identity file** with your pack so it declares an id.
2. **Register that id with LAN+** (one-time) so the id resolves to your name + link.

---

### Step 1 — ship the identity file

Drop **one flat file** into your pack's config folder:

```
<instance>/config/lanplus-modpack.json
```

```json
{
  "modpackId": "your-pack-id",
  "version": "1.2.0"
}
```

That's it, in a CurseForge / Modrinth export the `config/` folder ships under `overrides/config/`, so the file travels with your pack automatically.

- **`modpackId`** (required): a stable, unique id for your pack. Use lowercase letters, digits, `-` and
  `_` (for example `astro-greg`, `start-beyond`). Treat it as permanent — everything keys off it, so don't
  change it between versions.
- **`version`** (optional): informational, for your own bookkeeping. LAN+ currently ignores it; include
  it if you like.

This is **self-declaration, not fingerprinting**: LAN+ trusts the id the file states and does not scan
your mod list.

> The file lives in `config/`, which is **per-instance** — it applies to every world in that install. That
> is the right behavior: an instance *is* one modpack. (Don't put it in a world's `saves/.../datapacks/`.)
>
> LAN+ reports your pack only while the player is in **their own world** (singleplayer, or when they host).
> A guest who joins someone else's remote server reports the host's pack (via presence), not their own.

---

### Step 2 — register your id with LAN+

LAN+ turns your `modpackId` into a display name + link using a **curated registry**. There is **no
public write endpoint** — registration is done by the maintainer on purpose, so names and links stay
trustworthy (nobody can spoof another pack or point a link somewhere malicious).

To get listed, send the LAN+ maintainer the following. **You send links and text only — never files.**

| Field         | Required    | What it is                                              |
|---------------|-------------|---------------------------------------------------------|
| `modpackId`   | yes         | the exact id from your `modpack.json`                   |
| `name`        | yes         | display name shown on profiles.                         |
| `downloadUrl` | recommended | link to your pack (CurseForge / Modrinth / your site)   |
| `author`      | optional    | your name or handle                                     |
| `team`        | optional    | your team or organization                               |
| `iconUrl`     | optional    | link to an icon (reserved for an upcoming icon display) |
| `description` | optional    | a short blurb                                           |

LAN+ stores only these references. It never uploads, hosts, or proxies your modpack, and it never
follows the links itself — a player's client opens the download link in a browser, only on an explicit
click.

---

## Quick checklist

1. Pick a stable `modpackId` (lowercase, `a-z0-9-_`).
2. Ship `config/lanplus-modpack.json` with your pack (in `overrides/config/` in a CF/Modrinth export).
3. Send the maintainer your row: `modpackId` + `name` + a download link, at minimum.
4. Load a world with your pack + LAN+ installed. Your profile shows **Playing -> _your name_**; over a
   few sessions **Recently played** fills in too.

## Good to know

- **Before** your id is registered, LAN+ treats it as unknown and simply shows nothing for it.
- **Recently played** comes from real play time, measured server-side from presence heartbeats. Disconnect
  gaps are not counted, and only **registered** ids accrue time — so an unregistered or made-up id never
  pollutes anyone's stats.
- Players control visibility: each of *playing now*, *favorite*, and *recently played* has its own on/off
  toggle on their profile, and an invisible mode hides live presence entirely. Your pack only ever shows
  up because a player chose to let it.
