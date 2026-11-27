"""Check a saved Forge 1.12.2 regression world (seed 38473917, vanilla or TFC Classic)."""
import argparse
import io
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


def check(folder):
    level = nbtlib.load(folder / "level.dat")
    assert int(level["Data"]["RandomSeed"]) == 38473917, "Unexpected test seed"
    tfc = str(level["Data"]["generatorName"]) == "tfc_classic"
    registry = {str(row["K"]): int(row["V"]) for row in level["FML"]["Registries"]["minecraft:blocks"]["ids"]}
    masonry = registry["tfc:bricks/andesite" if tfc else "minecraft:stonebrick"]
    expected = (-179, 151, 169) if tfc else (181, 72, 181)
    sign = registry["fourwindsroadworks:tfc_waypost" if tfc else "fourwindsroadworks:waypost"]
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
            if str(entity["id"]) == "fourwindsroadworks:waypost":
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
    check(parser.parse_args().world)
