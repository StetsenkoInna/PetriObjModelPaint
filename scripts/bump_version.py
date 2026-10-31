#!/usr/bin/env python3
"""Set this project's versions everywhere they are written down.

There are two kinds of version here:

  - The release version: the Git tag, `petri-parent`, the editor (`petri-swing-ui`) and its
    download archives. Every release raises it.
  - A version per library module: `petri-math`, `petri-api`, `petri-model` and `petri-server`.
    A release raises a module to the release version only if the module changed since the
    previous release; an unchanged module keeps the version it already has, so nobody
    downloads an identical library under a new number.

A module counts as changed when anything under its folder differs from the previous release
tag, or when the root pom changed beyond version numbers (a dependency pin it inherits).
Dependents are not raised along with a changed module: the parent pom, published with every
release, pins each library's current version, and an older published module keeps resolving
the versions it was built against.

Where each version is written:

  - pom.xml: the release version, and a `<petri-*.version>` property per library
  - every module pom: its `<parent>` version (the release) and its own version
  - PnmlConstants.TOOL_VERSION_PETRI_OBJ_MODEL: petri-model's version, the writer that
    stamps it into every PNML file (ToolSpecificIdentityTest checks the two agree)
  - README.md, README.uk.md: editor download links (release) and library links (each module)
  - docs/java-libraries.md: the Maven example (petri-math) and the example tag (release)

Usage, from anywhere:

    python scripts/bump_version.py 2.7.0              # raise what changed since the last tag
    python scripts/bump_version.py 2.7.0 --dry-run    # only show what would be raised
    python scripts/bump_version.py 2.7.0 --modules petri-math,petri-model
    python scripts/bump_version.py --check            # every site agrees with the poms
    python scripts/bump_version.py --check 2.7.0      # ... and the release is 2.7.0

`--modules` replaces the detection with an explicit list. The bump-version workflow runs the
first form, then the tests, and opens a pull request; the release build runs the last form
against its tag. Each pattern below must match, so a file whose layout drifted away from it
fails loudly instead of being skipped.

Standard library only, so it runs on any Python 3.10+ without a virtual environment.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path
from typing import NamedTuple

VERSION_RE = re.compile(r"^\d+\.\d+\.\d+$")
REPO_ROOT = Path(__file__).resolve().parent.parent
RELEASE = "release"
LIBRARIES = ("petri-math", "petri-api", "petri-model", "petri-server")
MODULES = ("petri-math", "petri-api", "petri-model", "petri-swing-ui", "petri-server")
V = r"(\d+\.\d+\.\d+)"
DOWNLOAD = "releases/download/v"


class Site(NamedTuple):
    """One place that states a version: a file, a pattern whose group 1 is it, and whose it is."""

    path: str
    pattern: str
    owner: str
    # How many matches the file must have; a different count means its layout changed.
    count: int = 1


def own_version_site(module: str) -> Site:
    owner = RELEASE if module == "petri-swing-ui" else module
    return Site(f"{module}/pom.xml", rf"</parent>\s*<artifactId>{module}</artifactId>\s*<version>" + V, owner)


def sites() -> list[Site]:
    result = [
        Site("pom.xml", r"<artifactId>petri-parent</artifactId>\s*<packaging>pom</packaging>\s*<version>" + V,
             RELEASE),
    ]
    for module in LIBRARIES:
        result.append(Site("pom.xml", rf"<{module}\.version>{V}</{module}\.version>", module))
    for module in MODULES:
        result.append(Site(f"{module}/pom.xml",
                           r"<parent>\s*<groupId>ua\.stetsenkoinna</groupId>\s*"
                           r"<artifactId>petri-parent</artifactId>\s*<version>" + V, RELEASE))
        result.append(own_version_site(module))
    result.append(Site("petri-model/src/main/java/ua/stetsenkoinna/pnml/PnmlConstants.java",
                       r'TOOL_VERSION_PETRI_OBJ_MODEL = "' + V + '"', "petri-model"))
    for readme in ("README.md", "README.uk.md"):
        # Each link names a version twice, the tag and the file, and both belong to its owner:
        # an unchanged library is still downloaded from the release that published it.
        result.append(Site(readme, DOWNLOAD + V + r"/petri-swing-ui-", RELEASE, count=3))
        result.append(Site(readme, DOWNLOAD + r"\d+\.\d+\.\d+/petri-swing-ui-" + V + "-", RELEASE, count=3))
        for module in ("petri-math", "petri-api", "petri-model"):
            result.append(Site(readme, DOWNLOAD + V + rf"/{module}-", module))
            result.append(Site(readme, DOWNLOAD + rf"\d+\.\d+\.\d+/{module}-" + V + r"\.jar", module))
    result.append(Site("docs/java-libraries.md",
                       r"<artifactId>petri-math</artifactId>\s*<version>" + V + "</version>", "petri-math"))
    result.append(Site("docs/java-libraries.md", r"`v" + V + r"`", RELEASE, count=2))
    return result


def find(root: Path, site: Site) -> list[re.Match[str]]:
    text = (root / site.path).read_text(encoding="utf-8")
    matches = list(re.finditer(site.pattern, text))
    if len(matches) != site.count:
        raise SystemExit(f"{site.path}: expected {site.count} match(es) of {site.pattern!r}, "
                         f"found {len(matches)}. Has the file's layout changed?")
    return matches


def versions(root: Path) -> dict[str, str]:
    """The release version and every library's, as the poms state them."""
    result = {RELEASE: find(root, sites()[0])[0].group(1)}
    for module in LIBRARIES:
        result[module] = find(root, own_version_site(module))[0].group(1)
    return result


def mismatches(root: Path, release: str | None = None) -> list[str]:
    """Every site that disagrees with its owner's version; release, if given, is checked too."""
    expected = versions(root)
    problems = []
    if release and expected[RELEASE] != release:
        problems.append(f"pom.xml: release {expected[RELEASE]} (expected {release})")
    for site in sites():
        for match in find(root, site):
            if match.group(1) != expected[site.owner]:
                problems.append(f"{site.path}: {site.owner} {match.group(1)} "
                                f"(the poms say {expected[site.owner]})")
    return problems


def changed_modules(root: Path, since: str) -> list[str]:
    """Libraries whose files differ from the tag `since`; all of them if the root pom's pins did.

    Pom lines that only carry versions (this script's own edits, and the version properties
    and references themselves) do not count as a change.
    """
    def diff(*paths: str) -> str:
        return subprocess.run(["git", "-C", str(root), "diff", "--unified=0", since, "--", *paths],
                              check=True, capture_output=True, text=True).stdout

    bookkeeping = re.compile(r"^\s*(<version>(\d+\.\d+\.\d+|\$\{(petri-[a-z-]+|project)\.version\})</version>"
                             r"|<petri-[a-z-]+\.version>.*|<!-- Each library has its own version.*)\s*$")

    def pom_changed(path: str) -> bool:
        lines = [line[1:] for line in diff(path).splitlines()
                 if line[:1] in "+-" and not line.startswith(("+++", "---")) and line[1:].strip()]
        return any(not bookkeeping.match(line) for line in lines)

    if pom_changed("pom.xml"):
        return list(LIBRARIES)
    return [module for module in LIBRARIES
            if diff(f"{module}/", f":(exclude){module}/pom.xml") or pom_changed(f"{module}/pom.xml")]


def bump(root: Path, release: str, modules: list[str]) -> list[str]:
    """Raises the release and the given modules to release; returns the files it changed."""
    target = versions(root)
    target[RELEASE] = release
    for module in modules:
        target[module] = release
    by_file: dict[str, list[Site]] = {}
    for site in sites():
        by_file.setdefault(site.path, []).append(site)
    changed = []
    for path, file_sites in by_file.items():
        file = root / path
        # Bytes in, bytes out: several of these files are CRLF and must stay that way.
        text = file.read_bytes().decode("utf-8")
        original = text
        for site in file_sites:
            find(root, site)  # validates the count before anything is written
            new = target[site.owner]
            text = re.sub(site.pattern,
                          lambda m: m.group(0)[:m.start(1) - m.start(0)] + new
                          + m.group(0)[m.end(1) - m.start(0):],
                          text)
        if text != original:
            file.write_bytes(text.encode("utf-8"))
            changed.append(path)
    return changed


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("version", nargs="?", help="the new release version, e.g. 2.7.0")
    parser.add_argument("--check", action="store_true", help="only verify every site agrees")
    parser.add_argument("--dry-run", action="store_true", help="show what would be raised")
    parser.add_argument("--modules", help="comma-separated libraries to raise, instead of detecting")
    parser.add_argument("--repo-root", type=Path, default=REPO_ROOT, help=argparse.SUPPRESS)
    args = parser.parse_args(argv)
    root = args.repo_root

    if args.version and not VERSION_RE.match(args.version):
        parser.error("give the version as MAJOR.MINOR.PATCH, e.g. 2.7.0")

    if args.check:
        problems = mismatches(root, args.version)
        for line in problems:
            print(line)
        if not problems:
            print("Every version site agrees: " + ", ".join(f"{k} {v}" for k, v in versions(root).items()))
        return 1 if problems else 0

    if not args.version:
        parser.error("give the new release version, e.g. 2.7.0")
    before = versions(root)
    if args.modules is not None:
        modules = [m.strip() for m in args.modules.split(",") if m.strip()]
        unknown = [m for m in modules if m not in LIBRARIES]
        if unknown:
            parser.error(f"unknown modules {unknown}; choose from {', '.join(LIBRARIES)}")
    else:
        modules = changed_modules(root, f"v{before[RELEASE]}")

    print(f"release: {before[RELEASE]} -> {args.version}")
    for module in LIBRARIES:
        if module in modules:
            print(f"{module}: {before[module]} -> {args.version} (changed)")
        else:
            print(f"{module}: {before[module]} (unchanged)")
    if args.dry_run:
        return 0
    for path in bump(root, args.version, modules):
        print(f"updated {path}")
    problems = mismatches(root, args.version)
    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
