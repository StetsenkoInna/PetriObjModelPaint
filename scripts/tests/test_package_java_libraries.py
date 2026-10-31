import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("libraries", Path(__file__).resolve().parents[1] / "package_java_libraries.py")
libraries = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(libraries)


class PublishedArtifactTests(unittest.TestCase):
    def test_released_files_are_immutable_and_have_checksums(self):
        with tempfile.TemporaryDirectory() as temporary:
            artifact = Path(temporary) / "sample.jar"
            libraries.write_immutable(artifact, b"original")
            libraries.write_immutable(artifact, b"original")
            with self.assertRaises(ValueError):
                libraries.write_immutable(artifact, b"replacement")
            self.assertEqual(b"original", artifact.read_bytes())
            self.assertTrue(artifact.with_name("sample.jar.sha256").is_file())
            self.assertTrue(artifact.with_name("sample.jar.sha512").is_file())


class ReleasedModulesTests(unittest.TestCase):
    def test_only_libraries_raised_to_the_release_are_published(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for module, version in (("petri-math", "2.6.0"), ("petri-api", "2.7.0"), ("petri-model", "2.7.0")):
                (root / module).mkdir()
                (root / module / "pom.xml").write_text(
                    '<project xmlns="http://maven.apache.org/POM/4.0.0"><parent><version>2.7.0</version></parent>'
                    f'<artifactId>{module}</artifactId><version>{version}</version></project>', encoding="utf-8")
            self.assertEqual(["petri-api", "petri-model"], sorted(libraries.released_modules(root, "2.7.0")))
