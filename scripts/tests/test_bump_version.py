import importlib.util
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("bump", Path(__file__).resolve().parents[1] / "bump_version.py")
bump = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(bump)

REPO = bump.REPO_ROOT


class VersionSitesTests(unittest.TestCase):
    """The release and every library carry their own version; these keep each one in step."""

    def scratch_copy(self, temporary: str) -> Path:
        root = Path(temporary)
        for path in {site.path for site in bump.sites()}:
            (root / path).parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(REPO / path, root / path)
        return root

    def scratch_repository(self, temporary: str) -> Path:
        """A scratch copy committed and tagged as the current release, for change detection."""
        root = self.scratch_copy(temporary)
        git = lambda *args: subprocess.run(["git", "-C", str(root), *args], check=True, capture_output=True)
        git("init", "-q")
        git("-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "--allow-empty", "-m", "base")
        git("add", "-A")
        git("-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "-m", "release")
        git("tag", "v" + bump.versions(root)[bump.RELEASE])
        return root

    def test_every_site_in_the_repository_agrees_with_the_poms(self):
        self.assertEqual([], bump.mismatches(REPO))

    def test_only_the_named_libraries_follow_the_release(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = self.scratch_copy(temporary)
            before = bump.versions(root)

            self.assertEqual(0, bump.main(["9.8.7", "--modules", "petri-model", "--repo-root", str(root)]))

            after = bump.versions(root)
            self.assertEqual("9.8.7", after[bump.RELEASE])
            self.assertEqual("9.8.7", after["petri-model"])
            self.assertEqual(before["petri-math"], after["petri-math"])
            self.assertEqual([], bump.mismatches(root))
            readme = (root / "README.md").read_text(encoding="utf-8")
            self.assertIn("releases/download/v9.8.7/petri-swing-ui-9.8.7-windows.zip", readme)
            self.assertIn("releases/download/v9.8.7/petri-model-9.8.7.jar", readme)
            # An unchanged library is still downloaded from the release that published it.
            math = before["petri-math"]
            self.assertIn(f"releases/download/v{math}/petri-math-{math}.jar", readme)
            constants = (root / "petri-model/src/main/java/ua/stetsenkoinna/pnml/PnmlConstants.java").read_text(
                encoding="utf-8")
            self.assertIn('TOOL_VERSION_PETRI_OBJ_MODEL = "9.8.7"', constants)

    def test_detection_raises_a_changed_library_and_nothing_else(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = self.scratch_repository(temporary)
            release = bump.versions(root)[bump.RELEASE]
            source = root / "petri-api/src/Changed.java"
            source.parent.mkdir(parents=True)
            source.write_text("class Changed {}", encoding="utf-8")
            subprocess.run(["git", "-C", str(root), "add", "-A"], check=True)
            self.assertEqual(["petri-api"], bump.changed_modules(root, "v" + release))

    def test_version_only_pom_edits_are_not_a_change(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = self.scratch_repository(temporary)
            release = bump.versions(root)[bump.RELEASE]
            bump.bump(root, "9.8.7", ["petri-math"])
            self.assertEqual([], bump.changed_modules(root, "v" + release))

    def test_a_root_pom_dependency_change_raises_every_library(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = self.scratch_repository(temporary)
            release = bump.versions(root)[bump.RELEASE]
            pom = root / "pom.xml"
            pom.write_bytes(pom.read_bytes().replace(b"<auto-service.version>", b"<auto-service.version>9", 1))
            self.assertEqual(list(bump.LIBRARIES), bump.changed_modules(root, "v" + release))

    def test_line_endings_survive_a_bump(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = self.scratch_copy(temporary)
            before = {site.path: (root / site.path).read_bytes().count(b"\r\n") for site in bump.sites()}
            bump.main(["9.8.7", "--modules", ",".join(bump.LIBRARIES), "--repo-root", str(root)])
            after = {site.path: (root / site.path).read_bytes().count(b"\r\n") for site in bump.sites()}
            self.assertEqual(before, after)

    def test_a_site_whose_layout_changed_fails_instead_of_being_skipped(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = self.scratch_copy(temporary)
            constants = root / "petri-model/src/main/java/ua/stetsenkoinna/pnml/PnmlConstants.java"
            constants.write_text(constants.read_text(encoding="utf-8").replace(
                "TOOL_VERSION_PETRI_OBJ_MODEL =", "TOOL_VERSION =", 1), encoding="utf-8")
            with self.assertRaises(SystemExit):
                bump.main(["9.8.7", "--modules", "", "--repo-root", str(root)])

    def test_check_reports_a_pin_left_behind(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = self.scratch_copy(temporary)
            pom = root / "pom.xml"
            math = bump.versions(root)["petri-math"]
            pom.write_bytes(pom.read_bytes().replace(
                f"<petri-math.version>{math}<".encode(), b"<petri-math.version>0.0.1<", 1))
            self.assertEqual(1, bump.main(["--check", "--repo-root", str(root)]))

    def test_check_against_a_given_release_catches_an_unbumped_release(self):
        release = bump.versions(REPO)[bump.RELEASE]
        self.assertEqual(0, bump.main(["--check", release]))
        self.assertEqual(1, bump.main(["--check", "9.8.7"]))


if __name__ == "__main__":
    unittest.main()
