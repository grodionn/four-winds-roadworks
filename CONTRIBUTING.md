# Working on Four Winds

You can open an issue or pull request in English or Russian. For a small fix, a PR with a clear description is enough. For a new structure or a change that reroutes the main roads, an issue with a sketch or a pair of coordinates gives us somewhere to discuss the design first.

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

The [mod metadata](src/main/resources/META-INF/neoforge.mods.toml) currently says **All Rights Reserved**. Forks for contributing on GitHub are welcome. A licence for distributing your own build is still pending.

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
