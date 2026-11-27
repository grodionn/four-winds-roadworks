# Loader builds

Use the same Minecraft version and loader on the client and server. Fabric also needs Fabric API. The NeoForge JAR keeps its original filename so existing modpack manifests continue to work.

| Minecraft | Loader used in the test world | Additional dependency | Gradle project |
| --- | --- | --- | --- |
| 1.21.1 | NeoForge 21.1.267 | None. TerraFirmaCraft is optional. | `.` |
| 1.21.1 | Forge 52.1.20 | None | `ports/forge-1.21.1` |
| 1.21.1 | Fabric Loader 0.17.3 | Fabric API 0.116.18+1.21.1 | `ports/fabric-1.21.1` |
| 1.20.1 | Forge 47.4.28 | None. Tested with TerraFirmaCraft 3.2.30 and Patchouli 85. | `ports/forge-1.20.1` |
| 1.20.1 | Fabric Loader 0.17.3 | Fabric API 0.92.12+1.20.1 | `ports/fabric-1.20.1` |

From the repository root, run `./gradlew -p ports/fabric-1.21.1 build` with JDK 21, substituting the project from the table. On Windows use `.\gradlew.bat`. The 1.20.1 builds also need a JDK 17 toolchain. The installable JAR is in the selected project's `build/libs/`.

The 1.20.1 and 1.21.1 Forge and Fabric servers each generated fresh vanilla terrain on seed `38473917`. The saved-world checks cover the bridge at X 412, Z 740, the streets around X 350, Z 714 and the jungle roads at X 1372, Z −2360 and X 1566, Z −2455. The [TFC test sites](../CONTRIBUTING.md#working-on-generation) were checked on NeoForge.
