# First Boot Epigraph

Source: Notion *First Boot Epigraph* (decided 2026-10-14). Implemented 2026-10-17 in `client/FirstBootEpigraph.java`.

Before the first ORSA intro on a client, the screen goes black and all sound stops except soft wind (`ambient.wind_light`, volume 0.3). The two lines, then the attribution, type out one character at a time, with a blinking cursor and long pauses between them. A three-second hold follows, then a hard cut into the ORSA intro with no extra black lead-in. Text lives in lang keys `epigraph.frozendawn.line1`, `line2` and `attribution`. The typing speed (three ticks per character, two for the attribution) was the owner's request for an ominous effect.

It plays once per client. On completion it writes the marker file `config/frozendawn-first-boot-epigraph.seen`, and it never repeats in any world. If the player disconnects first, nothing is recorded and it plays again next time.

Development clients skip the ORSA intro (`frozendawn.dev.skipOrsaAwakening`). The dev-only client command `/fdpreview epigraph` plays the epigraph and then the intro without recording either.
