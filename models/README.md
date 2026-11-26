# Editing the wayposts

Open either `.bbmodel` in Blockbench. For a textured preview, copy the following files from `assets/minecraft/textures/block/` in your own Minecraft Java 1.21.1 client JAR to `models/textures/block/`, then reopen the model.

- `oak_log.png` and `oak_log_top.png`
- `stripped_oak_log.png` and `stripped_oak_log_top.png`
- `oak_planks.png` and `spruce_log.png`

The in-game models under [models/block](../src/main/resources/assets/fourwindsroadworks/models/block) reference textures by resource ID. The `tfc/` variants use the same geometry with TFC wood. Keep the arrows aligned with the lettering in `WaypostRenderer` when you move a board.
