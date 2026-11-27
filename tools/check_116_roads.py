"""Verify Four Winds paving, headroom and modelled sign data in a saved 1.16.5 world."""
import argparse
import io
import json
import struct
import zlib
from pathlib import Path

import nbtlib


def chunks(folder):
    for filename in sorted((folder / "region").glob("*.mca")):
        with filename.open("rb") as stream:
            table = stream.read(4096)
            for start in range(0, 4096, 4):
                sector = int.from_bytes(table[start:start + 3], "big")
                if not sector:
                    continue
                stream.seek(sector * 4096)
                size, compression = struct.unpack(">IB", stream.read(5))
                assert compression == 2
                root = nbtlib.File.parse(io.BytesIO(zlib.decompress(stream.read(size - 1))))
                level = root["Level"]
                if str(level["Status"]) in ("full", "minecraft:full"):
                    assert "fourwindsroadworks:survey" not in root, "Finished chunks retained a generation survey"
                    yield level


def block(sections, x, y, z):
    section = sections.get(y >> 4)
    if section is None or "Palette" not in section:
        return "minecraft:air"
    palette = section["Palette"]
    if len(palette) == 1:
        return str(palette[0]["Name"])
    bits = max(4, (len(palette) - 1).bit_length())
    index, slot = divmod(((y & 15) << 8) | ((z & 15) << 4) | (x & 15), 64 // bits)
    value = (int(section["BlockStates"][index]) >> (slot * bits)) & ((1 << bits) - 1)
    return str(palette[value]["Name"])


def check(folder):
    info = nbtlib.load(folder / "level.dat")["Data"]
    assert str(info["Version"]["Name"]) == "1.16.5" and int(info["WorldGenSettings"]["seed"]) == 38473917
    signs, clear_road, count = [], [], 0
    paving = {"minecraft:stone_bricks", "minecraft:cracked_stone_bricks"}
    for chunk in chunks(folder):
        count += 1
        sections = {int(s["Y"]): s for s in chunk["Sections"]}
        for post in chunk["TileEntities"]:
            if str(post["id"]) != "minecraft:sign" or "fourwindsroadworks" not in str(post.get("Text1", "")):
                continue
            x, y, z = (int(post[k]) for k in "xyz")
            lines = [json.loads(str(post[f"Text{i}"])) for i in range(1, 5)]
            assert lines[0]["translate"] in ("sign.fourwindsroadworks.crossing", "sign.fourwindsroadworks.village")
            assert block(sections, x, y, z) == "minecraft:oak_sign"
            assert block(sections, x, y-1, z) == "minecraft:oak_fence"
            assert lines[1]["translate"].startswith("sign.fourwindsroadworks.")
            signs.append((x, y, z))
        if len(clear_road) >= 3:
            continue
        for sy, section in sections.items():
            if not any(str(p["Name"]) in paving for p in section.get("Palette", ())):
                continue
            for y in range(sy * 16, sy * 16 + 16):
                for z in range(14):
                    for x in range(15):
                        if block(sections, x, y, z) not in paving:
                            continue
                        if all(block(sections, x+i, y, z+j) in paving and
                               block(sections, x+i, y+1, z+j) == "minecraft:air" and
                               block(sections, x+i, y+2, z+j) == "minecraft:air"
                               for i in range(2) for j in range(3)):
                            clear_road.append((int(chunk["xPos"])*16+x, y, int(chunk["zPos"])*16+z))
                            break
                    else:
                        continue
                    break
                else:
                    continue
                break
            if clear_road and clear_road[-1][0] >> 4 == int(chunk["xPos"]) and clear_road[-1][2] >> 4 == int(chunk["zPos"]):
                break
    assert signs and len(clear_road) >= 3, (signs, clear_road)
    print(f"{count} full chunks, {len(signs)} Four Winds wayposts, clear paving at {clear_road[:3]}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("world", type=Path)
    check(parser.parse_args().world)
