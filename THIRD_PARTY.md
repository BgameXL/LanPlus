# Third-party code

LAN+ vendors third-party source code. Each entry lists the project, its
license, what is taken and where it lives in this repository.

## GeckoLib

- Project: https://github.com/bernie-g/geckolib
- Upstream basis: GeckoLib 4.9.2 (branch `1.21.1`), adapted for Minecraft
  1.20.1
- License: MIT (see [geckolib-MIT.txt](licenses/geckolib-MIT.txt))

LAN+ vendors portions of GeckoLib's animation engine to render animated 3D
cosmetics from Bedrock assets downloaded at runtime, without a live dependency
on GeckoLib. The vendored source lives under
`common/src/main/java/dev/bgame/lanplus/cosmetics/geckolib`; the custom static
renderer in `client/CosmeticGeoRender.java` is derived from the same data model.
