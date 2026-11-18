# Contributing to LAN+

Thanks for taking the time to help out. LAN+ is a Minecraft multiplayer
quality-of-life mod, and contributions are welcome: bug reports,
fixes, features, and docs.

## Ways to contribute

- **Report a bug or suggest an idea:** open an issue on
  [GitHub](https://github.com/BgameXL/LanPlus/issues) using one of the issue
  templates, or come say hi on [Discord](https://discord.gg/kAKk3wRAVh).
- **Send a change:** open a pull request (see below).

If you are planning a large change, open an issue or discuss it on Discord first
so its direction can be agreed on before implementation.

## Project

LAN+ is a multi-loader [Architectury](https://docs.architectury.dev/) project targeting **Fabric** and **Forge** on **Minecraft 1.20.1**.

- `common/` holds all shared logic, including mixins (`dev.bgame.lanplus`).
- `fabric/` holds the Fabric entry points.
- `forge/` holds the Forge entry points.

Features are organized into domains inside `common/`: `presence`, `friends`,
`invites`, `skins`, `network`, `discord`, `profiles`, and `cosmetics`. `core`,
`client`, and `api` are support packages. When adding something, fit it into an existing domain rather than inventing a new one.

The online backend and relay do not live on this repo. Check in [here](https://github.com/BgameXL/lanplus-backend-relay) instead.

`common/src/main/java/dev/bgame/lanplus/cosmetics/geckolib` is vendored
third-party code (GeckoLib, MIT). Keep it as close to upstream as
possible. See [THIRD_PARTY.md](THIRD_PARTY.md).

## Development setup

Requirements:

- JDK 17
- Git
- IntelliJ IDEA is recommended, but other IDEs are fine.
- Knowledge in Java of course.

```bash
./gradlew build
```

Run a dev client

```bash
./gradlew :fabric:runClient
./gradlew :forge:runClient
```

For multiplayer testing, launch a second `runClient` configuration from the IDE
after importing the Gradle project.

Changes are verified in-game, so please test it and confirm your change works before opening a PR.

## Code style

- Keep comments limited to required license and attribution headers,
  compatibility constraints, and decisions the code cannot explain clearly.
- Follow the existing structure and naming and please keep changes small and focused,
  reuse and extend before creating new classes or helpers, and do not refactor
  unrelated code.
- For UI, use the existing `LanPlusUI` helpers and theme tokens rather than introducing a separate visual.

## Pull requests

1. Fork the repository and create a branch off `1.20-forge-fabric`.
2. Make your change following the code style above.
3. Make sure `./gradlew build` passes.
4. Test the change in-game with `runClient`.
5. Open a pull request against `1.20-forge-fabric`. Describe what it does and why, and reference any related issue.

Using an AI coding tool is fine, but you are still responsible for understanding, reviewing, and testing everything you submit.
If you can't explain your changes in detail, please don't submit.

## License

By contributing, you agree that your contributions are licensed under the
project's [GNU LGPL v3.0](LICENSE).
