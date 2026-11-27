"""Check village turnings, bridges and TFC road materials in saved Minecraft worlds.

Generate the plains village and river east of it on vanilla seed 38473917, and
the Ice and Fire settlement at (4240, -608) on TFC seed 20261121. To check
forest soils too, generate the crossings near (405, -688), (690, 164) and (689, 92) on
TFC seed -583271 twice, approaching from opposite directions. Save and close
the worlds before running this script. Requires nbtlib.
"""
import argparse
import json
import math
from pathlib import Path

from check_regression_worlds import World
from check_track_support import area_chunks, tracks


def canonical_component(component):
    if isinstance(component, dict):
        if set(component) == {'text'}:
            return component['text']
        if 'extra' in component:
            return {**component, 'extra': [canonical_component(child) for child in component['extra']]}
    return component


def check_floodplain(world):
    # These four blocks used to be masonry inside the built-up village.
    for x, y, z in [(351, 67, 714), (353, 67, 714), (354, 66, 714), (359, 66, 752)]:
        world.expect(x, y, z, 'minecraft:air')
    # The 1.18.2 generator places the village on a different bend in the river.
    bridge_x, bridge_z = (405, 724) if world.version == '1.18.2' else (412, 740)
    for z in range(bridge_z, bridge_z + 3):
        for x in range(bridge_x, bridge_x + 3):
            world.expect(x, 67, z, 'minecraft:stone_bricks')
            for y in (68, 69):
                world.expect(x, y, z, 'minecraft:air')
    if world.version == '1.18.2':
        world.expect(405, 62, 722, 'minecraft:water')
        world.expect(405, 64, 722, 'minecraft:air')
        for x in (403, 408):
            world.expect(x, 68, 722, 'minecraft:stone_brick_slab', type='bottom')
    # A cocoa pod on the trunk or moss on mangrove roots could veto an entire road column.
    for x, y, z in [(1372, 67, -2360), (1373, 67, -2361), (1373, 67, -2359), (1374, 66, -2360)]:
        if 'leaves' in world.block(x, y, z)[0] or 'roots' in world.block(x, y, z)[0]:
            raise ValueError(f'Forest growth in place of the road at {x}, {y}, {z}')
        for dy in (1, 2):
            world.expect(x, y + dy, z, 'minecraft:air')

    world.expect(1372, 67, -2360, 'minecraft:coarse_dirt')
    world.expect(1373, 67, -2360, 'minecraft:dirt_path')
    # Walk the jungle lane for eleven rows. The exact stripe shifts with the verge.
    previous = set()
    for x in range(1560, 1571):
        row = {(z, y) for z in range(-2462, -2450) for y in range(62, 70)
               if world.block(x, y, z)[0] in ('minecraft:dirt_path', 'minecraft:coarse_dirt', 'minecraft:gravel')
               and all(world.block(x, y + h, z)[0] == 'minecraft:air' for h in (1, 2))}
        if not any((z + 1, y) in row for z, y in row):
            raise ValueError(f'Jungle lane narrows to less than two walkable blocks at X {x}')
        if previous and not any(z == pz and abs(y - py) <= 1 for z, y in row for pz, py in previous):
            raise ValueError(f'Jungle lane is disconnected at X {x}')
        previous = row

    if world.version == '1.21.1':
        # A bridge approach used to extend across the dry meadow ten blocks above the grass.
        world.expect(410, 63, 685, 'minecraft:grass_block')
        for y in range(65, 75):
            world.expect(410, y, 685, 'minecraft:air')
        for x in range(396, 415):
            for z in range(679, 703):
                for y in range(69, 78):
                    block = world.block(x, y, z)[0]
                    if block.startswith(('minecraft:stone_brick', 'minecraft:cobblestone', 'minecraft:mossy_cobblestone',
                                         'minecraft:spruce_planks', 'minecraft:stripped_spruce_log')):
                        raise ValueError(f'Bridge approach extends above the meadow at {x}, {y}, {z}: {block}')

    # Read the available faces of the waypost on the low bank at the village turn.
    for cx in (19, 20):
        for cz in (42, 43):
            for post in world.chunk(cx, cz)['block_entities']:
                if 'front_text' not in post and 'Text1' not in post:
                    continue
                x, y, z = (int(post[key]) for key in 'xyz')
                expected_post = (324, 65, 683) if world.version == '1.18.2' else (319, 65, 686)
                if (x, y, z) != expected_post:
                    continue
                south = {'translate': 'sign.fourwindsroadworks.south'}
                front_direction = {**south, 'extra': [' →']}
                if 'front_text' in post:
                    faces = [(post['front_text']['messages'], front_direction),
                             (post['back_text']['messages'], {'text': '← ', 'extra': [south]})]
                else:
                    faces = [([post[f'Text{i}'] for i in range(1, 5)], front_direction)]
                for messages, direction in faces:
                    lines = [json.loads(str(line)) for line in messages]
                    expected = round(math.hypot(332 - x, 764 - z))
                    if (lines[0].get('translate') != 'sign.fourwindsroadworks.village'
                            or canonical_component(lines[1]) != direction
                            or lines[2].get('translate') != 'sign.fourwindsroadworks.distance'):
                        raise ValueError(f'Incorrect village directions at {x}, {y}, {z}: {lines}')
                    distance = lines[2]['with'][0]
                    if int(distance['text'] if isinstance(distance, dict) else distance) != expected:
                        raise ValueError(f'Incorrect waypost distance at {x}, {y}, {z}')
                world.expect(x, y - 1, z, 'minecraft:oak_fence')
                world.expect(x, y - 2, z, 'minecraft:mossy_cobblestone')
                world.expect(x, y - 3, z, 'minecraft:cobblestone')
                return
    raise ValueError('Missing waypost at the plains village turn')


def check_pixie_street(world):
    world.expect(4235, 70, -606, 'iceandfire:pixie_house_birch', facing='east')
    world.expect(4235, 69, -606, 'tfc:duff/podzol')
    world.expect(4235, 68, -606, 'tfc:dirt/podzol')
    world.expect(4238, 70, -605, 'iceandfire:pixie_house_birch', facing='south')
    world.expect(4238, 69, -605, 'tfc:dirt/podzol')
    for z in (-606, -605, -604):
        world.expect(4236, 69, z, 'minecraft:dirt_path')


def check_waysides(world):
    for x in range(4120, 4128):
        lane = [world.block(x, 65, z)[0] for z in range(-1397, -1391)]
        if not 2 <= sum(b in ('tfc:grass_path/mollisol', 'tfc:coarse_dirt/mollisol') for b in lane) <= 3:
            raise ValueError(f'Missing the earthen TFC track or its verge at X {x}')
    for z in range(-108, -101):
        for x, height, facing in [(4228, 66, 'east'), (4229, 67, 'east'), (4231, 67, 'west'), (4232, 66, 'west')]:
            world.expect(x, height, z, 'tfc:wood/planks/spruce_stairs', facing=facing)
        world.expect(4230, 68, z, 'tfc:wood/planks/spruce_slab', type='bottom')
    for z in (-107, -103):
        for x in (4229, 4231):
            world.expect(x, 63, z, 'tfc:rock/cobble/dacite')
            world.expect(x, 64, z, 'tfc:rock/cobble/dacite')
            world.expect(x, 65, z, 'tfc:wood/fence/spruce')
            world.expect(x, 66, z, 'tfc:wood/stripped_log/spruce', axis='z')
        world.expect(4230, 66, z, 'tfc:wood/stripped_log/spruce', axis='x')
    for z in (-106, -105, -104):
        world.expect(4229, 64, z, 'tfc:wood/planks/spruce_stairs', facing='west')
    for x in range(4230, 4234):
        for y in (64, 65):
            world.expect(x, y, -105, 'minecraft:air')
    world.expect(2442, 67, 1494, 'tfc:rock/cobble/claystone_slab', type='bottom')
    for z in (1495, 1496):
        world.expect(2442, 67, z, 'tfc:rock/cobble/claystone')
    world.expect(2442, 68, 1496, 'tfc:rock/cobble/claystone_slab', type='bottom')


def check_tfc_materials(world):
    if world.version == '1.18.2':
        height, rock = 73, 'shale'
        distant = [(16, 74, z, 'shale') for z in (4, 5, 6)]
    elif world.version == '1.20.1':
        height, rock = 67, 'diorite'
        distant = [(x, 67, z, 'claystone') for x, z in ((176, 176), (176, 177), (177, 176))]
    else:
        raise ValueError(f'No TFC material fixture for {world.version}')
    near = [(x, height, z, rock) for x, z in ((0, 0), (1, 0), (1, 1))]
    for x, y, z, stone in near + distant:
        world.expect(x, y, z, 'tfc:rock/bricks/' + stone)
        world.expect(x, y + 1, z, 'minecraft:air')
        world.expect(x, y + 2, z, 'minecraft:air')


def check_soil_boundaries(first, reverse):
    # Compare every earth or path block in each patch, including the margins of the relocated lane.
    areas = [(394, -698, 418, -678), (665, 142, 720, 185), (678, 78, 700, 106)]
    for cx, cz in sorted(area_chunks(areas)):
        difference = set(tracks(first.chunk(cx, cz))) ^ set(tracks(reverse.chunk(cx, cz)))
        if difference:
            dx, y, dz, material = min(difference)
            raise ValueError(f'Chunk order changed {material} at {16 * cx + dx}, {y}, {16 * cz + dz}')
    for x, y, z, surface, soil in [(409, 64, -691, 'coarse_dirt', 'mollisol'),
                                    (688, 73, 89, 'coarse_dirt', 'entisol'),
                                    (689, 73, 84, 'grass_path', 'podzol')]:
        first.expect(x, y, z, f'tfc:{surface}/{soil}')
        first.expect(x, y - 1, z, f'tfc:dirt/{soil}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('vanilla_world', type=Path)
    parser.add_argument('tfc_world', type=Path, nargs='?', help='Optional TerraFirmaCraft world on seed 20261121')
    parser.add_argument('--waysides', action='store_true', help='Also check the TFC meadow, shelter and mounting steps on 1.21.1')
    parser.add_argument('--soil-worlds', nargs=2, type=Path, metavar=('FORWARD', 'REVERSE'),
                        help='TFC seed -583271 generated in opposite chunk orders')
    args = parser.parse_args()
    if args.waysides and not args.tfc_world:
        parser.error('--waysides needs the TFC world')
    check_floodplain(World(args.vanilla_world, 38473917))
    if args.tfc_world:
        tfc = World(args.tfc_world, 20261121)
        if tfc.version == '1.21.1':
            check_pixie_street(tfc)
        else:
            check_tfc_materials(tfc)
        if args.waysides:
            check_waysides(tfc)
    if args.soil_worlds:
        check_soil_boundaries(*(World(path, -583271) for path in args.soil_worlds))
    print('Saved-world checks passed.')
