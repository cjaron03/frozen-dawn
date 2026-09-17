tellraw @s {"text":"Architect Lab: setup -> scenario -> run -> /tick sprint 850","color":"aqua"}
tellraw @s {"text":"/fd architect lab reset: rebuild the selected scenario, replace actors, stay frozen."}
tellraw @s {"text":"/fd architect lab scenario <name>: use Tab for all baseline and stress cases"}
tellraw @s {"text":"/fd architect lab run: start recording before AI. /tick step 1 or /tick sprint 850 advances time."}
tellraw @s {"text":"Each run pauses and exports automatically on PASS/FAIL. /fd architect lab dump exports a snapshot."}
tellraw @s {"text":"/fd architect lab target static|live and seed <number> reset the scenario with those settings."}
tellraw @s {"text":"/fd architect lab inspect or mark <label>. Reports: <world>/architect-debug/architect-latest.json"}
tellraw @s {"text":"Fixtures use shared NBTs; see docs/architect-lab.md for capture and regression instructions."}
tellraw @s {"text":"Stress cases: closing_passage, corridor_soak, corridor_shuttle, seeded_maze, stairs, bridge, lava and more. See docs/architect-monkey-testing.md."}
tellraw @s {"text":"Hole cases: pit_shallow, pit_direct_steps, pit_side_steps, pit_corner_steps, pit_narrow_steps, pit_slab_ramp, pit_tunnel, pit_target_offset. Require an actual melee hit."}
tellraw @s {"text":"Expanded: fence_gap, fence_corner, gate_closed, slab_low_roof, slab_stair_mix, footing_slab_bridge, multi_choice, multi_crossing, multi_target_removed, multi_near_enclosed."}
tellraw @s {"text":"Random fields: field_fences, field_slabs, field_mixed. seed <number> changes terrain. rotation 0|90|180|270 rebuilds orientation; tp returns to observation corner."}
tellraw @s {"text":"Long tests: scaffold_ascent, scaffold_gap, scaffold_interruption, scaffold_damage, dig_down_required, dig_down_open, dig_up_required, dig_up_open, mixed_escape, route_opens_mining, route_closes_travel, target_turnover, long_pursuit."}
tellraw @s {"text":"Durations: focused 30-90s; changing encounters 3m; turnover 5m; pursuit 10m. Use target static, run, /tick unfreeze to watch; /tick sprint 13000 to accelerate. See docs/architect-lifecycle-tests.md."}
