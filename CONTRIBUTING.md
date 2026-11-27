# Working on Four Winds

You can open an issue or pull request in English or Russian. For a small fix, a PR with a clear description is enough. For a new structure or a change that reroutes the main roads, an issue with a sketch or a pair of coordinates gives us somewhere to discuss the design first.

## Ways to help

- **Walk a road.** A picture from the approach to a broken bridge or a blocked tunnel can show what a map misses. Include the world preset and whatever coordinates you have in a [generation report](https://github.com/grodionn/four-winds-roadworks/issues/new?template=worldgen.yml). For startup failures or mod conflicts, use the [compatibility form](https://github.com/grodionn/four-winds-roadworks/issues/new?template=compatibility.yml).
- **Translate a waypost.** Copy [the English labels](src/main/resources/assets/fourwindsroadworks/lang/en_us.json) to a file named for your Minecraft locale in the same folder. Keep the keys and the `%s` in the distance label. Check the longest destination on a sign in-game. Both 1.12.2 builds convert these files to the older language format.
- **Photograph a route.** The [gallery](docs/gallery/README.md) lists its seeds and camera positions. For a new shot, hide the HUD, switch the game language to English if a sign is visible, and include the seed, coordinates, mod version and any shaders you used.

## Run it locally

Install a **JDK 21** and clone the repository. The wrapper downloads Gradle and NeoForge on the first run.

| Task | Linux / macOS | Windows PowerShell |
| --- | --- | --- |
| Compile, check the road layout and package the mod | `./gradlew build` | `.\gradlew.bat build` |
| Start a vanilla-terrain development client | `./gradlew runClient` | `.\gradlew.bat runClient` |
| Start a development server | `./gradlew runServer` | `.\gradlew.bat runServer` |

These commands target NeoForge 1.21.1. The [loader build table](ports/README.md) lists the Gradle project, toolchain and saved-world checks for each port. The JAR is written to `build/libs/` in the selected project. To try the TFC preset, use the JAR in a separate Minecraft 1.21.1 NeoForge instance with TerraFirmaCraft and its dependencies. For unattended client tests, set the **Master Volume** in the test instance to zero before launch.

## From a fork to a pull request

1. Fork the project on GitHub and clone your fork. In the clone, run `git remote add upstream https://github.com/grodionn/four-winds-roadworks.git`.
2. Start your change from the current main branch with `git fetch upstream` followed by `git switch -c fix/bridge-landing upstream/main`. Use a branch name that fits your change.
3. Make the change and run the build. For a terrain change, generate fresh chunks on the same seed in vanilla and TFC. Approach the site in-game at road level and look across the chunk boundary.
4. Commit and push your branch with `git push -u origin HEAD`. Open a pull request against `grodionn/four-winds-roadworks:main`. Describe what changed and how to see it. Include before and after screenshots for a visual fix.

The GitHub Actions workflow runs the build and layout checks on pull requests. If a runner is unavailable, run the same build on the proposed commit and record the result in the PR. Maintainers review the change under **Files changed** and use **Squash and merge** to bring it into `main`.

Grodion’s code and original artwork are available under the [MIT licence](LICENSE). Keep the copyright and licence notice when distributing a fork. The Gradle wrapper retains its [Apache 2.0 licence](gradle/wrapper/LICENSE).

## Checking the READMEs

After every patch, read [README.md](README.md) and [README.ru.md](README.ru.md) against the change. Check the affected gameplay details, supported versions, dependencies, commands and links. If both still match, leave them as they are. If something changed, correct just the affected passage in each language, using the same plain language as the surrounding text.

## Release versions

All loaders share `modVersion` in `gradle.properties`. The first public release is `0.1.0`. Bump the number once for each published release. Several fixes can ship together. A change to the README or gallery alone keeps the current number.

- **Patch** (`0.1.0 → 0.1.1`) for fixes that preserve existing saves and supported integrations. Examples include a blocked bridge landing, a missing model or a JAR that the loader rejects. A fix for one loader still uses the shared release number.
- **Minor** (`0.1.2 → 0.2.0`) for a new structure, a new supported platform or a redesign of the road network. During `0.x`, an incompatible change also needs a minor release and an upgrade note in the changelog.
- **Major** starts at `1.0.0` when we commit to a stable save format and integrations. After that, a change that breaks either requires the next major version.

For a release candidate, append `-rc.1`, then `-rc.2` if needed. With release `0.1.0`, the loader displays `0.1.0+mc1.20.1` on Minecraft 1.20.1. GitHub uses one tag, `v0.1.0`, for all builds.

1. Update `modVersion` and [the changelog](CHANGELOG.md). Build every project in the [loader table](ports/README.md) from the same commit.
2. Run `python tools/package_release.py` with Python 3.11 or later. It checks the packaged versions, licences, loader descriptors, mixins and assets. The JARs and `SHA256SUMS.txt` appear in `build/distributions/v<version>/`.
3. Create a draft GitHub release for the tested commit, attach those files and review the list against the loader table. When publishing on CurseForge, upload each JAR separately for its own Minecraft version and loader. Fabric API is required on Fabric 1.16.5 and later. TerraFirmaCraft is optional on the builds listed in the table. The [project description](docs/curseforge.md), [512-pixel cover](art/four-winds-preview.png) and [gallery](docs/gallery) are ready to use on the project page.

## Working on generation

`RoadLayout` places the network, `RoadGrade` selects the vertical profile and `RoadFeature` writes blocks. Bridge surveys, tunnels, village links and coastal piers have their own classes in [the roads package](src/main/java/dev/fourwinds/roads). [RoadLayoutCheck](src/test/java/dev/fourwinds/roads/RoadLayoutCheck.java) exercises the routing, widths, bridge seats and harbour spacing during the build.

The scripts in `tools/` can also inspect saved chunks. Install `nbtlib` in a Python environment, visit the sites below, then save and close the worlds before running the checks. Pass paths to the world directories containing `level.dat`.

| Preset | Seed | Where to go |
| --- | ---: | --- |
| TFC | 7319041 | The gorge at X −1072, Z −894 and the portal at X −971, Z −533. |
| Vanilla | 96320157 | The spring at X −885, Z 270. Keep it loaded for 500 game ticks. |
| Vanilla | 38473917 | The village around X 350, Z 720 and the crossing to its east. |
| TFC with Ice and Fire | 20261121 | The pixie settlement around X 4240, Z −608. |

```sh
python -m pip install nbtlib
python tools/check_regression_worlds.py path/to/tfc-gorge path/to/vanilla-spring
python tools/check_village_roads.py path/to/vanilla-village path/to/tfc-pixies
```

## Working on structures and art

- [Waypost models](models/README.md) open in Blockbench. Their vanilla and TFC exports live under `src/main/resources/assets/fourwindsroadworks/models/block/`.
- [The lighthouse drawing](tools/draw_lighthouse.py) generates both block palettes. Run `python tools/draw_lighthouse.py` after editing the drawing and include the updated NBT templates. [Full in-game ports](schematics) are also available for use with a structure block.
- [The cover frame](art/README.md) is shared by the Grodion's series.
