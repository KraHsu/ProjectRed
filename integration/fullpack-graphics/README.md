# Complete GTNH 2.9.0 graphical verification

These checks run the official **GTNH 2.9.0 MultiMC/Prism Java 17–26 pack** with
Angelica **2.2.43**, LWJGL3ify **3.0.42**, and Java **25.0.4.1** on Linux/Mesa.
They use the assembled production pack, not the Gradle development mod set.
Each run loads 324 FML mod entries, including one isolated verification helper.

The baseline keeps the packaged ProjectRed JAR. The candidate replaces only that
JAR with source `9a619a135931b0d800e0bf01d27bd2abb3584353`, runtime JAR SHA-256
`e86e94e869cb0e1e7c17d46eb19f7e704b0ae3906565927b02cb4d6684078867`.
Both runs use the identical helper JAR. All 242 original mod JARs were checked
against the ZIP; the only production-mod difference is ProjectRed. IC2's normal
startup downloads EJML-core-0.26; its checksum is identical between runs.
ProjectRed's configuration matches the ZIP byte for byte. Storage Drawers writes
three missing defaults during normal startup (`enableQuantifyUpgrades`,
`itemRenderDistance`, `enableBackhand`); all original assignments remain unchanged,
and the resulting configuration is identical in both runs.

## Coverage

- An isolated integrated world containing 144 basic/junction pipe samples and
  16 redstone-overlay samples, with actual world and held-item rendering.
- Two real Storage Drawers controllers, eight four-slot drawer blocks each,
  real extractor/responder chips and a native pipe connection. In both runs,
  8,224 total items remain conserved, 960 items arrive through native pipe
  transport in addition to one direct adapter transfer, and the same stock
  survives save/exit/reopen. The client observes moving native payloads.
- Empty item-flow lighting and matrix-stack checks: baseline characterizes the
  old lighting behavior; candidate verifies the early-return behavior.
- Actual resource reload, world close/reopen, client redstone state and stock
  persistence; screenshots are taken before and after those transitions.
- Native Forge item rendering of all 11 metadata icons, including inventory-only
  entries, at 16/32-pixel sizes under two mipmap modes. The baseline reproduces
  blurred/solid icons. All 44 candidate comparisons match nearest-sampled
  references exactly. The candidate records 132 sampler-restoration checkpoints
  (66 fixed gallery cases plus GUI-transition observations); GL checkpoints are 0.

**Wool-dyed pressure tubes are excluded from the full-pack pass criteria.** Their
model remains covered by the earlier development regression. The complete pack's
crafting list contains zero outputs for pipe metadata 7/8, and GTNH's custom pipe
recipe script does not re-add those entries. This is evidence about the available
recipes, not a claim that the underlying mod code has been disabled.

## Run

Use a clean checkout with the candidate production code and this validation
directory. A working Linux graphical session and JDK 25 are required. The
scripts use only disposable instances under `PR_FULLPACK_ROOT`; they never
connect to a remote game server. Reserve at least 12 GiB heap plus native memory.

```sh
export JAVA_HOME=/path/to/jdk25
export PR_FULLPACK_REPO=/path/to/checkout
export PR_FULLPACK_ROOT="$HOME/.local/share/gtnh-fullpack-verification"
export PR_FULLPACK_CACHE="$HOME/.cache/gtnh-fullpack-verification"
export PR_FULLPACK_OUTPUT="$PR_FULLPACK_ROOT/results"
mkdir -p "$PR_FULLPACK_ROOT/official-2.9.0" "$PR_FULLPACK_CACHE"
curl -fL --retry 2 \
  https://downloads.gtnewhorizons.com/Multi_mc_downloads/GT_New_Horizons_2.9.0_Java_17-26.zip \
  -o "$PR_FULLPACK_CACHE/GT_New_Horizons_2.9.0_Java_17-26.zip"
unzip -q "$PR_FULLPACK_CACHE/GT_New_Horizons_2.9.0_Java_17-26.zip" \
  -d "$PR_FULLPACK_ROOT/official-2.9.0"
cd "$PR_FULLPACK_REPO"
VERSION=4.12.50-GTNH ./gradlew --no-daemon --no-configuration-cache \
  -I integration/fullpack-graphics/fixture.init.gradle \
  -PfullpackRoot="$PWD/integration/fullpack-graphics" \
  fullpackFixtureJar downloadVanillaAssets
python integration/fullpack-graphics/prepare.py
python integration/fullpack-graphics/launch.py baseline
python integration/fullpack-graphics/launch.py candidate
python integration/fullpack-graphics/check-images.py \
  "$PR_FULLPACK_OUTPUT/baseline/inventory" "$PR_FULLPACK_OUTPUT/candidate/inventory" \
  --report "$PR_FULLPACK_OUTPUT/inventory-verification.json"
python integration/fullpack-graphics/summarize.py "$PR_FULLPACK_OUTPUT" \
  --instances "$PR_FULLPACK_ROOT"
```

`prepare.py` resolves the pack's own patch metadata, checks download SHA-1s,
selects Linux libraries, applies component library overrides, and uses the
official RFB/LWJGL3 JVM arguments. It can reuse downloaded Gradle assets and
libraries. If Forge's Maven endpoint rejects a request, set
`PR_FULLPACK_SERVER_PACK` to an extracted official 2.9.0 server pack, allowing
reuse of the identical Forge/library files. `PR_FULLPACK_ASSETS` can select an
existing verified Minecraft 1.7.10 assets directory.

The helper is built as a separate reobfuscated JAR; it is not included in the
ProjectRed release JAR. It explicitly loads the scene's chunks before native
block placement, waits for tile setup, and runs its worlds serially. Earlier
setup attempts that failed before verification are kept locally separately from
the final reports. No production code needed changing to complete these checks.

## Evidence and limits

`results/summary.json` records the source, ZIP and JAR hashes, loaded versions,
functional results, rendering checks and environment. Each variant contains
launch metadata, mod versions, scene/inventory JSON reports and actual game
inventory gallery screenshots. Full logs and repeated scene screenshots are
kept in the local complete archive rather than this reduced evidence branch.

The stock pack emits unrelated missing-texture, recipe, signature and Discord
warnings in these runs; they are not attributed to this PR. The baseline log also
ends with an unfinished shutdown-thread exception line after completed checks
and exit status 0. These are targeted ProjectRed graphical/functional checks,
not an all-mod certification or a performance benchmark. Windows graphics
drivers, every inventory-item combination and every game-world situation are
outside this coverage. The official pack/mod/asset binaries are not stored in
this validation branch. No production server process, mod or world was changed.
