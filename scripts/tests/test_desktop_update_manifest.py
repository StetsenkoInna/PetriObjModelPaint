import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('desktop_update_manifest', Path(__file__).resolve().parents[1] / 'desktop_update_manifest.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class DesktopUpdateManifestTest(unittest.TestCase):
    def create_archives(self, root, version='2.7.0', metadata_version=None):
        for platform in ('windows', 'linux', 'macos'):
            data = io.BytesIO()
            with zipfile.ZipFile(data, 'w') as jar:
                jar.writestr('ua/stetsenkoinna/updates/build.properties', f'version={metadata_version or version}\narch=amd64\n')
                jar.writestr('ua/stetsenkoinna/PetriObjPaintApp.class', b'example')
            with zipfile.ZipFile(root / f'petri-swing-ui-{version}-{platform}.zip', 'w') as archive:
                archive.writestr(f'package/petri-swing-ui-{platform}.jar', data.getvalue())
    def test_records_both_archive_and_jar_checksums_for_all_platforms(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); self.create_archives(root)
            manifest = module.build_manifest(root, '2.7.0')
            self.assertEqual(3, len(manifest['platforms']))
            for asset in manifest['platforms'].values():
                self.assertEqual(64, len(asset['sha256']))
                self.assertEqual(64, len(asset['jar_sha256']))
                self.assertNotEqual(asset['sha256'], asset['jar_sha256'])
                self.assertIn('/v2.7.0/', asset['url'])
    def test_rejects_wrong_build_version(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); self.create_archives(root, metadata_version='2.6.0')
            with self.assertRaises(ValueError): module.build_manifest(root, '2.7.0')
    def test_requires_all_platforms(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(FileNotFoundError): module.build_manifest(Path(directory), '2.7.0')
    def test_rejects_invalid_version(self):
        with self.assertRaises(ValueError): module.build_manifest(Path('.'), '../latest')
