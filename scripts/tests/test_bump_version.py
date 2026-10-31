import importlib.util
from pathlib import Path
import shutil
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("bump", Path(__file__).resolve().parents[1] / "bump_version.py")
bump = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(bump)

REPO = bump.REPO_ROOT


class VersionSitesTests(unittest.TestCase):
    """The release number is written in several files; these keep them in step."""

    def scratch_copy(self, temporary: str) -> Path:
        root = Path(temporary)
        for path in {site.path for site in bump.sites()}:
            (root / path).parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(REPO / path, root / path)
        return root

    def test_every_site_in_the_repository_agrees_with_the_root_pom(self):
        self.assertEqual([], bump.mismatches(REPO))

    def test_a_bump_moves_every_site_and_nothing_else(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = self.scratch_copy(temporary)
            old = bump.current_version(root)
            readme_before = (root / "README.md").read_text(encoding="utf-8")

            self.assertEqual(0, bump.main(["9.8.7", "--repo-root", str(root)]))

            self.assertEqual("9.8.7", bump.current_version(root))
            self.assertEqual([], bump.mismatches(root))
            readme = (root / "README.md").read_text(encoding="utf-8")
            self.assertNotIn(f"/v{old}/", readme)
            self.assertIn("releases/download/v9.8.7/petri-swing-ui-9.8.7-windows.zip", readme)
            # A historical mention is prose, not a link, and stays as written.
            self.assertEqual("From v2.4.0" in readme_before, "From v2.4.0" in readme)

    def test_line_endings_survive_a_bump(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = self.scratch_copy(temporary)
            before = {site.path: (root / site.path).read_bytes().count(b"\r\n") for site in bump.sites()}
            bump.main(["9.8.7", "--repo-root", str(root)])
            after = {site.path: (root / site.path).read_bytes().count(b"\r\n") for site in bump.sites()}
            self.assertEqual(before, after)

    def test_a_site_whose_layout_changed_fails_instead_of_being_skipped(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = self.scratch_copy(temporary)
            constants = root / "petri-model/src/main/java/ua/stetsenkoinna/pnml/PnmlConstants.java"
            constants.write_text(constants.read_text(encoding="utf-8").replace(
                "TOOL_VERSION_PETRI_OBJ_MODEL =", "TOOL_VERSION =", 1), encoding="utf-8")
            with self.assertRaises(SystemExit):
                bump.main(["9.8.7", "--repo-root", str(root)])

    def test_check_reports_a_site_left_behind(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = self.scratch_copy(temporary)
            pom = root / "petri-math/pom.xml"
            pom.write_text(pom.read_text(encoding="utf-8").replace(
                f"<version>{bump.current_version(root)}</version>", "<version>0.0.1</version>", 1),
                encoding="utf-8")
            self.assertEqual(1, bump.main(["--check", "--repo-root", str(root)]))

    def test_check_against_a_given_version_catches_an_unbumped_release(self):
        current = bump.current_version(REPO)
        self.assertEqual(0, bump.main(["--check", current]))
        self.assertEqual(1, bump.main(["--check", "9.8.7"]))


if __name__ == "__main__":
    unittest.main()
