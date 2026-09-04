# Contributing to LAN+

Thanks for taking the time to help out. LAN+ is a Minecraft multiplayer
quality-of-life mod, and contributions of all sizes are welcome: bug reports,
fixes, features, and docs.

## Ways to contribute

- **Report a bug or suggest an idea:** open an issue on
  [GitHub](https://github.com/BgameXL/LanPlus/issues) using one of the issue
  templates, or come say hi on [Discord](https://discord.gg/kAKk3wRAVh).
- **Send a change:** open a pull request (see below).

If you are planning a larger change, it is a good idea to open an issue (or talk this in discord) first so we can agree on the approach before you spend time.

## Project

LAN+ is a multi-loader [Architectury](https://docs.architectury.dev/) project targeting **Fabric** and **NeoForge** on **Minecraft 1.21.1**.

- `common/` holds all shared logic and every mixin (`dev.bgame.lanplus`).
- `fabric/` holds the Fabric entry points.
- `neoforge/` holds the NeoForge entry points.

Features are organized into domains inside `common/`: `presence`, `friends`,
`invites`, `skins`, `network`, `discord`, `profiles`, and `cosmetics`. `core`,
`client`, and `api` are support packages. When adding something, fit it into an existing domain rather than inventing a new one.

The online backend and relay do not live on `main`. They are maintained on the
`backend-relay` branch.

`common/src/main/java/dev/bgame/lanplus/cosmetics/geckolib` is vendored
third-party code (GeckoLib, MIT). Please keep it as close to upstream as
possible. See [THIRD_PARTY.md](THIRD_PARTY.md).

## Development setup

Requirements:

- JDK 21
- Git
- IntelliJ IDEA is recommended but others IDEs are fine.
- Knowledge of Java of course.

```bash
./gradlew build
```

Run a dev client

```bash
./gradlew :fabric:runClient
./gradlew :neoforge:runClient
```

For multiplayer testing, launch a second `runClient` configuration from the IDE
after importing the Gradle project.

Changes are verified in-game, so please test it and confirm your change works before opening a PR.

## Code style

- **No comments in LAN+ code.** If a decision needs explaining, do it in the PR description.
- Follow the existing structure and naming and please keep changes small and focused,
  reuse and extend before creating new classes or helpers, and do not refactor
  unrelated code.
- For UI, use the existing `LanPlusUI` helpers and theme tokens rather than
  introducing a separate visual language.

## Pull requests

1. Fork the repository and create a branch off `main`.
2. Make your change following the code style above.
3. Make sure `./gradlew build` passes.
4. Test the change in-game with `runClient`.
5. Open a pull request against `main`. Describe what it does and why, and reference any related issue.

Using an AI coding tool is fine, But you are still responsible for understanding, reviewing, and testing everything you submit.
If you can't explain your changes in detail, please don't submit.

## License

By contributing, you agree that your contributions are licensed under the
project's [GNU LGPL v3.0](LICENSE).
