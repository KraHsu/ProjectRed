#!/usr/bin/env python3
"""Check native Forge item screenshots against nearest-sampled references."""
import argparse
import json
from pathlib import Path

from PIL import Image, ImageChops


def check(directory):
    runtime = json.loads((directory / "results.json").read_text())
    assert runtime.get("status") == "completed", runtime
    assert all(value == 0 for key, value in runtime.items() if key.endswith("_gl_error"))
    states = [value for key, value in runtime.items() if key.startswith("sampler-restored-")]
    assert len(states) >= 66 and all(states), "Missing atlas state restoration checks"
    gallery_states = [
        value for key, value in runtime.items()
        if key.startswith(("sampler-restored-2:", "sampler-restored-6:"))
    ]
    assert len(gallery_states) == 66 and all(gallery_states), "Missing gallery sampler coverage"
    comparisons = []
    for filename, scale in [("gallery-before.png", 1), ("gallery-after-reload.png", 2)]:
        image = Image.open(directory / "screenshots" / filename).convert("RGB")
        start = (image.width - 11 * 28 * scale) // 2
        for row in (1, 2):
            for meta in range(11):
                x = start + meta * 28 * scale
                y = 45 + row * 65
                reference = image.crop((x, 45, x + 16 * scale, 45 + 16 * scale))
                actual = image.crop((x, y, x + 16 * scale, y + 16 * scale))
                changed = sum(pixel != (0, 0, 0) for pixel in ImageChops.difference(reference, actual).getdata())
                comparisons.append({"scale": scale, "incoming_sampler": row, "meta": meta, "changed_pixels": changed})
    return {
        "completed": True,
        "state_checks": len(states),
        "gallery_state_checks": len(gallery_states),
        "transition_state_checks": len(states) - len(gallery_states),
        "comparisons": comparisons,
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    baseline, candidate = check(args.baseline), check(args.candidate)
    assert any(row["changed_pixels"] for row in baseline["comparisons"]), "Baseline did not reproduce the defect"
    assert all(row["changed_pixels"] == 0 for row in candidate["comparisons"]), "Candidate differs from clear reference"
    report = {
        "passed": True,
        "baseline": baseline,
        "candidate": candidate,
        "limits": [
            "Real Forge development client on Linux/Mesa, not the complete GTNH/Angelica client or Windows GPU stack",
            "Pixels compare native Forge inventory rendering under controlled sampler states; user hotbar movement remains a manual Windows check",
            "No connection to or restart of the production server",
        ],
    }
    args.report.write_text(json.dumps(report, indent=2) + "\n")
    print(f"PASS: {len(candidate['comparisons'])} exact pixel comparisons; {candidate['state_checks']} atlas state checks")
