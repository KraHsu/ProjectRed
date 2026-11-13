Contribution validation

Recorded runs use the historical source states described in
../source-snapshots/README.md. The current source contains the complete combined
contribution; historical patches allow exact intermediate-state reproduction.
Manual connections and GT wrench controls are outside this contribution.

Build and unit tests (the project selects a Java 8 compilation toolchain):
  VERSION=4.12.50-GTNH JAVA_HOME=/path/to/jdk25 ./gradlew --no-configuration-cache test build

Real Minecraft 1.7.10 client + local integrated server on this computer:
  VERSION=4.12.50-GTNH JAVA_HOME=/path/to/jdk25 ./gradlew --no-configuration-cache \
    -I integration/performance/client.init.gradle \
    -PprTestRoot="$PWD/integration/performance" -PprTestVariant=followups runClient

Use -PprTestVariant=cache with the model-reuse-only branch. The init script and
test source can be referenced by absolute paths from that branch. Java 8 runs
this development client; the desktop needs a valid DISPLAY and XAUTHORITY.
The fixture owns run/client/saves/PerformanceContributionTest. Reruns rebuild
that fixture; never rename it to a play world. Reports/screenshots are written
to build/contribution-client-test. This client has the development runtime
mod set, not all GTNH client mods or Angelica. The fixture includes 168 pipes,
all 16 wool colors, redstone material, held/inventory pipe rendering, native
transport, resource reload, and save/reopen checks. It is not an FPS benchmark.

Disposable full GTNH dedicated-server checks (each variant saves/restarts once):
  python3 integration/performance/run-native.py --pack /path/to/gtnh-server-pack \
    --candidate build/libs/ProjRed-4.12.50-GTNH.jar --java-home /path/to/jdk25 \
    --variant followups
Use --variant cache for the model-reuse-only build. Accept the Minecraft EULA
in the supplied pack first. No production instance is used. The runner copies
the pack and binds 127.0.0.1 with an ephemeral port. It checks dyed pressure and
resistance delivery, 64 actual router registrations, version-array sentinels,
real Scala filter hash behavior, and cold inventory/color persistence.
The cache variant characterizes the upstream hash defect; it is fixed only in
the followups variant. Background mod warnings in the full pack are not treated
as proof of success; each helper must explicitly report passed=true.

CPU rendering fixture, using real production renderer/CCL bytecode with test
world/icons and a captured Tessellator (no GPU/game-FPS measurement):
  python3 integration/performance/render/run.py --pack /path/to/gtnh-server-pack \
    --candidate /path/to/candidate.jar --java-home /path/to/jdk25 \
    --srg-minecraft /path/to/build/rfg/srg_merged_minecraft.jar --report result.json
It records the Java runtime and artifact hashes. One run is one JVM per variant,
with 4,000 warmups and seven rounds of 5,000 draws; these rounds are not separate
paired game runs. The 2,368 cases cover 64 masks, positions, texture fixtures,
redstone/wool overlays and inventory rendering; geometry is checked for mutation.

No helpers or client-test source sets are included in production jars. These
checks alone do not establish Windows/Angelica compatibility or a whole-game
speedup. The later ../fullpack-graphics checks cover Angelica on Linux/Mesa.
