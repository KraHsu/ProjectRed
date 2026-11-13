# Contribution validation

This separate validation branch contains fixtures and recorded results only.
The production PR contains seven source files and one unit-test file; these
materials are not part of its diff. Both branches are squashed independently
to one commit above the unchanged upstream base. Fixtures run against disposable
local worlds/instances and are excluded from the ProjectRed release JAR.

To use this evidence with the production candidate (from a clone of KraHsu/ProjectRed):

```sh
git worktree add --detach ../projectred-validation c403ae4b04b90601034d2e98714f8c3e9ffe5393
git -C ../projectred-validation restore --source=origin/validation/consolidated-evidence --worktree -- integration
```

Run the individual fixture instructions from that disposable checkout. The
validation branch itself retains the upstream production sources, so do not
build it directly as the final candidate.

| Directory | Coverage |
| --- | --- |
| [performance](performance/README.txt) | Model-cache CPU fixture, development client and GTNH dedicated-server checks |
| [drawers-client-test](drawers-client-test/fixture/README.txt) | Three paired real-client drawer-transfer measurements, per-run measurements and representative screenshots |
| [client-item-render](client-item-render/README.md) | Development-client inventory sampler regression and 44 image comparisons |
| [fullpack-graphics](fullpack-graphics/README.md) | Official GTNH 2.9.0 with Angelica, native transport, reloads and 44 image comparisons |
| [source-snapshots](source-snapshots/README.md) | Reconstruct historical benchmark source states without separate private branches |

Historical reports retain their original source/artifact hashes and environment
paths as provenance. Consult each directory's limits before interpreting results.
In particular, the model-cache fixture ratio is not a whole-game FPS/TPS gain;
the paired drawer measurements use the development mod set; complete-pack checks
are targeted graphical/functional validation, not a full-pack performance test.

## Retained evidence

This branch keeps runnable fixtures, historical source patches, aggregate and
per-run JSON measurements, and six key screenshots: the four full-pack inventory
galleries needed to recompute all image comparisons, plus one native drawer scene
per variant. The six drawer run reports retain per-phase tick/frame statistics
and packet counts, so the reported paired statistics can be recomputed.

Repeated scene/reload screenshots, raw frame CSVs, launch/build logs, duplicate
unit-test XML and the earlier development inventory result set were removed
from this review branch. Complete originals remain in the author's local archive.
This is a reduction of published evidence, not a new test run. Raw frame-level
analysis and log inspection require that archive or a new fixture run. Scripts
still produce complete new results when rerun. The historical complete-pack
checks and their limitations remain documented in the suite README.
