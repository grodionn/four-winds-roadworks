# Choose a build

Install the JAR for your Minecraft version and loader on both sides of the game. Roads appear in newly generated Overworld chunks. The Forge and NeoForge builds have separate filenames. On Fabric, install the matching Fabric API from the table. The 1.12.2 Legacy Fabric build includes its own model and language registration.

| Minecraft | Forge | Fabric Loader 0.17.3 | TerraFirmaCraft tested |
| --- | --- | --- | --- |
| 1.21.1 | 52.1.20 | Fabric API 0.116.18+1.21.1 | NeoForge 21.1.267, TFC 4.2.16 and Patchouli 93. |
| 1.20.1 | 47.4.28 | Fabric API 0.92.12+1.20.1 | Forge, TFC 3.2.30 and Patchouli 85 |
| 1.19.2 | 43.5.0 | Fabric API 0.77.0+1.19.2 | — |
| 1.18.2 | 40.3.0 | Fabric API 0.77.0+1.18.2 | Forge, TFC 2.2.33 and Patchouli 71.1 |
| 1.16.5 | 36.2.34 | Fabric API 0.42.0+1.16 | — |
| 1.12.2 | 14.23.5.2859 | [Legacy Fabric](https://legacyfabric.net/), generation 1 | Forge, TFC Classic 1.7.23.181 |

On Forge 1.12.2, TFC Classic roads use andesite masonry, local gravel and local soil paths. The wooden piers use TFC timber.

The 1.12.2 adapters recognise the default and flat Overworld generators. Forge also recognises the TFC Classic generator.

## Building from source

The root project builds NeoForge 1.21.1. For Forge or Fabric, select the project under `ports/<loader>-<minecraft>`. For example, from the root run `./gradlew -p ports/fabric-1.19.2 build` or `./gradlew -p ports/legacy-fabric-1.12.2 build`. On Windows use `.\gradlew.bat`. The selected project's `build/libs/` directory contains the installable JAR.

Run the root wrapper on JDK 21. Also install JDK 17 for 1.18.2–1.20.1 or JDK 8 for 1.16.5 and Legacy Fabric 1.12.2. If Gradle cannot find a toolchain, pass `-Porg.gradle.java.installations.paths=/path/to/jdk`. Forge 1.12.2 has its own wrapper. Run `./gradlew build` from `ports/forge-1.12.2` with JDK 8.

## Field checks

The Forge and Fabric servers use vanilla seed `38473917`. Minecraft changed the riverbank between 1.18.2 and 1.19.2, so the bridge and its village turning have different coordinates.

| Versions | What to inspect in the saved world |
| --- | --- |
| 1.19.2, 1.20.1, 1.21.1 | A three-by-three patch of clear bridge deck at X 412, Y 67, Z 740. The village sign at X 319, Y 65, Z 686 gives the bearing and distance. |
| 1.18.2 | Bridge at X 405, Y 67, Z 724. The sign at X 324, Y 65, Z 683 points south, 81 blocks to the village. |
| 1.16.5 | Load the areas around X/Z 0/0, 176/176, 352/352 and 180/−176. Check the paved surface, headroom and sign blocks in the saved chunks. |
| 1.12.2 | The vanilla crossroads stands at X 181, Z 181. Check the timber crossing at X 107, Y 67, Z 362 and both of its banks. The light on the western-facing pier is at X 931, Y 88, Z −157. In TFC Classic the spawn-area crossroads is at X −179, Y 151, Z 169. |

For Forge TFC on 1.18.2 and 1.20.1 use seed `20261121` and load X/Z 0/0, 16/0 and 176/176. On 1.18.2 the first road is shale. On 1.20.1 the stone changes from diorite near the origin to claystone around X/Z 176/176.

The 1.12.2 coast check compares all 997 lighthouse block IDs against the structure drawing in the saved chunks. The crossing and village sign models contain 144 and 84 textured faces respectively. Both models were checked in the 1.21.1 and 1.16.5 Forge and Fabric clients and the 1.12.2 Legacy Fabric client.

Install Python's `nbtlib`, then save and close the world before running the matching check from the repository root.

```sh
python tools/check_village_roads.py path/to/vanilla-world
python tools/check_village_roads.py path/to/vanilla-world path/to/tfc-world
python tools/check_116_roads.py path/to/1.16.5-world
python tools/check_classic_roads.py path/to/1.12.2-world
python tools/check_classic_roads.py path/to/1.12.2-vanilla-world --coast
```
