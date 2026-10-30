"""Prepare public Maven artifacts and release downloads without publishing them."""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
MODULES = ("petri-math", "petri-api", "petri-model")


def write_immutable(path: Path, content: bytes) -> None:
    if path.exists() and path.read_bytes() != content:
        raise ValueError(f"Refusing to replace an existing released artifact: {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(content)
    for algorithm in ("sha1", "sha256", "sha512"):
        path.with_name(path.name + "." + algorithm).write_text(
            hashlib.new(algorithm, content).hexdigest() + "\n", encoding="ascii")


def source_jar(module: Path, destination: Path) -> None:
    with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as archive:
        paths = [(p.relative_to(module / "src/main/java").as_posix(), p)
                 for p in (module / "src/main/java").rglob("*") if p.is_file()]
        paths.append(("META-INF/LICENSE", module / "LICENSE"))
        for name, path in sorted(paths):
            info = zipfile.ZipInfo(name, (2024, 1, 1, 0, 0, 0))
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            archive.writestr(info, path.read_bytes())


def package(root: Path, repository: Path, output: Path) -> str:
    pom = ET.parse(root / "pom.xml").getroot()
    group = pom.findtext("m:groupId", namespaces=NS)
    version = pom.findtext("m:version", namespaces=NS)
    if not group or not version or version.endswith("-SNAPSHOT"):
        raise ValueError("Expected a release group and version")
    output.mkdir(parents=True, exist_ok=True)
    base = repository / group.replace(".", "/")
    artifacts = {"petri-parent": root / "pom.xml", **{m: root / m / "pom.xml" for m in MODULES}}
    for name, pom_path in artifacts.items():
        target = base / name / version
        stem = f"{name}-{version}"
        write_immutable(target / (stem + ".pom"), pom_path.read_bytes())
        shutil.copy2(target / (stem + ".pom"), output / (stem + ".pom"))
        if name != "petri-parent":
            module = root / name
            jar = module / "target" / (name + ".jar")
            if not jar.is_file():
                raise ValueError(f"Build {name} before packaging: {jar}")
            # Distribution must include the module's own license, also in binary JARs.
            binary = output / (stem + ".jar")
            shutil.copy2(jar, binary)
            with zipfile.ZipFile(binary, "a") as archive:
                if "META-INF/LICENSE" not in archive.namelist():
                    info = zipfile.ZipInfo("META-INF/LICENSE", (2024, 1, 1, 0, 0, 0))
                    info.create_system = 3
                    info.external_attr = 0o100644 << 16
                    archive.writestr(info, (module / "LICENSE").read_bytes())
            write_immutable(target / binary.name, binary.read_bytes())
            sources = output / (stem + "-sources.jar")
            source_jar(module, sources)
            write_immutable(target / sources.name, sources.read_bytes())
        versions = sorted(p.name for p in (base / name).iterdir() if p.is_dir())
        versions.sort(key=lambda text: tuple(int(part) for part in text.split(".")))
        metadata = ET.Element("metadata")
        ET.SubElement(metadata, "groupId").text = group
        ET.SubElement(metadata, "artifactId").text = name
        versioning = ET.SubElement(metadata, "versioning")
        ET.SubElement(versioning, "latest").text = versions[-1]
        ET.SubElement(versioning, "release").text = versions[-1]
        listing = ET.SubElement(versioning, "versions")
        for available in versions:
            ET.SubElement(listing, "version").text = available
        meta = base / name / "maven-metadata.xml"
        meta.write_bytes(ET.tostring(metadata, encoding="utf-8", xml_declaration=True))
        for algorithm in ("sha1", "sha256", "sha512"):
            meta.with_name(meta.name + "." + algorithm).write_text(
                hashlib.new(algorithm, meta.read_bytes()).hexdigest() + "\n", encoding="ascii")
    archive = output / f"petri-libraries-{version}-maven.zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as zipped:
        for path in sorted(repository.rglob("*")):
            if path.is_file() and ".git" not in path.relative_to(repository).parts:
                zipped.write(path, path.relative_to(repository).as_posix())
    outputs = sorted(p for p in output.iterdir() if p.is_file() and p.name != "SHA256SUMS.txt")
    (output / "SHA256SUMS.txt").write_text("".join(
        f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n" for p in outputs), encoding="ascii")
    return version


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, default=ROOT / "target/maven-repository")
    parser.add_argument("--output", type=Path, default=ROOT / "target/library-release")
    args = parser.parse_args()
    print("Prepared Java libraries " + package(ROOT, args.repository, args.output))
