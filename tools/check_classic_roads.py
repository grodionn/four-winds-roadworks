"""Check a saved Forge or Legacy Fabric 1.12.2 regression world (seed 38473917, vanilla or TFC Classic)."""
import argparse
import io
import json
import struct
import zlib
from pathlib import Path

import nbtlib
import numpy as np


def populated_chunks(folder):
    for region in sorted((folder / "region").glob("*.mca")):
        with region.open("rb") as stream:
            offsets = stream.read(4096)
            for index in range(0, len(offsets), 4):
                sector = int.from_bytes(offsets[index:index + 3], "big")
                if not sector:
                    continue
                stream.seek(sector * 4096)
                size, compression = struct.unpack(">IB", stream.read(5))
                if compression != 2:
                    raise ValueError(f"Unexpected compression {compression} in {region}")
                data = zlib.decompress(stream.read(size - 1))
                chunk = nbtlib.File.parse(io.BytesIO(data))["Level"]
                if chunk["TerrainPopulated"]:
                    yield chunk


def block_ids(section):
    low = np.asarray(section["Blocks"], dtype=np.uint8).astype(np.uint16)
    if "Add" in section:
        high = np.asarray(section["Add"], dtype=np.uint8).astype(np.uint16)
        low[0::2] |= (high & 15) << 8
        low[1::2] |= (high >> 4) << 8
    return low


def check_coast(folder):
    # Seed 38473917, west-facing pier with the tower on the south side of the jetty.
    level = nbtlib.load(folder / "level.dat")
    assert int(level["Data"]["RandomSeed"]) == 38473917 and str(level["Data"]["generatorName"]) == "default"
    if "FML" in level:
        ids = {str(row["K"]): int(row["V"]) for row in level["FML"]["Registries"]["minecraft:blocks"]["ids"]}
    else:
        # Vanilla numeric block IDs used by the 1.12.2 drawing.
        ids = {
            "minecraft:brick_block": 45, "minecraft:cobblestone": 4, "minecraft:dark_oak_fence": 191,
            "minecraft:dark_oak_stairs": 164, "minecraft:glowstone": 89, "minecraft:hardened_clay": 172,
            "minecraft:log": 17, "minecraft:log2": 162, "minecraft:mossy_cobblestone": 48,
            "minecraft:nether_brick": 112, "minecraft:nether_brick_fence": 113,
            "minecraft:nether_brick_stairs": 114, "minecraft:planks": 5, "minecraft:quartz_block": 155,
            "minecraft:quartz_stairs": 156, "minecraft:spruce_fence": 188, "minecraft:spruce_stairs": 134,
            "minecraft:stone": 1, "minecraft:stone_brick_stairs": 109, "minecraft:stone_slab": 44,
            "minecraft:stonebrick": 98, "minecraft:trapdoor": 96, "minecraft:wooden_slab": 126,
        }
    drawing = Path(__file__).resolve().parents[1] / "src/mc1.12.2/resources/assets/fourwindsroadworks/structures/coastal_lighthouse.nbt"
    template = nbtlib.load(drawing)
    by_chunk = {}
    for entry in template["blocks"]:
        along, height, side = map(int, entry["pos"])
        x, y, z = 940 - along, 65 + height, -176 + side
        name = str(template["palette"][int(entry["state"])]["Name"])
        by_chunk.setdefault((x >> 4, z >> 4), []).append((x, y, z, ids[name]))
    checked = 0
    for chunk in populated_chunks(folder):
        positions = by_chunk.get((int(chunk["xPos"]), int(chunk["zPos"])))
        if not positions:
            continue
        sections = {int(section["Y"]): block_ids(section) for section in chunk["Sections"]}
        for x, y, z, block in positions:
            index = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
            assert sections[y >> 4][index] == block, ("incorrect lighthouse block", x, y, z)
            checked += 1
    assert checked == len(template["blocks"]), "Generate and save the coast around X 930, Z -161"
    print(f"{checked} lighthouse blocks match the drawing; beacon at X 930, Y 88, Z -161")


def check(folder):
    level = nbtlib.load(folder / "level.dat")
    assert int(level["Data"]["RandomSeed"]) == 38473917, "Unexpected test seed"
    tfc = str(level["Data"]["generatorName"]) == "tfc_classic"
    forge = "FML" in level
    registry = {str(row["K"]): int(row["V"]) for row in level["FML"]["Registries"]["minecraft:blocks"]["ids"]} if forge else {"minecraft:stonebrick": 98, "minecraft:standing_sign": 63}
    masonry = registry["tfc:bricks/andesite" if tfc else "minecraft:stonebrick"]
    expected = (-179, 151, 169) if tfc else (181, 72 if forge else 73, 181)
    sign = registry["fourwindsroadworks:tfc_waypost" if tfc else "fourwindsroadworks:waypost" if forge else "minecraft:standing_sign"]
    start_x, height, start_z = (-177, 145, 82) if tfc else (107, 64, 360)
    pavement = {(x, start_z + dz) for x in range(start_x, start_x + 2) for dz in range(3)}
    found = set()
    masonry_chunks, populated, posts = 0, 0, set()
    for chunk in populated_chunks(folder):
        populated += 1
        sections = {int(section["Y"]): block_ids(section) for section in chunk["Sections"]}
        if sum(np.count_nonzero(ids == masonry) for ids in sections.values()) >= 10:
            masonry_chunks += 1
        for x, z in pavement:
            if (x >> 4, z >> 4) == (int(chunk["xPos"]), int(chunk["zPos"])):
                index = ((height & 15) << 8) | ((z & 15) << 4) | (x & 15)
                if sections[height >> 4][index] == masonry:
                    found.add((x, z))
        for entity in chunk["TileEntities"]:
            if forge:
                is_post = str(entity["id"]) == "fourwindsroadworks:waypost"
            else:
                is_post = (str(entity["id"]) == "minecraft:sign" and
                           "sign.fourwindsroadworks." in str(entity.get("Text1", "")))
                if is_post:
                    assert json.loads(str(entity["Text1"]))["translate"] in (
                        "sign.fourwindsroadworks.crossing", "sign.fourwindsroadworks.village")
            if is_post:
                x, y, z = (int(entity[k]) for k in ("x", "y", "z"))
                index = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
                assert sections[y >> 4][index] == sign, (x, y, z, "missing waypost block")
                posts.add((x, y, z))
    assert expected in posts, ("missing crossroads", expected, sorted(posts))
    assert found == pavement, ("missing road surface", sorted(pavement - found))
    print(f"{'TFC Classic' if tfc else 'Vanilla'}: {populated} populated chunks, "
          f"{masonry_chunks} with masonry, {len(posts)} saved wayposts")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("world", type=Path)
    parser.add_argument("--coast", action="store_true", help="Check the complete vanilla lighthouse near X 930, Z -161")
    args = parser.parse_args()
    (check_coast if args.coast else check)(args.world)
