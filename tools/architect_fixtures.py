#!/usr/bin/env python3
"""Canonical lab geometry. Both interactive runs and GameTests load these exact NBTs.
Run --write after editing a fixture; --check rejects stale generated resources.
"""
import argparse
import gzip
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NAMES = ('clear_corridor', 'low_ceiling', 'unreachable_target', 'sealed_pocket', 'wall',
         'dogleg', 'u_detour', 'cheap_detour', 'stairs_up', 'stairs_down', 'narrow_bridge',
         'offset_doorways', 'slab_steps', 'vanishing_wall', 'target_juke', 'seeded_maze',
         'closing_passage', 'lava_detour', 'corridor_soak', 'corridor_shuttle',
         'pit_shallow', 'pit_direct_steps', 'pit_side_steps', 'pit_corner_steps', 'pit_narrow_steps', 'pit_slab_ramp', 'pit_tunnel', 'pit_target_offset',
         'fence_gap', 'fence_detour', 'fence_corner', 'gate_open', 'gate_closed', 'gate_reopens', 'slab_checkerboard', 'slab_top_tunnel', 'slab_low_roof', 'slab_stair_mix', 'slab_fence_lane', 'slab_trapdoor', 'footing_ice', 'footing_honey', 'footing_soul_sand', 'footing_slab_bridge', 'multi_choice', 'multi_crossing', 'multi_target_removed', 'multi_near_enclosed', 'field_fences', 'field_slabs', 'field_mixed')
SIZE = (21, 16, 21)
NAMES += ('scaffold_ascent', 'scaffold_gap', 'scaffold_interruption', 'scaffold_damage', 'dig_down_required', 'dig_down_open', 'dig_up_required', 'dig_up_open', 'mixed_escape', 'route_opens_mining', 'route_closes_travel', 'target_turnover', 'long_pursuit')


def layout(name):
    blocks = {(x, y, z): 'air' for x in range(21) for y in range(16) for z in range(21)}

    def fill(a, b, block):
        for x in range(a[0], b[0]+1):
            for y in range(a[1], b[1]+1):
                for z in range(a[2], b[2]+1):
                    blocks[x, y, z] = block

    fill((0, 0, 0), (20, 0, 20), 'bedrock')
    fill((1, 0, 1), (19, 0, 19), 'stone')
    fill((0, 15, 0), (20, 15, 20), 'bedrock')
    for x in (0, 20): fill((x, 1, 0), (x, 14, 20), 'bedrock')
    for z in (0, 20): fill((0, 1, z), (20, 14, z), 'bedrock')
    for x in (4, 10, 16):
        for z in (4, 10, 16): blocks[x, 6, z] = 'light'

    if name in ('scaffold_ascent', 'scaffold_interruption'):
        fill((13, 1, 8), (18, 7, 13), 'bedrock')
    elif name in ('scaffold_gap', 'scaffold_damage'):
        fill((1, 1, 8), (7, 5, 12), 'bedrock')
        fill((12, 1, 8), (19, 5, 12), 'bedrock')
        for z in (8, 12): fill((1, 1, z), (19, 11, z), 'bedrock')
    elif name in ('dig_down_required', 'dig_down_open', 'dig_up_required', 'dig_up_open'):
        fill((4, 1, 8), (8, 6, 12), 'bedrock')
        fill((5, 1, 9), (7, 6, 11), 'stone')
        fill((5, 1, 9), (7, 2, 11), 'air')
        if name.endswith('_open'):
            fill((4, 1, 10), (4, 2, 10), 'air')
            fill((3, 6, 4), (6, 6, 7), 'bedrock')
            fill((6, 6, 7), (6, 6, 10), 'bedrock')
            for z in range(4, 10): fill((2, 1, z), (3, 10-z, z), 'bedrock')
    elif name == 'mixed_escape':
        for z in (8, 12): fill((1, 1, z), (19, 12, z), 'bedrock')
        fill((1, 1, 9), (7, 5, 11), 'bedrock')
        fill((12, 1, 9), (19, 5, 11), 'bedrock')
        fill((2, 6, 9), (2, 8, 11), 'bedrock')
        fill((3, 8, 9), (5, 8, 11), 'bedrock')
        fill((5, 6, 9), (5, 7, 11), 'stone')
        fill((15, 6, 9), (19, 8, 11), 'bedrock')
    elif name == 'route_opens_mining':
        for z in (9, 11): fill((1, 1, z), (19, 4, z), 'bedrock')
        fill((1, 3, 10), (19, 3, 10), 'bedrock')
        fill((9, 1, 10), (9, 2, 10), 'stone')
    elif name == 'route_closes_travel':
        fill((10, 1, 1), (10, 4, 7), 'bedrock')
        fill((10, 1, 11), (10, 4, 19), 'bedrock')
        # Direct doorway z10; a second opening z8..9 provides a short detour.
    elif name in ('target_turnover', 'long_pursuit'):
        fill((1, 1, 10), (19, 1, 19), 'stone')
        fill((1, 2, 12), (19, 2, 19), 'stone')
    elif name in ('clear_corridor', 'vanishing_wall', 'closing_passage', 'corridor_soak', 'corridor_shuttle'):
        for x in (3, 5): fill((x, 1, 2), (x, 3, 15), 'stone')
        for z in (2, 15): fill((4, 1, z), (4, 3, z), 'stone')
        fill((4, 3, 2), (4, 3, 15), 'stone')
        if name == 'vanishing_wall': fill((4, 1, 7), (4, 2, 7), 'stone')
    elif name == 'low_ceiling':
        for x in (3, 5): fill((x, 1, 2), (x, 4, 13), 'bedrock')
        fill((4, 1, 2), (4, 4, 2), 'bedrock')
        fill((4, 1, 5), (4, 1, 13), 'deepslate')  # Mineable, but costlier than the stone ceiling.
        blocks[4, 3, 3] = blocks[4, 3, 4] = 'stone'
        blocks[4, 4, 5] = 'bedrock'
    elif name in ('unreachable_target', 'sealed_pocket'):
        for x in ((4, 12) if name == 'unreachable_target' else (4,)):
            fill((x-1, 1, 3), (x+1, 3, 5), 'bedrock')
            fill((x, 1, 4), (x, 2, 4), 'air')
            if name == 'sealed_pocket' or x == 12:
                fill((x-1, 0, 3), (x+1, 0, 5), 'bedrock')
        if name == 'unreachable_target':
            blocks[5, 1, 4] = blocks[5, 2, 4] = 'stone'
    elif name == 'wall':
        fill((9, 1, 1), (11, 4, 19), 'stone')
    elif name in ('dogleg', 'u_detour'):
        fill((2, 1, 2), (17, 3, 17), 'bedrock')
        if name == 'dogleg':
            fill((4, 1, 4), (4, 2, 12), 'air')
            fill((4, 1, 12), (12, 2, 12), 'air')
        else:
            fill((5, 1, 5), (5, 2, 13), 'air')
            fill((5, 1, 13), (9, 2, 13), 'air')
            fill((9, 1, 5), (9, 2, 13), 'air')
    elif name == 'cheap_detour':
        fill((10, 1, 6), (10, 4, 8), 'deepslate')
    elif name in ('stairs_up', 'stairs_down', 'slab_steps'):
        for x in (3, 5): fill((x, 1, 2), (x, 7, 15), 'bedrock')
        for z in (2, 15): fill((4, 1, z), (4, 7, z), 'bedrock')
        for z in range(3, 15):
            if name == 'stairs_up': height = min(3, max(0, z - 5))
            elif name == 'stairs_down': height = min(3, max(0, 9 - z))
            else: height = min(2, max(0, (z - 5) // 2))
            if height: fill((4, 1, z), (4, height, z), 'stone')
        if name == 'slab_steps':
            blocks[4, 1, 6] = 'stone_slab[type=bottom,waterlogged=false]'
            blocks[4, 2, 8] = 'stone_slab[type=bottom,waterlogged=false]'
    elif name == 'narrow_bridge':
        fill((4, 1, 10), (16, 3, 10), 'stone')
    elif name == 'offset_doorways':
        fill((2, 1, 2), (10, 3, 16), 'bedrock')
        fill((3, 1, 3), (9, 2, 15), 'air')
        for z, x in ((7, 4), (11, 8)):
            fill((3, 1, z), (9, 2, z), 'bedrock')
            fill((x, 1, z), (x, 2, z), 'air')
    elif name == 'seeded_maze':
        # Corridors are carved by the shared runner using the recorded run seed.
        fill((2, 1, 2), (18, 3, 18), 'bedrock')
    elif name == 'target_juke':
        pass
    elif name.startswith('pit_'):
        depth = 1 if name == 'pit_shallow' else 2 if name == 'pit_slab_ramp' else 4 if name == 'pit_side_steps' else 3
        fill((1, 1, 1), (19, depth, 19), 'stone')
        zlo, zhi = (10, 10) if name == 'pit_narrow_steps' else (8, 12)
        if name == 'pit_corner_steps': zlo, zhi = 10, 14
        fill((10, 1, zlo), (14, depth, zhi), 'air')
        if name in ('pit_direct_steps', 'pit_narrow_steps', 'pit_target_offset'):
            for x, height in ((7, 2), (8, 1), (9, 0)):
                fill((x, height + 1, max(9, zlo)), (x, depth, min(11, zhi)), 'air')
        elif name in ('pit_side_steps', 'pit_corner_steps'):
            first = 4 if name == 'pit_side_steps' else 7
            for offset in range(depth):
                fill((11, depth - offset, first + offset), (13, depth, first + offset), 'air')
        elif name == 'pit_slab_ramp':
            for x, height in ((7, 1), (8, 1), (9, 0)):
                fill((x, height + 1, 9), (x, depth, 11), 'air')
                if x != 8:
                    fill((x, height + 1, 9), (x, height + 1, 11), 'stone_slab[type=bottom,waterlogged=false]')
        elif name == 'pit_tunnel':
            fill((4, 1, 10), (9, 2, 10), 'air')
    elif name in ('fence_gap', 'fence_detour', 'gate_open', 'gate_closed', 'gate_reopens'):
        lo, hi = (7, 13) if name == 'fence_detour' else (1, 19)
        for z in range(lo, hi + 1):
            if name == 'fence_gap' and z == 10: continue
            blocks[10, 1, z] = 'oak_fence[north=true,south=true,east=false,west=false,waterlogged=false]'
        if name.startswith('gate_'):
            opened = 'false' if name == 'gate_closed' else 'true'
            blocks[10, 1, 10] = f'oak_fence_gate[facing=east,in_wall=false,open={opened},powered=false]'
    elif name == 'fence_corner':
        for x in (8, 12): fill((x, 1, 7), (x, 1, 13), 'oak_fence[north=true,south=true,east=false,west=false,waterlogged=false]')
        fill((8, 1, 7), (12, 1, 7), 'oak_fence[north=false,south=false,east=true,west=true,waterlogged=false]')
    elif name == 'slab_checkerboard':
        for x in range(7, 14):
            for z in range(7, 14):
                if (x + z) % 2 == 0: blocks[x, 1, z] = 'stone_slab[type=bottom,waterlogged=false]'
    elif name in ('slab_top_tunnel', 'slab_low_roof', 'slab_stair_mix', 'slab_fence_lane', 'slab_trapdoor',
                  'footing_ice', 'footing_honey', 'footing_soul_sand'):
        for x in (3, 5):
            if name == 'slab_fence_lane':
                fill((x, 1, 2), (x, 1, 15), 'oak_fence[north=true,south=true,east=false,west=false,waterlogged=false]')
            else: fill((x, 1, 2), (x, 6, 15), 'bedrock')
        for z in (2, 15): fill((4, 1, z), (4, 6, z), 'bedrock')
        if name == 'slab_top_tunnel':
            fill((4, 0, 3), (4, 0, 14), 'stone_slab[type=top,waterlogged=false]')
            fill((4, 3, 3), (4, 3, 14), 'stone_slab[type=bottom,waterlogged=false]')
        elif name == 'slab_low_roof':
            fill((4, 1, 3), (4, 1, 14), 'stone_slab[type=bottom,waterlogged=false]')
            fill((4, 3, 3), (4, 3, 14), 'stone_slab[type=top,waterlogged=false]')
        elif name == 'slab_fence_lane':
            fill((4, 1, 3), (4, 1, 14), 'stone_slab[type=bottom,waterlogged=false]')
        elif name == 'slab_trapdoor':
            for z in (5, 7, 9, 11): blocks[4, 1, z] = 'oak_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]'
        elif name == 'slab_stair_mix':
            blocks[4, 1, 5] = 'stone_slab[type=bottom,waterlogged=false]'
            blocks[4, 1, 6] = 'stone_brick_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]'
            blocks[4, 1, 7] = 'stone'
            blocks[4, 2, 7] = 'stone_slab[type=bottom,waterlogged=false]'
            fill((4, 1, 8), (4, 2, 8), 'stone')
            blocks[4, 1, 9] = 'stone'
            blocks[4, 2, 9] = 'stone_slab[type=bottom,waterlogged=false]'
            blocks[4, 1, 10] = 'stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]'
            blocks[4, 1, 11] = 'stone_slab[type=bottom,waterlogged=false]'
        else:
            material = {'footing_ice': 'blue_ice', 'footing_honey': 'honey_block', 'footing_soul_sand': 'soul_sand'}[name]
            fill((4, 0, 3), (4, 0, 14), material)
    elif name == 'footing_slab_bridge':
        fill((4, 1, 10), (16, 2, 10), 'stone')
        fill((4, 3, 10), (16, 3, 10), 'stone_slab[type=bottom,waterlogged=false]')
    elif name == 'multi_near_enclosed':
        fill((7, 1, 9), (9, 3, 11), 'bedrock')
        fill((8, 1, 10), (8, 2, 10), 'air')
    elif name.startswith('multi_') or name.startswith('field_'):
        pass  # Additional actors / seeded obstacles are placed by the shared runner.
    elif name == 'lava_detour':
        fill((9, 0, 9), (11, 0, 11), 'lava[level=0]')
    else: raise ValueError(name)
    return blocks



def validate_pit_routes():
    """Independent geometry check: a 0.6 x 1.95 body has a cardinal route,
    using at most one-block height changes, with no excavation or long falls.
    Half-slabs use their collision height; light blocks have no collision.
    """
    from collections import deque
    import re
    source = (ROOT / 'src/main/java/com/frozendawn/debug/architect/ArchitectLabScenario.java').read_text()
    for name, actor, target in re.findall(r'\("(pit_[a-z_]+|dig_down_open|dig_up_open)", new Vec3\(([^)]+)\), new Vec3\(([^)]+)\)', source):
        blocks = layout(name)
        def solid_height(block):
            return 0 if block in ('air', 'light') else 0.5 if block.startswith('stone_slab[') else 1
        def clear(x, feet, z):
            return all(not solid_height(blocks[x, y, z]) or y + solid_height(blocks[x, y, z]) <= feet
                       or y >= feet + 1.95 for y in range(16))
        nodes = set()
        for x in range(1, 20):
            for z in range(1, 20):
                for y in range(15):
                    height = solid_height(blocks[x, y, z])
                    if height and clear(x, y + height, z): nodes.add((x, y + height, z))
        def node(text):
            x, y, z = map(float, text.split(','))
            return int(x), y, int(z)
        start, end = node(actor), node(target)
        assert start in nodes and end in nodes, (name, 'actor or target intersects terrain')
        seen, queue = {start}, deque([start])
        while queue:
            x, y, z = queue.popleft()
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                for dy in (-1, -0.5, 0, 0.5, 1):
                    nxt = (x + dx, y + dy, z + dz)
                    if nxt not in nodes or nxt in seen: continue
                    top = max(y, y + dy)
                    if clear(x, top, z) and clear(x + dx, top, z + dz):
                        seen.add(nxt)
                        queue.append(nxt)
        assert end in seen, (name, 'no safe connected route')
    print('Architect geometry: verified 8 pit routes and 2 open digging alternatives')


def string(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b


def tag(kind, value):
    if kind == 3: return struct.pack('>i', value)
    if kind == 8: return string(value)
    if kind == 9:
        subtype, values = value
        return bytes([subtype]) + struct.pack('>i', len(values)) + b''.join(tag(subtype, v) for v in values)
    if kind == 10:
        return b''.join(bytes([k]) + string(n) + tag(k, v) for n, (k, v) in value.items()) + b'\0'
    raise ValueError(kind)


def template(name):
    blocks = layout(name)
    palette = sorted(set(blocks.values()))
    states = []
    for block in palette:
        base, _, properties = block.partition('[')
        state = {'Name': (8, 'minecraft:' + base)}
        if properties:
            state['Properties'] = (10, {k: (8, v) for k, v in
                (pair.split('=', 1) for pair in properties.rstrip(']').split(','))})
        if block == 'light': state['Properties'] = (10, {'level': (8, '15'), 'waterlogged': (8, 'false')})
        states.append(state)
    root = {'DataVersion': (3, 3955), 'size': (9, (3, SIZE)), 'palette': (9, (10, states)),
            'blocks': (9, (10, [{'pos': (9, (3, pos)), 'state': (3, palette.index(block))}
                               for pos, block in sorted(blocks.items())])), 'entities': (9, (10, []))}
    return gzip.compress(bytes([10]) + string('') + tag(10, root), mtime=0)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write', action='store_true')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    validate_pit_routes()
    stale = []
    for name in NAMES:
        path = ROOT / 'src/main/resources/data/frozendawn/structure/lab' / (name + '.nbt')
        expected = template(name)
        if args.write:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(expected)
        elif not path.exists() or gzip.decompress(path.read_bytes()) != gzip.decompress(expected): stale.append(name)
    if stale: raise SystemExit('Stale Architect fixtures: ' + ', '.join(stale) + '. Run tools/architect_fixtures.py --write')
    print('Architect fixtures: ' + ('wrote' if args.write else 'verified') + ' ' + str(len(NAMES)))


if __name__ == '__main__': main()
