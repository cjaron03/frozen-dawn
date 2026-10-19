# First Boot Epigraph

Source: Notion *First Boot Epigraph* (decided 2026-10-14). Implemented 2026-10-17 in `client/FirstBootEpigraph.java`. The staging was revised by the owner the same day: slow fades instead of typing, louder wind, and a transition into the ORSA logo instead of a hard cut.

All other sound stops. Two wind layers play, each following a scripted volume curve:
- **Near wind:** `ambient.wind_light`, rising out of silence to 0.9.
- **Low howl:** `ambient.wind_strong` pitched to 0.55. It builds under each sentence and peaks as the words dissolve.

The timeline, at 20 ticks per second:

1. 3 s of black and wind.
2. Line one fades in over 3.5 s while settling two pixels upward.
3. Line two follows 7 s after line one starts.
4. The attribution, in italics, follows 7 s after that.
5. A 6 s hold.
6. The words dissolve over 2.5 s.
7. The near wind cuts out, leaving about 3 s of black with only the howl. This combines the epigraph's last beat with the intro's own two-second lead-in.
8. The ORSA logo fades in and the howl fades away underneath it.

Text lives in lang keys `epigraph.frozendawn.line1`, `line2` and `attribution`.

It plays once per client. On completion it writes the marker file `config/frozendawn-first-boot-epigraph.seen`, and it never repeats in any world. If the player disconnects first, nothing is recorded, sound stops at once, and the epigraph plays again next time.

Development clients skip the ORSA intro (`frozendawn.dev.skipOrsaAwakening`). The dev-only client command `/fdpreview epigraph` plays the epigraph and then the intro without recording either.
