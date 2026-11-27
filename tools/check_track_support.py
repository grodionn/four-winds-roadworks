"""Find exposed dirt paths and coarse dirt over air or water in full Minecraft 1.21.1 chunks.

An optional control world on the same seed and preset distinguishes existing village streets
and natural coarse dirt from blocks introduced by Roadworks. Requires nbtlib.
"""
import argparse
from pathlib import Path

from check_regression_worlds import World


AIR = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'}
OPEN = AIR | {'minecraft:water', 'tfc:river_water', 'tfc:salt_water'}


def tracks(chunk):
    for section in chunk['sections']:
        states = section.get('block_states')
        if states is None:
            continue
        names = [str(p['Name']) for p in states['palette']]
        worn = {i for i, name in enumerate(names)
                if name in ('minecraft:dirt_path', 'minecraft:coarse_dirt')
                or name.startswith(('tfc:grass_path/', 'tfc:coarse_dirt/'))}
        if not worn:
            continue
        bits = max(4, (len(names) - 1).bit_length())
        per_long = 64 // bits
        words = list(map(int, states['data'])) if len(names) > 1 else []
        for index in range(4096):
            word, slot = divmod(index, per_long)
            material = (words[word] >> (slot * bits)) & ((1 << bits) - 1) if words else 0
            if material in worn:
                yield index % 16, int(section['Y']) * 16 + index // 256, (index // 16) % 16, names[material]


def area_chunks(areas):
    return {(cx, cz) for x, z, end_x, end_z in areas
            for cx in range(min(x, end_x) // 16, max(x, end_x) // 16 + 1)
            for cz in range(min(z, end_z) // 16, max(z, end_z) // 16 + 1)}


def check(world, areas, control=None):
    chunks = area_chunks(areas)
    exposed = existing = 0
    findings = []
    for cx, cz in sorted(chunks):
        for dx, y, dz, material in tracks(world.chunk(cx, cz)):
            x, z = 16 * cx + dx, 16 * cz + dz
            if world.block(x, y + 1, z)[0] not in AIR:
                continue
            exposed += 1
            below = world.block(x, y - 1, z)[0]
            if below not in OPEN:
                continue
            if control and control.block(x, y, z)[0] == material and control.block(x, y - 1, z)[0] == below:
                existing += 1
            else:
                findings.append((x, y, z, material, below))
    return len(chunks), exposed, existing, findings


if __name__ == '__main__':
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument('world', type=Path)
    cli.add_argument('--control', type=Path, help='The same area generated with Roadworks removed')
    cli.add_argument('--area', nargs=4, type=int, metavar=('X', 'Z', 'END_X', 'END_Z'), required=True,
                     action='append', help='Scan the full chunks intersecting this rectangle. Repeat for multiple sites.')
    args = cli.parse_args()
    world = World(args.world)
    control = World(args.control, world.seed) if args.control else None
    if control and (control.version != world.version or control.generator != world.generator):
        cli.error('The control uses a different Minecraft version or Overworld generator')
    count, exposed, existing, findings = check(world, args.area, control)
    print(f'{count} full chunks, {exposed} exposed soil or path blocks, {len(findings)} findings')
    if control:
        print(f'{existing} unsupported blocks also occur in the control')
    for x, y, z, block, below in findings:
        print(f'{x} {y} {z}  {block} over {below}')
    raise SystemExit(1 if findings else 0)
