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
