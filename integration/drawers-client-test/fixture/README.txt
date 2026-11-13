Real-client drawer transfer benchmark

Only ProjectRed's StorageDrawersInvWrapper.injectItem/extractItem differ between
baseline and candidate. Storage Drawers stays at 2.2.31-GTNH. This is an actual
Minecraft/Forge client + integrated world with actual drawer blocks and pipes,
not the earlier counted proxy API fixture. It is not the full GTNH/Angelica client.

A warehouse has 50 four-slot drawer blocks (200 logical storage cells) per
controller, source and destination both contain matching coal. Their configured
range is 4 and block limit 50, matching the copied GTNH StorageDrawers.cfg. All
blocks are created through the mod's native ItemBlock placement method. Source
and target are connected through two real routed interfaces and a basic pipe.
The real extractor/responder chips are built into item stacks and installed in
chipSlots, letting native update/refresh code load and operate them.

Run client using the init script:
  VERSION=4.12.50-GTNH JAVA_HOME=/path/to/jdk25 ./gradlew --no-configuration-cache \
    -I /absolute/path/client.init.gradle -PdrawerTestRoot=/absolute/path/here \
    -PdrawerVariant=baseline -PdrawerTicks=400 -PdrawerOutput=/absolute/path/output runClient

The Gradle build uses Java 25; the game's development runtime uses Java 8u504.
Set DISPLAY/XAUTHORITY for a working desktop session. Copy the same test-only
StorageDrawers.cfg to each project's run/client/config before launching. The
init script adds the unchanged SD dev artifact and dependencies only to the test
runtime; they do not become new mandatory production dependencies.

The source creates an independently named world for each run; no production
server is contacted. The camera/settings stay fixed, lost-focus pausing is off,
and the runner rejects runs with insufficient focused frames. Cases measure
400 server ticks (20 seconds) of idle, one-item transfers at 20 Hz, and the normal
extractor-chip schedule (64 items per operation, every 10 ticks). Warm-up and
settling periods are outside the measured windows. Native controller scheduled
refresh is started once then left running. Matching-item storage is an adverse
layout for repeated writes, so the result must not be generalized to all mixed
warehouses. This change does not optimize controller scanning or capacity queries.

Observer pipeline handlers pass packets through unchanged. The outbound handler
counts CountUpdateMessage. FIFO phase attribution allows the inbound FML payload
observer to match every such message to its sending window, including delayed
arrivals after a phase ends. These tests have one local player in range. Each
CountUpdateMessage payload is 16 bytes including its discriminator. Framing,
compression, other Minecraft messages and IP/TCP overhead are not counted.
Timers bracket the server tick and include the manual pulse workload. CPU time
uses the server thread's actual ThreadMXBean CPU time; wall time is separate.
The same counters are used in both variants; their overhead is not subtracted.

Outputs include per-run JSON, raw client frame intervals, progress and actual
screenshots. All final controller inventories are checked against the real
client drawer blocks; total items must remain 51400. Regression checks cover
NBT/metadata mismatch, partial multi-drawer transfers, merge-before-empty,
locked prototypes, void overflow and vending. Compacting drawers and ore item
conversion are disabled in the copied GTNH config and explicitly not claimed.

run-pairs.py runs three pairs serially, reversing the second pair's order.
Set PR_DRAWER_BASELINE to a checkout reconstructed with routing-followups.patch
and PR_DRAWER_CANDIDATE to one reconstructed with drawer-adapter.patch; see
../../source-snapshots/README.md. PR_DRAWER_OUTPUT selects a fresh output directory.
JAVA_HOME, DISPLAY and XAUTHORITY are inherited. Set PR_DRAWER_SD_JAR to the
unchanged StorageDrawers-2.2.31-GTNH-dev.jar before running analyze.py. The archived
status retains historical paths for provenance; analysis reads pair-* directories
relative to PR_DRAWER_OUTPUT, so the archived results are portable. analyze.py computes
medians of per-run means and retains all pair-level data, rather than pooling
all samples into a single fake independent population.

The reduced review archive keeps all six per-run results.json files and their
comparison, plus one native-extractor screenshot per variant. Raw frame CSVs,
repeated screenshots, the duplicate text report and unit-test summary are kept
in the local complete archive. A fresh run still emits all original outputs.
