# Historical benchmark sources

All four validation branches are consolidated into one evidence-only commit,
separate from the production PR. Retained report JSON, hashes, timings and
screenshots are unchanged; redundant outputs are archived locally.
Their old source IDs identify the original runs; those private commits are not
required to reproduce them. These cumulative, authorless source patches recreate
the measured intermediate states on upstream `6bb50c696f5eb19414b636c5ae19cb3d1a47ac98`:

| Patch | Historical source | Use |
| --- | --- | --- |
| `model-reuse.patch` | `08df35e48fee61d2108dc0afbd39cdbdacfc5502` | Model-cache CPU fixture and cache client/server checks |
| `routing-followups.patch` | `8337b995920cc69ed6c348d7cf80db03f550ee11` | Routing checks; drawer benchmark baseline |
| `drawer-adapter.patch` | `01cae953e1a54963758c3250e85363699b44d4c4` | Drawer benchmark candidate; inventory-rendering baseline |

For example, from the consolidated checkout:

```sh
git worktree add --detach ../pr-model-reuse 6bb50c696f5eb19414b636c5ae19cb3d1a47ac98
git -C ../pr-model-reuse apply "$PWD/integration/source-snapshots/model-reuse.patch"
```

Apply exactly one cumulative patch to each clean baseline checkout. Point the
fixture's init script/source root at this consolidated checkout. Build with
`VERSION=4.12.50-GTNH` and the documented JDK/toolchains. Original mod dependencies
and pack downloads are required; game binaries are not committed here.

Production candidate `c403ae4b04b90601034d2e98714f8c3e9ffe5393` has a `src` tree
byte-identical to historical final source
`9a619a135931b0d800e0bf01d27bd2abb3584353`. Use that production checkout, with
these validation files copied in, for inventory and full-pack checks (see
[setup](../README.md)). Production JAR hash:
`e86e94e869cb0e1e7c17d46eb19f7e704b0ae3906565927b02cb4d6684078867`.

Rebuilding intermediate states avoids attributing the model-cache-only 15.1x
CPU-path result to every change in the combined contribution. No new measurements
are claimed merely because these results have been consolidated.
