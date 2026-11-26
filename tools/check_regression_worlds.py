"""Check the two real Four Winds 1.8.1 regression sites in saved Minecraft 1.21.1 worlds.

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
    def __init__(self, path, seed):
        self.path = path
        actual = int(nbtlib.load(path / 'level.dat')['Data']['WorldGenSettings']['seed'])
        if actual != seed:
            raise ValueError(f'{path}: expected seed {seed}, found {actual}')

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
        if str(data.get('Status')) != 'minecraft:full':
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

    def expect(self, x, y, z, name, **properties):
        block, state = self.block(x, y, z)
        if block != name or any(str(state.get(k)) != v for k, v in properties.items()):
            raise ValueError(f'{x}, {y}, {z}: expected {name} {properties}, found {block} {state}')


def check_gorge(world):
    for x in range(-1090, -1067):
        for z in range(-896, -891):
            world.expect(x, 83, z, 'tfc:rock/bricks/andesite')
            if abs(z + 894) == 2:
                world.expect(x, 84, z, 'tfc:rock/bricks/andesite_slab', type='bottom')
            else:
                for y in (84, 85):
                    block, _ = world.block(x, y, z)
                    if block not in ('minecraft:air', 'minecraft:cave_air'):
                        raise ValueError(f'Obstructed crossing at {x}, {y}, {z}: {block}')
    for x in (-1090, -1068):
        for z in range(-896, -891):
            tags = world.chunk(x // 16, z // 16)['neoforge:attachments']
            heights = tags['fourwindsroadworks:surveyed_surface']['heights']
            column = (z % 16) * 16 + x % 16
            value = (int(heights[column // 2]) >> (16 * (column % 2))) & 65535
            ground = (value if value < 32768 else value - 65536) >> 1
            if value & 1 or ground < 80:
                raise ValueError(f'Bridge seat above open water or a drop at {x}, {z}: {ground}')
    for z in (-537, -529):
        world.expect(-971, 73, z, 'tfc:rock/bricks/basalt_wall', up='true')
    for z in range(-536, -529):
        world.expect(-971, 70, z, 'tfc:rock/bricks/basalt')
        world.expect(-971, 71, z, 'minecraft:air')


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


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('tfc_world', type=Path)
    parser.add_argument('vanilla_world', type=Path)
    args = parser.parse_args()
    check_gorge(World(args.tfc_world, 7319041))
    check_taiga(World(args.vanilla_world, 96320157))
    print('Gorge deck, stone seats, spring, road clearance and overhanging rock verified.')
