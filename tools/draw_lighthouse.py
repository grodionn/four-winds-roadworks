"""Rebuild the two Java Edition structure templates with Python and nbtlib.

Coordinates run seaward (s), across the quay (u), and up from its deck (h).
The generator supplies the tide-dependent foundations, gangway and timber deck.
"""
from collections import Counter, deque
from pathlib import Path
import gzip
import io

import nbtlib as nbt


TOWER = (10, 3)
MIN_U, MAX_U = -12, 12
GALLERY = 20
HEIGHT = 34
OUTPUT = Path(__file__).resolve().parents[1] / "src/main/resources/data/fourwindsroadworks/structure"
blocks = {}
faces = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}
opposite = {"east": "west", "west": "east", "south": "north", "north": "south"}
left_of = {"east": "north", "north": "west", "west": "south", "south": "east"}

# CoastalPier continues the andesite plinth down to the seabed.
materials = {
    "rubble": ("cobblestone", "rock/cobble/andesite"),
    "wet": ("mossy_cobblestone", "rock/mossy_cobble/andesite"),
    "ashlar": ("stone_bricks", "rock/bricks/andesite"),
    "cracked": ("cracked_stone_bricks", "rock/cracked_bricks/andesite"),
    "base_step": ("stone_brick_stairs", "rock/bricks/andesite_stairs"),
    "base_slab": ("stone_brick_slab", "rock/bricks/andesite_slab"),
    "chalk": ("calcite", "rock/smooth/chalk"),
    "lime_brick": ("polished_diorite", "rock/bricks/chalk"),
    "old_lime": ("diorite", "rock/cracked_bricks/chalk"),
    "sill": ("polished_diorite_slab", "rock/bricks/chalk_slab"),
    "white_step": ("polished_diorite_stairs", "rock/bricks/chalk_stairs"),
    "red": ("bricks", "rock/bricks/chert"),
    "old_red": ("terracotta", "rock/cracked_bricks/chert"),
    "tile": ("deepslate_tiles", "rock/bricks/shale"),
    "tile_step": ("deepslate_tile_stairs", "rock/bricks/shale_stairs"),
    "finial": ("deepslate_tile_wall", "rock/bricks/shale_wall"),
    "mast": ("dark_oak_log", "wood/log/chestnut"),
    "beam": ("stripped_dark_oak_log", "wood/stripped_log/chestnut"),
    "oak": ("stripped_oak_log", "wood/stripped_log/oak"),
    "pine": ("spruce_planks", "wood/planks/pine"),
    "pine_step": ("spruce_stairs", "wood/planks/pine_stairs"),
    "pine_slab": ("spruce_slab", "wood/planks/pine_slab"),
    "rail": ("dark_oak_fence", "wood/fence/chestnut"),
    "shutter": ("dark_oak_trapdoor", "wood/trapdoor/chestnut"),
    "vane": ("acacia_trapdoor", "wood/trapdoor/spruce"),
    "shingle": ("dark_oak_stairs", "wood/planks/chestnut_stairs"),
    "ridge": ("dark_oak_slab", "wood/planks/chestnut_slab"),
    "fire": ("campfire", "firepit"),
    "belay": ("dark_oak_slab", "rock/rope_anchor/andesite"),
    "line": ("chain", "hanging_rope"),
    "barrel": ("barrel", "wood/barrel/chestnut"),
}


def put(s, u, h, material, **properties):
    assert 0 <= s <= 16 and MIN_U <= u <= MAX_U and 1 <= h <= HEIGHT
    blocks[s, u, h] = material, properties


def cut(s, u, h):
    blocks.pop((s, u, h), None)


def tower(x, z, h, material, **props):
    put(TOWER[0] + x, TOWER[1] + z, h, material, **props)


def octagon(r):
    return {(x, z) for x in range(-r, r + 1) for z in range(-r, r + 1)
            if abs(x) + abs(z) <= r + (r + 1) // 2}


def rim(cells):
    return {(x, z) for x, z in cells if any((x + a, z + b) not in cells for a, b in faces)}


def inward(x, z):
    if abs(x) > abs(z):
        return "west" if x > 0 else "east"
    return "north" if z > 0 else "south"


def battered_ring(r, h, tile, flat, *, inverted=False):
    cells = octagon(r)
    for x, z in rim(cells):
        facing = inward(x, z)
        shape = "straight"
        if (abs(x) == abs(z) or (abs(x) < r and abs(z) < r)) and x and z:
            inside_x, inside_z = ("west" if x > 0 else "east"), ("north" if z > 0 else "south")
            facing = inside_x
            shape = "outer_left" if left_of[facing] == inside_z else "outer_right"
        tower(x, z, h, tile, facing=facing, half="top" if inverted else "bottom", shape=shape)
    for x, z in cells - rim(cells) - octagon(max(r - 2, 0)):
        tower(x, z, h, flat)


def masonry():
    # The heavy toe, two setbacks and broad daymark read at sailing distance.
    for h in range(1, GALLERY):
        r = 4 if h <= 6 else 3 if h <= 14 else 2
        for x, z in rim(octagon(r)):
            corner = abs(x) != 0 and abs(z) != 0 and (abs(x) + abs(z) >= r + (r + 1) // 2)
            if h <= 2:
                mat = "wet" if z < 1 and x >= 0 else "rubble"
            elif h <= 5:
                mat = "cracked" if x >= 2 and z <= 1 and h <= 4 else "ashlar"
            elif 10 <= h <= 12:
                mat = "old_red" if z < 0 and x >= 1 and h < 12 else "red"
                if h==10 and x<=-2 and z>=1:
                    mat="old_lime"
            else:
                mat = "lime_brick" if corner or h in (7, 14, 19) else "chalk"
                if z > 0 and x < 0 and h in (8, 9):
                    mat = "old_lime"
            tower(x, z, h, mat)
    battered_ring(5, 1, "base_step", "ashlar")
    battered_ring(4, 6, "base_step", "ashlar")
    battered_ring(3, 14, "white_step", "lime_brick")
    # A stepped arch through the quay wall and an open threshold below.
    for x in (-1, 0, 1):
        for h in range(1, 4 if x == 0 else 3):
            cut(TOWER[0] + x, TOWER[1] - 4, h)
        cut(TOWER[0] + x, TOWER[1] - 5, 1)
    for x in (-2, 2):
        for h in range(1, 4):
            tower(x, -4, h, "ashlar")
        tower(x, -5, 1, "base_step", facing="south")
    for x in (-1, 1):
        tower(x, -4, 3, "base_step", facing="west" if x < 0 else "east", half="top")
    tower(0, -4, 4, "lime_brick")
    # Deep-set openings turn with the spiral. Their stone sills cast an actual shadow.
    for x, z, first, height in [(4, 0, 2, 3), (-4, 0, 2, 3), (0, 3, 8, 2),
                                (3, 0, 8, 2), (0, -3, 12, 2), (-3, 0, 11, 2),
                                (0, 2, 16, 2), (2, 0, 16, 2), (0, -2, 16, 2)]:
        for h in range(first, first + height):
            cut(TOWER[0] + x, TOWER[1] + z, h)
        dx = (x > 0) - (x < 0)
        dz = (z > 0) - (z < 0)
        tower(x + dx, z + dz, first - 1, "sill", type="top")
        if first in (8, 12):
            tower(x + dx, z + dz, first + height, "base_slab", type="bottom")
    # The dry workbench fits against the far wall; the door and stairs stay clear.
    tower(0, 3, 1, "pine_step", facing="south", half="top")
    tower(-1, 3, 1, "pine_slab", type="top")
    for z in (-1,1):
        tower(3,z,1,"shingle",facing="east",half="top")
    tower(3,0,1,"pine_slab",type="top")
    tower(3,1,2,"shutter",facing="east",open="true")


def spiral():
    # Four risers per revolution leave three blocks between successive landings.
    flight = [(-1, 0, "south"), (-1, 1, None), (0, 1, "east"), (1, 1, None),
              (1, 0, "north"), (1, -1, None), (0, -1, "west"), (-1, -1, None)]
    for i in range(0, GALLERY, 4):
        for step, (x, z, direction) in enumerate(flight):
            h = i + step // 2 + 1
            if direction:
                tower(x, z, h, "pine_step", facing=direction)
            else:
                tower(x, z, h, "pine")
            for above in (h + 1, h + 2):
                cut(TOWER[0] + x, TOWER[1] + z, above)
    for h in range(1, GALLERY + 1):
        tower(0, 0, h, "oak", axis="y")


def lantern_room():
    # Individual corbels support the projecting gallery, with open recesses between them.
    for x,z in octagon(3)-octagon(1):
        tower(x,z,19,"ashlar")
    for r,h in ((3,18),(4,19)):
        for x,z in rim(octagon(r)):
            if x==0 or z==0 or abs(x)==abs(z):
                tower(x,z,h,"base_step",facing=inward(x,z),half="top")
    gallery = octagon(4)
    for x, z in gallery:
        if max(abs(x), abs(z)) > 1:
            tower(x,z,GALLERY,"lime_brick" if (x,z) in rim(gallery) else "pine")
    # Fill the little inside elbows so the railed octagon has no diagonal openings.
    rail = {(x, z) for x, z in gallery if any((x + a, z + b) not in gallery
            for a in (-1, 0, 1) for b in (-1, 0, 1))}
    for x, z in rail:
        if (abs(x), abs(z)) in ((4, 1), (1, 4)):
            tower(x, z, 21, "finial")
        else:
            tower(x, z, 21, "rail", **{face: "true" for (dx, dz), face in faces.items() if (x+dx,z+dz) in rail})
    for x in (-2, 2):
        for z in (-2, 2):
            tower(x, z, 21, "ashlar")
            tower(x,z,22,"beam",axis="y")
            for h in (23,24,25):
                tower(x, z, h, "rail")
            tower(x,z,26,"beam",axis="y")
    # Two short shutter leaves shelter the windward corner. The fire is visible from all four sides.
    for z in (-1, 1):
        tower(-2, z, 22, "shutter", facing="west", open="true")
    for h, mat in [(21,"ashlar"),(22,"cracked"),(23,"tile"),(24,"fire")]:
        tower(0, 0, h, mat, **({"lit": "true"} if mat == "fire" else {}))
    for x, z in octagon(3):
        tower(x,z,27,"beam",axis="x" if abs(z)>=2 else "z")
    # A flared skirt, four slopes, a stone finial and a thin timber wind vane.
    for r,h in [(4,28),(3,29),(2,30),(1,31)]:
        for x, z in octagon(r):
            tower(x,z,h,"tile")
        battered_ring(r,h,"tile_step","tile")
    tower(0,0,32,"finial")
    tower(0,0,33,"rail",east="true",west="true")
    tower(-1,0,33,"rail",east="true")
    tower(1,0,33,"rail",west="true")
    tower(-1,0,34,"vane",open="false",half="bottom",facing="east")
    tower(0,0,34,"rail")


def keeper_store():
    # This separate low gable leans into the tower on the sheltered side.
    for s in range(5, 11):
        for u in range(6, 11):
            if s not in (5, 10) and u not in (6, 10):
                continue
            post = s in (5, 10) and u in (6, 10)
            for h in range(1, 5):
                if s==5 and u==8 and h<4 or u==10 and s in (7,8) and h in (2,3):
                    continue
                if u<=7 and s>=7:  # masonry of the tower is the party wall
                    continue
                if post:
                    put(s,u,h,"mast",axis="y")
                elif h==4:
                    put(s,u,h,"beam",axis="x" if u==10 else "z")
                else:
                    put(s,u,h,"pine")
    for s in range(4,12):
        for u in range(5,12):
            if s>=7 and u<7:
                continue
            h=8-abs(u-8)
            if u==8:
                put(s,u,h,"ridge",type="bottom")
            else:
                put(s,u,h,"shingle",facing="south" if u<8 else "north")
            if s in (5,10):
                for y in range(5,h):
                    if y==5 or s==5 or u>=8:
                        put(s,u,y,"beam" if y==5 else "pine",**({"axis":"z"} if y==5 else {}))
    put(5,8,6,"shutter",facing="west",open="true")
    for s in (7,8):
        put(s,11,1,"pine_slab",type="top")
    for s in (6,9):
        put(s,11,2,"shutter",facing="south",open="true")
        put(s,11,3,"shutter",facing="south",open="true")
    put(7,9,1,"pine_step",facing="south",half="top")
    put(8,9,1,"pine_slab",type="top")
    put(5,7,4,"beam",axis="z")


def working_quay():
    for s,u in [(4,-2),(8,-4),(12,-10),(15,-10),(16,-3)]:
        put(s,u,1,"mast",axis="y")
    # Framed windlass, diagonal jib brace, sheave and wooden lifting block.
    for s in (13,15):
        for h in range(1,4):
            put(s,-5,h,"beam",axis="y")
    for s in range(13,16):
        put(s,-5,3,"oak",axis="x")
    put(12,-5,3,"shutter",facing="west",open="true")
    put(16,-5,3,"shutter",facing="east",open="true")
    for h in range(1,8):
        put(14,-6,h,"mast",axis="y")
    for i in (1,2,3):
        put(14,-6-i,3+i,"beam",axis="z")
        put(14,-6-i,4+i,"shingle",facing="north",half="top")
    for u in range(-11,-4):
        put(14,u,7,"beam",axis="z")
    for u in (-12,-10,-6):
        put(14,u,8,"ridge",type="bottom")
    put(14,-11,8,"belay")
    for h in (4,5,6,7):
        put(14,-12,h,"line")
    put(14,-12,3,"shutter",facing="north",open="false",half="top")
    # Short timbers stored off the walking line, with the round ends facing the quay.
    for s in (7,8):
        put(s,-3,1,"mast",axis="x")
    put(8,-3,2,"pine_slab",type="bottom")
    put(15,-8,1,"barrel",facing="up")


# The flattened block IDs did not exist in 1.12. Variants and slab halves were block properties.
classic_materials = {
    "rubble": ("cobblestone", "cobble/andesite"), "wet": ("mossy_cobblestone", "cobble/andesite"),
    "ashlar": ("stonebrick", "bricks/andesite"), "cracked": ("stonebrick", "bricks/andesite"),
    "base_step": ("stone_brick_stairs", "stairs/bricks/andesite"), "base_slab": ("stone_slab", "slab/bricks/andesite"),
    "chalk": ("quartz_block", "smooth/chalk"), "lime_brick": ("stone", "bricks/chalk"),
    "old_lime": ("stone", "raw/chalk"), "sill": ("stone_slab", "slab/bricks/chalk"),
    "white_step": ("quartz_stairs", "stairs/bricks/chalk"), "red": ("brick_block", "bricks/chert"),
    "old_red": ("hardened_clay", "bricks/chert"), "tile": ("nether_brick", "bricks/shale"),
    "tile_step": ("nether_brick_stairs", "stairs/bricks/shale"), "finial": ("nether_brick_fence", "wall/bricks/shale"),
    "mast": ("log2", "wood/log/chestnut"), "beam": ("log2", "wood/log/chestnut"), "oak": ("log", "wood/log/oak"),
    "pine": ("planks", "wood/planks/pine"), "pine_step": ("spruce_stairs", "stairs/wood/pine"),
    "pine_slab": ("wooden_slab", "slab/wood/pine"), "rail": ("dark_oak_fence", "wood/fence/chestnut"),
    "shutter": ("trapdoor", "wood/trapdoor/chestnut"), "vane": ("trapdoor", "wood/trapdoor/spruce"),
    "shingle": ("dark_oak_stairs", "stairs/wood/chestnut"), "ridge": ("wooden_slab", "slab/wood/chestnut"),
    "fire": ("glowstone", "firepit"), "belay": ("wooden_slab", "slab/bricks/andesite"),
    "line": ("spruce_fence", "wood/fence/pine"), "barrel": ("planks", "wood/barrel/chestnut"),
}
classic_variants = {"cracked": "cracked_stonebrick", "lime_brick": "smooth_diorite", "old_lime": "diorite",
                    "base_slab": "stone_brick", "sill": "quartz", "mast": "dark_oak", "beam": "dark_oak",
                    "pine": "spruce", "pine_slab": "spruce", "ridge": "dark_oak", "belay": "dark_oak", "barrel": "spruce"}


def classic_state(mat, props, tfc):
    props = dict(props)
    if mat in ("line", "belay", "fire", "barrel"):
        props = {}
    if "type" in props:
        props["half"] = props.pop("type")
    if not tfc and mat in classic_variants:
        props["variant"] = classic_variants[mat]
    return ("tfc:" if tfc else "minecraft:") + classic_materials[mat][int(tfc)], props


def save(tfc, version):
    palette, indexes, entries = [], {}, []
    for (s,u,h),(mat,props) in sorted(blocks.items(),key=lambda entry:(entry[0][2], entry[0][0], entry[0][1])):
        name = ("tfc:" if tfc else "minecraft:")+materials[mat][int(tfc)]
        if mat=="line":props={"facing":"south"} if tfc else {"axis":"y"}
        if mat=="belay":props={"facing":"north"} if tfc else {"type":"bottom"}
        if tfc and version in ("1.18.2", "1.20.1"):
            if name.startswith(("tfc:wood/fence/", "tfc:wood/trapdoor/")):
                _, kind, timber = name.split("/")
                name = f"tfc:wood/planks/{timber}_{kind}"
            elif mat == "line":
                name, props = "tfc:wood/planks/pine_fence", {}
            elif mat == "belay":
                name, props = "tfc:rock/bricks/andesite_slab", {"type": "bottom"}
        if version == "1.12.2":
            name, props = classic_state(mat, props, tfc)
        elif version == "1.16.5":
            substitutions = {"minecraft:calcite": "minecraft:quartz_block", "minecraft:deepslate_tiles": "minecraft:polished_blackstone_bricks",
                             "minecraft:deepslate_tile_stairs": "minecraft:polished_blackstone_brick_stairs",
                             "minecraft:deepslate_tile_wall": "minecraft:polished_blackstone_brick_wall"}
            name = substitutions.get(name, name)
        key = (name,tuple(sorted(props.items())))
        if key not in indexes:
            indexes[key]=len(palette)
            state=nbt.Compound({"Name":nbt.String(name)})
            if props:
                state["Properties"]=nbt.Compound({k:nbt.String(v) for k,v in props.items()})
            palette.append(state)
        entries.append(nbt.Compound({"pos":nbt.List[nbt.Int]([s,h-1,u-MIN_U]),"state":nbt.Int(indexes[key])}))
    structure=nbt.File({"DataVersion":nbt.Int({"1.21.1": 3955, "1.20.1": 3465, "1.19.2": 3120, "1.18.2": 2975, "1.16.5": 2586, "1.12.2": 1343}[version]),"size":nbt.List[nbt.Int]([17,HEIGHT,MAX_U-MIN_U+1]),
                        "palette":nbt.List[nbt.Compound](palette),"blocks":nbt.List[nbt.Compound](entries),
                        "entities":nbt.List[nbt.Compound]()})
    folder = OUTPUT if version == "1.21.1" else Path(__file__).resolve().parents[1] / f"src/mc{version}/resources/data/fourwindsroadworks/structures"
    if version == "1.12.2":
        folder = Path(__file__).resolve().parents[1] / "src/mc1.12.2/resources/assets/fourwindsroadworks/structures"
    dest=folder/("tfc/coastal_lighthouse.nbt" if tfc else "coastal_lighthouse.nbt")
    dest.parent.mkdir(parents=True,exist_ok=True)
    raw=io.BytesIO()
    structure.write(raw)
    dest.write_bytes(gzip.compress(raw.getvalue(),mtime=0))


if __name__=="__main__":
    masonry()
    keeper_store()
    working_quay()
    lantern_room()
    spiral()
    assert blocks[10,3,24][0]=="fire" and Counter(mat for mat,_ in blocks.values())["fire"]==1
    for x in (-1,0,1):
        assert (10+x,-1,1) not in blocks and (10+x,-1,2) not in blocks
    anchored={pos for pos in blocks if pos[2]==1}
    queue=deque(anchored)
    while queue:
        s,u,h=queue.popleft()
        for neighbor in ((s+1,u,h),(s-1,u,h),(s,u+1,h),(s,u-1,h),(s,u,h+1),(s,u,h-1)):
            if neighbor in blocks and neighbor not in anchored:
                anchored.add(neighbor)
                queue.append(neighbor)
    assert anchored==blocks.keys(),set(blocks)-anchored
    for version in ("1.21.1", "1.20.1", "1.19.2", "1.18.2", "1.16.5", "1.12.2"):
        for tfc in ((False,) if version in ("1.19.2", "1.16.5") else (False, True)):
            save(tfc, version)
    print(len(blocks),"placed blocks in each structure")
