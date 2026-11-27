<h1><img src="src/main/resources/four-winds.png" width="64" height="64" align="absmiddle" alt=""> Grodion's Four Winds Roadworks</h1>

[Installation](#playing) · [Gallery](docs/gallery/README.md) · [Report an issue](https://github.com/grodionn/four-winds-roadworks/issues/new/choose) · [Contributing](CONTRIBUTING.md) · [Russian edition](README.ru.md)

Four roads meet near the centre of the Overworld. Pick a direction and the paving gives way to gravel, a turning for a village, or a flight of steps cut into the hillside. Where a road reaches a sheltered coast, you might find a working pier and a lighthouse with stairs all the way up to the beacon.

[![A jungle road, a stone tunnel and a lighthouse at sunset](art/road-to-coast.jpg)](docs/gallery/README.md)

The main roads run five blocks wide. A four-block gravel lane has room for two wheel tracks. The last few blocks into a village are often just a three-block dirt track. Wooden signs at crossroads carry the compass directions. At a village turning, the sign gives the destination and the distance.

Short river crossings can have a timber deck or a stone arch. In the mountains, the road can pass under a rock gallery or through a supported tunnel. Along quieter stretches there are occasional roofed benches and mounting stones.

### Playing

Forge and Fabric builds cover Minecraft **1.21.1, 1.20.1, 1.19.2, 1.18.2 and 1.16.5**. For **1.12.2**, choose Forge or Legacy Fabric. **1.21.1** also has a NeoForge build. Fabric on 1.16.5 and later needs the matching Fabric API. The [build table](ports/README.md) lists the tested versions.

Published JARs go on the [Releases page](https://github.com/grodionn/four-winds-roadworks/releases). To try a development build, sign in to GitHub, open a [successful build on `main`](https://github.com/grodionn/four-winds-roadworks/actions/workflows/build.yml?query=branch%3Amain+is%3Asuccess) and unzip the artifact for your Minecraft version and loader. Put the same JAR in `mods/` on the client and on the server. In an existing save, explore new Overworld chunks to find the roads.

TerraFirmaCraft is optional. In its Overworld preset, the roads pick up the local soil and stone, the quays use TFC timber and the beacon burns fuel in a TFC firepit. TFC Classic on 1.12.2 uses andesite for the masonry. Village turnings depend on which settlements your world can generate.

### Building

For the NeoForge build, install JDK 21 and run `./gradlew build` on Linux or macOS, or `.\gradlew.bat build` on Windows. The finished JAR is in `build/libs/`. [Build history](https://github.com/grodionn/four-winds-roadworks/actions/workflows/build.yml)

For a development client, editable models and reproducible worldgen checks, see [Contributing](CONTRIBUTING.md). If you find a broken stretch, a seed, coordinates and a screenshot make a good [bug report](https://github.com/grodionn/four-winds-roadworks/issues/new/choose).

The code and original artwork are available under the [MIT licence](LICENSE).
