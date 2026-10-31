#!/usr/bin/env python3
"""Set this project's release number everywhere it is written down.

The version lives in more places than the Maven poms, and a release that bumps only the poms
ships with the rest out of date:

  - pom.xml (petri-parent) and the <parent> block of every module pom
  - PnmlConstants.TOOL_VERSION_PETRI_OBJ_MODEL, stamped into every PNML file the app writes
    (ToolSpecificIdentityTest fails when it disagrees with the pom)
  - the release download links in README.md and README.uk.md
  - the Maven coordinates and example tag in docs/java-libraries.md

Usage, from anywhere:

    python scripts/bump_version.py 2.6.0
    python scripts/bump_version.py --check
    python scripts/bump_version.py --check 2.6.0

The first rewrites every place above. The others only report whether they all agree, with the
root pom or with the version given, and exit non-zero if not; the release build runs the last
form against its tag, so a release whose source was never bumped stops before it is built. The bump-version workflow runs the first, then the test
suites, and only then commits and tags. Historical mentions ("From v2.4.0, releases include")
are left alone: only the patterns below are touched, and each one must match, so a file whose
layout drifted away from them fails loudly instead of being skipped.

Standard library only, so it runs on any Python 3.10+ without a virtual environment.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from typing import NamedTuple

VERSION_RE = re.compile(r"^\d+\.\d+\.\d+$")
REPO_ROOT = Path(__file__).resolve().parent.parent
MODULES = ("petri-math", "petri-api", "petri-model", "petri-swing-ui", "petri-server")


class Site(NamedTuple):
    """One place that states the version: a file and a pattern whose group 1 is the version."""

    path: str
    pattern: str
    # How many matches the file must have; a different count means its layout changed.
    count: int = 1


def sites() -> list[Site]:
    v = r"(\d+\.\d+\.\d+)"
    result = [
        Site("pom.xml", r"<artifactId>petri-parent</artifactId>\s*<packaging>pom</packaging>\s*<version>" + v),
    ]
    for module in MODULES:
        result.append(Site(f"{module}/pom.xml",
                           r"<parent>\s*<groupId>ua\.stetsenkoinna</groupId>\s*"
                           r"<artifactId>petri-parent</artifactId>\s*<version>" + v))
    result.append(Site("petri-model/src/main/java/ua/stetsenkoinna/pnml/PnmlConstants.java",
                       r'TOOL_VERSION_PETRI_OBJ_MODEL = "' + v + '"'))
    for readme in ("README.md", "README.uk.md"):
        # Each link names the version twice: the tag and the file. Both are sites.
        result.append(Site(readme, r"releases/download/v" + v + r"/", count=6))
        result.append(Site(readme, r"releases/download/v\d+\.\d+\.\d+/[a-z-]+-" + v + r"[-.]", count=6))
    result.append(Site("docs/java-libraries.md", r"<version>" + v + r"</version>"))
    result.append(Site("docs/java-libraries.md", r"`v" + v + r"`", count=2))
    return result


def find(root: Path, site: Site) -> list[re.Match[str]]:
    text = (root / site.path).read_text(encoding="utf-8")
    matches = list(re.finditer(site.pattern, text))
    if len(matches) != site.count:
        raise SystemExit(f"{site.path}: expected {site.count} match(es) of {site.pattern!r}, "
                         f"found {len(matches)}. Has the file's layout changed?")
    return matches


def current_version(root: Path) -> str:
    return find(root, sites()[0])[0].group(1)


def mismatches(root: Path, expected: str | None = None) -> list[str]:
    """Every site whose version differs from expected (the root pom's by default)."""
    source = "expected" if expected else "pom says"
    expected = expected or current_version(root)
    problems = []
    for site in sites():
        for match in find(root, site):
            if match.group(1) != expected:
                problems.append(f"{site.path}: {match.group(1)} ({source} {expected})")
    return problems


def bump(root: Path, new_version: str) -> list[str]:
    """Rewrites every site to new_version; returns the files it changed."""
    changed = []
    by_file: dict[str, list[Site]] = {}
    for site in sites():
        by_file.setdefault(site.path, []).append(site)
    for path, file_sites in by_file.items():
        file = root / path
        # Bytes in, bytes out: several of these files are CRLF and must stay that way.
        text = file.read_bytes().decode("utf-8")
        original = text
        for site in file_sites:
            find(root, site)  # validates the count before anything is written
            text = re.sub(site.pattern,
                          lambda m: m.group(0)[:m.start(1) - m.start(0)] + new_version
                          + m.group(0)[m.end(1) - m.start(0):],
                          text)
        if text != original:
            file.write_bytes(text.encode("utf-8"))
            changed.append(path)
    return changed


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("version", nargs="?", help="the new version, e.g. 2.6.0")
    parser.add_argument("--check", action="store_true", help="only verify every site agrees")
    parser.add_argument("--repo-root", type=Path, default=REPO_ROOT, help=argparse.SUPPRESS)
    args = parser.parse_args(argv)
    root = args.repo_root

    if args.version and not VERSION_RE.match(args.version):
        parser.error("give the version as MAJOR.MINOR.PATCH, e.g. 2.6.0")

    if args.check:
        problems = mismatches(root, args.version)
        for line in problems:
            print(line)
        if not problems:
            print(f"Every version site says {current_version(root)}.")
        return 1 if problems else 0

    if not args.version:
        parser.error("give the new version as MAJOR.MINOR.PATCH, e.g. 2.6.0")
    old = current_version(root)
    for path in bump(root, args.version):
        print(f"updated {path}")
    problems = mismatches(root)
    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    print(f"{old} -> {args.version}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
