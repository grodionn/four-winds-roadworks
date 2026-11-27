"""Check bridge, spring and cave-roof sites in saved Minecraft 1.21.1 worlds.

Requires nbtlib. Generate the TFC gorge and bridge portal (seed 7319041) and vanilla taiga (seed 96320157),
keep the taiga spring loaded for at least 500 normal game ticks, then save and close both worlds.
"""
import argparse
import io
import struct
import zlib
from functools import lru_cache
from pathlib import Path

import nbtlib


class World:
    def __init__(self, path, seed=None):
        self.path = path
        level = nbtlib.load(path / 'level.dat')['Data']
        self.version = str(level['Version']['Name'])
        generation = level['WorldGenSettings']
        self.seed = int(generation['seed'])
        self.generator = generation['dimensions']['minecraft:overworld']['generator'].unpack(json=True)
        if seed is not None and self.seed != seed:
            raise ValueError(f'{path}: expected seed {seed}, found {self.seed}')

    @lru_cache(maxsize=32)
    def chunk(self, cx, cz):
        region = self.path / 'region' / f'r.{cx // 32}.{cz // 32}.mca'
        with region.open('rb') as file:
            file.seek(4 * (cx % 32 + 32 * (cz % 32)))
            offset = int.from_bytes(file.read(4), 'big') >> 8
            if not offset:
                raise ValueError(f'Missing chunk {cx}, {cz} in {region}')
            file.seek(4096 * offset)
            length, compression = struct.unpack('>IB', file.read(5))
            if compression != 2:
                raise ValueError(f'Unsupported region compression {compression} in {region}')
            data = nbtlib.File.parse(io.BytesIO(zlib.decompress(file.read(length - 1))))
        if str(data.get('Status')) not in ('minecraft:full', 'full'):
            raise ValueError(f'Generate and save chunk {cx}, {cz} first')
        return data

    @lru_cache(maxsize=128)
    def section(self, cx, sy, cz):
        return next((s['block_states'] for s in self.chunk(cx, cz)['sections']
                     if int(s['Y']) == sy and 'block_states' in s), None)

    def block(self, x, y, z):
        section = self.section(x // 16, y // 16, z // 16)
        if section is None:
            return 'minecraft:air', {}
        palette = section['palette']
        index = 0
        if len(palette) > 1:
            bits = max(4, (len(palette) - 1).bit_length())
            packed, slot = divmod((y % 16) * 256 + (z % 16) * 16 + x % 16, 64 // bits)
            index = (int(section['data'][packed]) >> (slot * bits)) & ((1 << bits) - 1)
        entry = palette[index]
        return str(entry['Name']), entry.get('Properties', {})

    def original_surface(self, x, z):
        chunk = self.chunk(x // 16, z // 16)
        for container in ('neoforge:attachments', 'fabric:attachments', 'fourwindsroadworks'):
            data = chunk.get(container, {})
            survey = data.get('fourwindsroadworks:surveyed_surface', data.get('surveyed_surface', {}))
            heights = survey.get('heights', ())
            if len(heights) == 128:
                column = z % 16 * 16 + x % 16
                packed = (int(heights[column // 2]) >> (16 * (column % 2))) & 0xffff
                return (packed if packed < 32768 else packed - 65536) >> 1, bool(packed & 1)
        raise ValueError(f'Missing original terrain survey at {x}, {z}')

    def expect(self, x, y, z, name, **properties):
        block, state = self.block(x, y, z)
        if block != name or any(str(state.get(k)) != v for k, v in properties.items()):
            raise ValueError(f'{x}, {y}, {z}: expected {name} {properties}, found {block} {state}')


def check_gorge(world):
    for x in range(-1090, -1067):
        for z in range(-896, -891):
            bearing = x in (-1090, -1068)
            world.expect(x, 83, z, 'tfc:rock/smooth/andesite' if bearing else 'tfc:rock/bricks/andesite')
            if abs(z + 894) == 2:
                if bearing:
                    world.expect(x, 84, z, 'tfc:rock/chiseled/andesite')
                else:
                    world.expect(x, 84, z, 'tfc:rock/bricks/andesite_slab', type='bottom')
            else:
                for y in (84, 85):
                    block, _ = world.block(x, y, z)
                    if block not in ('minecraft:air', 'minecraft:cave_air'):
                        raise ValueError(f'Obstructed crossing at {x}, {y}, {z}: {block}')
    for x in (-1090, -1068):
        for z in range(-896, -891):
            ground, wet = world.original_surface(x, z)
            if wet or ground < 80:
                raise ValueError(f'Bridge seat above open water or a drop at {x}, {z}: {ground}')
    for z, y, facing in [(-539, 73, 'south'), (-538, 75, 'south'), (-528, 75, 'north'), (-527, 73, 'north')]:
        world.expect(-971, y, z, 'tfc:rock/bricks/basalt_stairs', half='bottom', facing=facing)
        world.expect(-971, y - 1, z, 'tfc:rock/smooth/basalt')
    for z in (-537, -529):
        world.expect(-971, 73, z, 'tfc:rock/bricks/basalt_wall', up='true')
    for z in range(-536, -529):
        world.expect(-971, 70, z, 'tfc:rock/bricks/basalt')
        world.expect(-971, 71, z, 'minecraft:air')


def check_harbour(world):
    # Walk each stair and its quarter-turn landing, from the keeper's room up to the gallery.
    flights = [((-774, -160), (-774, -161)), ((-775, -161), (-776, -161)),
               ((-776, -160), (-776, -159)), ((-775, -159), (-774, -159))]
    for y in range(65, 85):
        stair, landing = flights[(y - 65) % 4]
        for (x, z), block in [(stair, 'pine_stairs'), (landing, 'pine')]:
            world.expect(x, y, z, 'tfc:wood/planks/' + block)
            for dy in (1, 2):
                world.expect(x, y + dy, z, 'minecraft:air')
    world.expect(-775, 88, -160, 'tfc:firepit', lit='true')
    # The quay and the smaller landing each have exposed pile heads flush with the planks.
    for x, z in [(-781, -158), (-770, -153), (-895, -120), (-897, -124)]:
        world.expect(x, 65, z, 'minecraft:air')
        y = 64
        while y > 45 and world.block(x, y, z)[0] == 'tfc:wood/stripped_log/spruce':
            world.expect(x, y, z, 'tfc:wood/stripped_log/spruce', axis='y')
            y -= 1
        if y >= 63 or not world.block(x, y, z)[0].startswith('tfc:rock/'):
            raise ValueError(f'Incomplete pile at {x}, {y}, {z}')


def check_taiga(world):
    world.expect(-891, 140, 275, 'minecraft:stone')
    world.expect(-891, 150, 275, 'minecraft:dirt')
    world.expect(-891, 153, 275, 'minecraft:grass_block')
    world.expect(-875, 85, 264, 'minecraft:water', level='0')
    # The wheel tracks and the uphill village lane flooded after the spring started flowing.
    for x, y, z in [(-883, 70, 262), (-884, 70, 263), (-880, 73, 263), (-876, 77, 263)]:
        for dx in (-1, 0, 1) if y == 70 else (0,):
            for dy in (0, 1):
                block, _ = world.block(x + dx, y + dy, z)
                if block == 'minecraft:water':
                    raise ValueError(f'Flooded road at {x + dx}, {y + dy}, {z}')
    for x, y, z in [(-894, 76, 277), (-893, 76, 275), (-892, 77, 283)]:
        block, _ = world.block(x, y, z)
        if block == 'minecraft:stripped_spruce_log':
            raise ValueError(f'Suspended tunnel timber at {x}, {y}, {z}')


def check_cave_roofs(tfc, taiga):
    # These blocks bear the road directly over a cavity in the original terrain.
    for x, y, z in [(355, 67, 736), (360, 65, 755)]:
        tfc.expect(x, y, z, 'tfc:rock/hardened/limestone')
        tfc.expect(x, y - 1, z, 'minecraft:cave_air')
        tfc.expect(x, y + 1, z, 'minecraft:air')
    taiga.expect(-384, 65, 156, 'minecraft:grass_block')
    taiga.expect(-384, 64, 156, 'minecraft:air')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('tfc_world', type=Path)
    parser.add_argument('vanilla_world', type=Path)
    parser.add_argument('--tfc-floodplain', type=Path, help='Also check the cave roofs on TFC seed 38473917 and in the taiga')
    parser.add_argument('--harbour', action='store_true', help='Also generate the TFC coast near (-775, -160) and (-895, -120)')
    args = parser.parse_args()
    gorge = World(args.tfc_world, 7319041)
    check_gorge(gorge)
    if args.harbour:
        check_harbour(gorge)
    taiga = World(args.vanilla_world, 96320157)
    check_taiga(taiga)
    if args.tfc_floodplain:
        check_cave_roofs(World(args.tfc_floodplain, 38473917), taiga)
    print('Gorge deck, stone seats, spring, road clearance and overhanging rock verified.')
