# Power Storage Check

Source: [Notion Power System](https://app.notion.com/p/3f57cfaa89018123a786fbb637b5ecb1), including its Power Cells and Outposts additions. Each unstackable cell stores 500 kJ; the bank holds four (2,000 kJ), and the dock holds one. Charge is carried by item data, not a second block-level energy pool.

Networks account every 20 ticks. Generation serves whole lights first; surplus charges installed bank cells in stable node/slot order. On shortage, stored energy pays for the next one-second interval. Banks smooth failures; docks power devices directly without Core, bank or pylon connections, including indirect connections through another device. No additional charge/discharge rate was specified; transfer is limited by actual surplus/deficit and installed cell capacity. The Core never changes its allocation to meet load. Unloaded chunks receive no offline charge or discharge.

Open bank/dock empty-handed. Shift-click cells between player and machine. The small screen shows slots, stored kJ and charging/feeding kW; a cell tooltip and charge bar travel with the item. Comparator strength reflects charge. Breaking the block releases cells with remaining charge. Newly crafted cells are empty.

This fresh lab preserves Power Network Check and Power Flow Check. It starts in Creative at night with Default difficulty selected, progression paused and advancement popups/chat awards suppressed by its world-local pack. Setup runs once under `#built pstore`. Never reset an existing lab.

| Station | Test | Expected |
| --- | --- | --- |
| Charge, z0 | Bind Core x0 → Bank x4 → Panel x8. Insert four empty cells; set Core to 25%. | Panel on; bank gains 4.9 kJ/s. Removing a cell retains its charge in the tooltip. Full cells stop accepting charge. |
| Outpost, z12 | Carry a charged cell to Dock x0; bind it directly to Panel x6. | Light on and charge decreases by 0.1 kJ/s. Empty dock/cell turns it off. Swap a charged cell to restore without rebinding. Dock or lamp to relay x10 is rejected. |
| Backup, z24 | Core x0 → Bank x4 → Panel x8. Charge a cell, then set Core to 0%. | Bank keeps the lamp on while charge falls. Pull/reinsert cell for off/on; break block to check retained dropped charge. |

The provided 2 kJ cell is a finite depletion fixture (about 20 seconds on one panel), not a special gameplay cell. The ordinary cells begin empty. Full cells last about 83 minutes per panel. Save and reopen this same world to check charge, cell slots and links persist.

Use `/function power_storage_check:controls` for station directions. Launch only the owning Java21 Gradle `runClientLab`, with `-PfdLabWorld='Power Storage Check'`. Headless tests do not establish owner visual acceptance or multiplayer acceptance.

On October 14, the owner accepted visible bank charging, Copper Rack artwork, dock depletion shutdown and charged-cell swap recovery, plus bank backup at 0% Core power and shutdown when its cell is pulled. Drop retention and same-save storage persistence remain live acceptance checks; native proof is distinct.

On October 14, the owner also accepted dropped-cell charge retention and same-save cell/charge persistence with both lights restored. The requested local storage replay is accepted; source review found no blocking issue. Multiplayer acceptance remains separate.
