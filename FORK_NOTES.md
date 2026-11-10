# GTNH 2.9 local fixes

This fork starts from upstream tag `4.12.50-GTNH` (`4766701a7674f6e0eedf57be7e8ee987393680c1`). Upstream's MIT license and notices are retained.

The branch `fix/gtnh-2.9-render-routing` contains:

- **Client rendering:** retain the prebuilt pipe models instead of generating three new sets after every static pipe draw. Initialize the wool overlay to the original steady-state scale of `0.85`.
- **Server routing correctness:** when a destination inventory rejects a delivery, exclude that destination for the current trip and release the remaining reservation before rerouting. This also covers partial insertions followed by rejection.
- **Server accounting:** subtract only the accepted quantity from in-transit reservations when an insertion is partial.

The fork preserves the `4.12.50-GTNH` mod version, IDs, packet formats, recipes and saved payload formats. Identify custom artifacts by their distribution filename and checksum. A rendering-only client can join an original server; a routing fix requires installation on the server. Restart only the component whose JAR is replaced.

Interfaces already share the routed-junction network implementation. No new junction feature or deliberate random routing was added. The regression scenarios distinguish a valid adjacent-interface layout from a rejected destination; they do not assume every reported loop has the same cause.

Build with Java 25 and a Java 8 toolchain available to Gradle:

```sh
VERSION=4.12.50-GTNH ./gradlew --no-daemon test build
```

The full-pack routing checks are documented in [integration/README.md](integration/README.md). The current change does not move topology discovery onto more threads, cache live inventory capacity, or otherwise assume a service-side performance bottleneck. Inventory capacity can change every tick and must remain authoritative.
