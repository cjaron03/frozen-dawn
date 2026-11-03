# Power Monitor Check

Source: [Notion Power System](https://app.notion.com/p/3f57cfaa89018123a786fbb637b5ecb1), including additions, refetched October 14, 2026. ORSA Power Monitor is a passive wall terminal linked with the Binder. Range16, existing sealed-wall/feedthrough and dock isolation rules apply. Place on a sturdy horizontal wall face. Losing support drops the monitor and clears links.

Open empty-handed: one read-only screen shows Normal/Battery backup/Brownout/No power/Not linked, supply and demand in kW, battery charge in kJ, bar and devices waiting for power, shown by plain names. Six rows per page; previous/next appear only for long lists. Coordinates are omitted from the screen and tooltips. No slots, settings, local battery, hidden demand, oxygen or heat. Source reads the last 20-tick allocation, never accounts energy again. Supply includes committed battery service as in Binder; Battery backup identifies a generation-free served load. Reported shed devices are actual unserved active loads; empty or blocked furnaces request zero and are omitted. Priority life-support devices arrive in their later slices.

Battery capacity is the linked physical bank/dock slot capacity, matching existing storage UI/comparator: a bank 2000 kJ, dock 500 kJ; cycles count each once. Comparator0 with no charge; positive charge1+floor(14*energy/capacity), up to15. Display values are transient, rebuilt from saved links/cells after restart. The wall graphic communicates state; the menu contains numeric readings. Readings use one-second allocation samples. After a power change, allow allocation plus one complete battery draw/sample interval (up to about two seconds) for the charge/comparator to reflect that work. Pager is range/identity/spectator validated; unsigned 16-bit menu words preserve 32-bit coordinates and 64-bit aggregate energy.

Fresh Creative world: Default difficulty chosen/locked, world-local advancement filter after mod_data, progression paused in owning run-lab. No gameplay data from accepted power/furnace saves copied. Setup runs once under #built pmonitor. Owner executes /function power_monitor_check:controls for directions; never reset/recreate the save.

Bind Core0,65,0 -> relay4,65,0 -> bank8,65,0 -> furnaces12,65,0 and12,65,4 plus lamp12,65,8. Link relay -> monitor4,65,6 mounted on stone4,65,5. Comparator at4,65,7 reads the monitor to its north and outputs south, with a15-block redstone output line. Stock both furnaces, leave bank empty, set25%: supply5.00kW, demand4.10kW, battery0/2000kJ, no shed devices. Input exhaustion returns furnace demand to0; refill before testing thresholds.

| Core | Expected with both furnaces stocked and no cells |
| --- | --- |
|9%|Both furnaces shed, lamp may work|
|10%|One furnace served; other furnace and lamp listed as shed|
|20%|Both furnaces served; lamp listed as shed|
|25%|All served, empty shed list|
|0%|No power, all three active devices listed|

Insert supplied full 500 kJ cells with25% Core: one gives about 25%/comparator4; two about 50%/8; four full/15. At0% the battery powers loads, display says Battery backup, charge falls; removing all cells causes No power. Shift-clear monitor links gives Not linked/0; rebind restores readings. Save and reopen with links/cells intact to check reconstruction. Break a spare monitor's supporting wall to check one-item drop/cleared links. Larger paging is covered by native tests; no general lab function allowlist expansion.

Five required native tests cover component/shed metrics and passive conservation, menu signed-short/long-value synchronization and paging/security, supported wall/drop/saved links/dock isolation/no forced chunks, real-tick battery states and actual vanilla comparator signals, and seven parsed helper functions. Owner visual/readability and same-save acceptance are distinct from those gates. Shared smoke jar remains reserved for unrelated work; use owning Java21 Gradle runClientLab.
