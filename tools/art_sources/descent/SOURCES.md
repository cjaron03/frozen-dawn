# The descent's art

- **Motif paintings** (`textures/painting/descent/*`, except the portrait): the owner's wall art,
  `FrozenDawn_Descent_Walls.zip`, delivered 2026-11-23, copied at native resolution (16 px per block).
  The famine wall is 72 px wide, which is not a whole number of blocks; its stone edge column is repeated
  4 px each side to 80 px, so it maps 1:1 onto a 5x3 painting.
- **Portrait, instruments and marks**: drawn by `tools/generate_descent_assets.py` from fixed seeds and
  approved by the owner on 2026-11-23 as the art for now. Rerun with `--resources src/main/resources`;
  `--mockups <dir>` draws the review sheets.
