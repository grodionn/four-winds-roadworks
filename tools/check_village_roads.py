"""Check two settlement regressions in saved Minecraft 1.21.1 worlds.

Generate the plains village and river east of it on vanilla seed 38473917, and
the Ice and Fire settlement at (4240, -608) on TFC seed 20261121. To check
forest soils too, generate the crossings near (400, -688) and (688, 160) on
TFC seed -583271 twice, approaching from opposite directions. Save and close
the worlds before running this script. Requires nbtlib.
"""
import argparse
import json
import math
from pathlib import Path

from check_regression_worlds import World


def check_floodplain(world):
    # These four blocks used to be masonry inside the built-up village.
    for x, y, z in [(351, 67, 714), (353, 67, 714), (354, 66, 714), (359, 66, 752)]:
        world.expect(x, y, z, 'minecraft:air')
    for z in range(740, 743):
        for x in range(412, 415):
            world.expect(x, 67, z, 'minecraft:stone_bricks')
            for y in (68, 69):
                world.expect(x, y, z, 'minecraft:air')
    # A cocoa pod on the trunk or moss on mangrove roots could veto an entire road column.
    for x, y, z in [(1372, 67, -2360), (1373, 67, -2361), (1373, 67, -2359),
                    (1374, 66, -2360), (1566, 66, -2455)]:
        if 'leaves' in world.block(x, y, z)[0] or 'roots' in world.block(x, y, z)[0]:
            raise ValueError(f'Forest growth in place of the road at {x}, {y}, {z}')
        for dy in (1, 2):
            world.expect(x, y + dy, z, 'minecraft:air')

    # Read both faces of the waypost on the low bank at the village turn.
    for cx in (19, 20):
        for cz in (42, 43):
            for post in world.chunk(cx, cz)['block_entities']:
                if 'front_text' not in post:
                    continue
                x, y, z = (int(post[key]) for key in 'xyz')
                if abs(x - 321) > 4 or not 684 <= z <= 697:
                    continue
                south = {'translate': 'sign.fourwindsroadworks.south'}
                for face, direction in [('front_text', {**south, 'extra': [' →']}),
                                        ('back_text', {'text': '← ', 'extra': [south]})]:
                    lines = [json.loads(str(line)) for line in post[face]['messages']]
                    expected = round(math.hypot(332 - x, 764 - z))
                    if (lines[0].get('translate') != 'sign.fourwindsroadworks.village'
                            or lines[1] != direction
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


def check_soil_boundaries(first, reverse):
    # Forest patches from the next chunk used to recolour the same wheel tracks differently.
    strips = [(range(400, 411), (64,), (-688,)), (range(677, 681), (71,), (160, 161)),
              ((688, 689), (73,), range(80, 99)), (range(695, 698), (72,), (160, 161)),
              (range(708, 715), (73,), (174, 175))]
    for xs, ys, zs in strips:
        for x in xs:
            for y in ys:
                for z in zs:
                    if first.block(x, y, z) != reverse.block(x, y, z):
                        raise ValueError(f'Chunk order changed the forest road at {x}, {y}, {z}')
    first.expect(678, 71, 160, 'tfc:grass_path/alfisol')
    first.expect(688, 73, 85, 'tfc:grass_path/podzol')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('vanilla_world', type=Path)
    parser.add_argument('tfc_world', type=Path)
    parser.add_argument('--soil-worlds', nargs=2, type=Path, metavar=('FORWARD', 'REVERSE'),
                        help='TFC seed -583271 generated in opposite chunk orders')
    args = parser.parse_args()
    check_floodplain(World(args.vanilla_world, 38473917))
    check_pixie_street(World(args.tfc_world, 20261121))
    if args.soil_worlds:
        check_soil_boundaries(*(World(path, -583271) for path in args.soil_worlds))
    print('All requested world checks passed.')
