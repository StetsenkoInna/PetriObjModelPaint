# Contributing to PetriObjModelPaint

Thanks for considering a contribution. This is a multi-module Maven project; see
the [README](README.md) for the module map (`petri-math`, `petri-api`,
`petri-model`, `petri-swing-ui`, `petri-server`) and the module-level guides
under [docs/](docs/).

## Before you start

- **Bug or small fix**: open a pull request directly.
- **New feature or behavior change**: open an issue first to discuss the
  approach before investing time in an implementation.

## License of contributions

This project is licensed per module: MIT for `petri-math`, `petri-api`,
`petri-model`, and PolyForm Noncommercial 1.0.0 for `petri-swing-ui` and
`petri-server` (see [LICENSE](LICENSE)). By submitting a pull request you agree
that your contribution is licensed under the same terms as the module(s) it
touches.

## Development setup

Requirements: Java 23+, Maven 3.9+.

```bash
mvn package -DskipTests
```

Run the test suite for the modules you touched before opening a PR:

```bash
mvn test -pl <module> -am
```

Code style follows [.editorconfig](.editorconfig) (4-space indent for
Java/XML, LF line endings, UTF-8). Match the conventions already used in the
file you're editing rather than introducing a new style.

## Pull requests

- Target the `master` branch.
- Keep PRs focused: one logical change per PR is easier to review than a
  bundle of unrelated fixes.
- Update the relevant guide under `docs/` (or the `README`) if your change
  affects documented behavior.
- Merging into `master` requires an approving review and passing CI.

## Releasing

Every release raises the release version: the tag, `petri-parent` and the
editor. The libraries (`petri-math`, `petri-api`, `petri-model`, `petri-server`)
each keep their own version and are raised to the release version only when
they changed since the previous release, so an unchanged library is not
published again under a new number. Never edit the versions by hand.

Run the **Bump version** workflow with the new release version. It detects
which libraries changed, updates every place a version is written with
`scripts/bump_version.py`, runs the tests and opens a pull request. Add
`docs/releases/<version>.md` to it, merge it, then publish a GitHub Release
with a new tag `v<version>` on the merged commit. The release publishes the
editor, the parent POM and only the libraries raised in it.

To bump locally, preview the plan, or see whether everything agrees:

```bash
python scripts/bump_version.py 2.7.0 --dry-run
python scripts/bump_version.py 2.7.0
python scripts/bump_version.py --check
```

## Reporting bugs

Open a [bug report issue](.github/ISSUE_TEMPLATE/bug_report.yml) with steps to
reproduce, expected vs. actual behavior, and your Java version / OS. For
security-relevant bugs, see [SECURITY.md](SECURITY.md) instead of opening a
public issue.

## Questions

Open a [feature request](.github/ISSUE_TEMPLATE/feature_request.yml) or a
regular issue. There's no separate discussion forum for this project yet.
