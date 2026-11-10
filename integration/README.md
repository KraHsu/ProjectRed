# GTNH 2.9 routing regression checks

Run against an **unmodified GTNH 2.9.0 Java 17+ server pack** and the reobfuscated fork build:

```sh
python3 integration/run-routing.py \
  --pack /path/to/unmodified-server-pack \
  --candidate build/libs/ProjRed-4.12.50-GTNH.jar \
  --java-home /path/to/jdk-25
```

The supplied pack must already have an accepted EULA. The runner copies its configuration, libraries and mods into a new temporary directory. It uses a separate flat world, binds only to `127.0.0.1` with an ephemeral port, and never connects to or changes a live server. Allow roughly 2 GiB of disk space and 8 GiB of heap. Each variant boots once, performs all scenarios in the same process, writes JSON, and stops.

Checks:

- Adjacent interfaces relay to a target through the expected next hop.
- An interface selects a branch leading to a junction.
- `chest — interface — interface — chest` accepts deliveries in both directions.
- A full target rejects an item. The original repeatedly selects that inventory; the candidate releases its remaining reservation and selects another accepting target.
- When two out of five items fit, three items and three reservations remain. The original retains five reservations.
- Native Forge version checks accept an unmodified `4.12.50-GTNH` peer for all nine ProjectRed modules. This exercises version negotiation, not a graphical client's end-to-end connection.

These are actual world, inventory, multipart, routing-table and payload classes in the complete dedicated pack. They do not measure Windows rendering performance or prove every possible machine/chip topology. Render geometry comparisons and CPU-path profiling are a separate validation.

## Wrench grid and support connections (krahsu.3)

New item placements automatically connect only to the supporting pipe or
accessible inventory that was clicked. Other adjacent faces stay disabled.
Incompatible pipes, solid blocks and blocked faces do not auto-connect.
Placing into the clicked multipart cell does not invent an adjacent support.
Failed placements do not change existing connections or consume an item.

Right-click with an ore-dictionary `craftingToolWrench` (including GT's ordinary
wrench) to toggle a face. The visible grid and hit mapping use GT's 1/4 and 3/4
boundaries: center = the face being viewed, four edges = the adjacent faces,
four corners = the opposite face. Green crosses mark actual connections and
the orange border marks the hovered cell. Holding a wrench expands selection
to the full block, matching GT pipe operation, while physical collision and
ordinary item selection remain unchanged. The dedicated server retraces the
player's wrench ray through FMP; no new packet or interaction bypass is used.
Pipe pairs change together. Inventory connections change only the pipe face.
Screwdriver orientation and valve controls retain their behavior.

`manualConnections` is a six-bit NBT field. Missing data defaults to all faces
enabled, preserving legacy networks. The existing connection description and
update packets are unchanged. Covers, inventory accessibility and pipe-type
compatibility still use native discovery. Routing junctions refresh immediately;
unrouted bridge changes propagate through the normal routing detection cycle.
The setting adds no per-tick scans or packet types. The grid's geometry arrays
are cached once. Dedicated-server full-block selection checks the dimension's
player list only when a raytrace occurs and only matches a wrench holder's eye
ray. It does not expand projectile/other rays or physical collision boxes.

The visible grid/full-block selection require the updated client as well as
the updated server. Support-only placement is server controlled. Original peer
version negotiation remains accepted, but this is not a full graphical test of
every older client.

```sh
python3 integration/run-connections.py \
  --pack /path/to/unmodified-server-pack \
  --candidate build/libs/ProjRed-4.12.50-GTNH.jar \
  --java-home /path/to/jdk-25
```

This starts one disposable full GTNH process for all warm checks, then stops,
saves, and restarts the same test world for persistence checks. Coverage includes
seven pipe types on all six faces, inventories on all six sides, reciprocal
toggles, legacy/new mixtures, solid covers, neighbour updates, serialization,
negative coordinates at a chunk boundary, the real ItemInWorldManager → GT tool
→ multipart activation path, route cuts/reconnection and in-flight item counts.
It also reruns the earlier adjacent-interface, full-inventory and partial-delivery
regressions and original-client version negotiation for all nine modules. Grid
tests compare against the real GTUtility helper for 34,992 coordinates, exercise
all 54 cells, perform 42 real pipe-support and 18 inventory-support placements,
and verify saved support pairs and deliberately disabled links after a restart.

The optional offscreen client render test needs Xvfb and the LWJGL native
libraries extracted by the normal build. It renders all six grids and verifies
shader, colour, blend, depth, line and matrix state restoration:

```sh
./gradlew --no-configuration-cache -I integration/grid-gl.init.gradle writeWrenchTestClasspath
python3 integration/run-grid-gl.py --classpath-file build/wrench-test-classpath.txt
```

`results/wrench-grid-preview.png` is captured from the actual client renderer in
that GL context. This does not replace a complete Windows/GTNH/Angelica test.

Reports do not replace a graphical client test or prove every third-party
inventory, claim system or possible machine/chip topology. Remove the previous
ProjectRed JAR before installing the replacement; retain a stopped-world backup.
