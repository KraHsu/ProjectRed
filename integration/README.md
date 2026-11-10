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
