# Inventory pipe rendering regression

The normal pipe frames are largely transparent. At hotbar sizes, atlas mipmaps
average those pixels with the narrow opaque frame, causing a solid dark prism
or blended routing colors. The Forge custom renderer previously inherited the
atlas's minification filter. Changing slots does not reliably identify the cause.

This fixture starts an isolated, actual Forge development client and integrated
flat world. It does not connect to the production server. It captures the hotbar
(basic pipe in slot 4 and junction pipe in slot 5), vanilla inventory, and native
Forge rendering of all 11 pipe variants before and after resource reload.

Each gallery has three rows at the same scale and with identical backgrounds:
nearest reference, incoming nearest mipmap, and incoming linear mipmap. Incoming
anisotropy is varied too, where supported. The test wrapper changes only incoming
sampler settings, calls the real registered renderer, and asserts that min/mag
filters and anisotropy are restored afterwards. It never supplies replacement
geometry or UVs. Screenshot analysis compares all 11 icons with the reference
in both 16-pixel and 32-pixel sizes (44 exact image comparisons).

Run with a working local graphical session and the project's Gradle JDK:

```sh
VERSION=4.12.50-GTNH ./gradlew --no-daemon --no-configuration-cache \
  -I integration/item-render-client.init.gradle \
  -PitemTestOutput=/absolute/path/to/candidate runClient
python integration/client-item-render/check-images.py \
  /absolute/path/to/baseline /absolute/path/to/candidate --report /absolute/path/to/report.json
```

Capture the baseline using this same fixture with `renders.scala` from
the `drawer-adapter.patch` state documented in
[historical sources](../source-snapshots/README.md). The baseline must reproduce a pixel
difference; the candidate must match the nearest reference exactly. The sampler
restoration assertions must pass in both runs.

Coverage is Linux/Mesa and the Forge development mod set, including native item,
held-item, world pipe and GUI rendering. The later [full-pack checks](../fullpack-graphics/README.md) cover the official
GTNH/Angelica distribution on Linux/Mesa; Windows graphics remain untested.
The fix is confined to the INVENTORY render path. It does not change routing,
connections, world models, packets, save data, or server-side logic.

The earlier development-client outputs are archived locally rather than repeated
in this branch. For retained baseline/candidate images and comparison results,
see ../fullpack-graphics/results. This fixture remains available for reproducing
the development-client check.
