"""Validate installable JARs and stage the release assets. Requires Python 3.11+."""
import argparse
import hashlib
import json
import re
import tempfile
import tomllib
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MOD = "fourwindsroadworks"
DESCRIPTORS = {"fabric.mod.json", "META-INF/mods.toml", "META-INF/neoforge.mods.toml", "mcmod.info"}


def properties(text):
    return dict(line.split("=", 1) for line in text.splitlines() if line and not line.startswith("#"))


def projects(default_game):
    result = {ROOT: ("neoforge", default_game)}
    for build in sorted((ROOT / "ports").glob("*/build.gradle")):
        loader, game = build.parent.name.rsplit("-", 1)
        if loader not in {"forge", "fabric", "legacy-fabric"} or not re.fullmatch(r"\d+\.\d+\.\d+", game):
            raise ValueError(f"Unknown release target {build.parent.name}")
        result[build.parent] = (loader, game)
    return result


def verify(jar, loader, game, version):
    def require(condition, message):
        if not condition:
            raise ValueError(f"{jar.name}: {message}")

    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
        require(archive.testzip() is None, "damaged archive")
        if loader in {"fabric", "legacy-fabric"}:
            descriptor = "fabric.mod.json"
            metadata = json.loads(archive.read(descriptor))
            require(metadata["id"] == MOD and metadata["depends"]["minecraft"] == game, "wrong Fabric target")
        elif loader == "forge" and game == "1.12.2":
            descriptor = "mcmod.info"
            metadata, = json.loads(archive.read(descriptor))
            require(metadata["modid"] == MOD and metadata["mcversion"] == game, "wrong Forge Classic target")
            runtime = properties(archive.read("version.properties").decode())
            require(runtime.get(MOD + ".version") == version, "Forge runtime version differs from mcmod.info")
        else:
            descriptor = "META-INF/neoforge.mods.toml" if loader == "neoforge" else "META-INF/mods.toml"
            config = tomllib.loads(archive.read(descriptor).decode())
            mod, = config["mods"]
            dependencies = {entry["modId"]: entry for entry in config["dependencies"][MOD]}
            require(mod["modId"] == MOD and loader in dependencies, "wrong Forge/NeoForge target")
            require(dependencies["minecraft"]["versionRange"] == f"[{game}]", "wrong Minecraft range")
            metadata = {"version": mod["version"], "license": config["license"]}
        require(names & DESCRIPTORS == {descriptor}, "unexpected loader metadata")
        require(metadata["version"] == version and metadata["license"] == "MIT", "wrong version or licence")
        require(archive.read("META-INF/LICENSE").decode().splitlines() == (ROOT / "LICENSE").read_text().splitlines(),
                "licence notice does not match the repository")
        require(archive.read("four-winds.png") == (ROOT / "src/main/resources/four-winds.png").read_bytes(),
                "wrong mod icon")
        lang_dir = f"assets/{MOD}/lang/"
        suffix = ".lang" if game == "1.12.2" else ".json"
        translations = {lang_dir + path.stem + suffix for path in (ROOT / "src/main/resources" / lang_dir).glob("*.json")}
        require({n for n in names if n.startswith(lang_dir) and not n.endswith("/")} == translations,
                "packaged languages differ from the source translations")
        require(any(name.endswith("/coastal_lighthouse.nbt") for name in names), "missing lighthouse")
        for name in ("crossroads_waypost", "village_waypost"):
            require(f"assets/{MOD}/models/block/{name}.json" in names, f"missing {name} model")
        for name in names:
            if name.endswith(".mixins.json"):
                mixins = json.loads(archive.read(name))
                for side in ("mixins", "client", "server"):
                    for cls in mixins.get(side, []):
                        classfile = (mixins["package"] + "." + cls).replace(".", "/") + ".class"
                        require(classfile in names, f"missing mixin {classfile}")


def stage(selected, release, output):
    output.parent.mkdir(parents=True, exist_ok=True)
    assets = {}
    with tempfile.TemporaryDirectory(dir=output.parent) as temporary:
        staging = Path(temporary)
        for project, (loader, game) in selected.items():
            prefix = "four-winds-roadworks" if loader == "neoforge" else f"four-winds-roadworks-{loader}"
            version = f"{release}+mc{game}"
            source = project / "build/libs" / f"{prefix}-{version}.jar"
            if not source.is_file():
                raise ValueError(f"Build {project.relative_to(ROOT)} first. Missing {source.name}")
            filename = f"four-winds-roadworks-{game}-{loader}-{release}.jar"
            staged = staging / filename
            data = source.read_bytes()
            staged.write_bytes(data)
            verify(staged, loader, game, version)
            assets[filename] = hashlib.sha256(data).hexdigest()
        checksums = "".join(f"{assets[name]}  {name}\n" for name in sorted(assets))
        (staging / "SHA256SUMS.txt").write_bytes(checksums.encode("ascii"))
        output.mkdir(exist_ok=True)
        unknown = {p.name for p in output.iterdir()} - set(assets) - {"SHA256SUMS.txt"}
        if unknown:
            raise ValueError(f"Unexpected files in {output}: {', '.join(sorted(unknown))}")
        for name in [*assets, "SHA256SUMS.txt"]:
            (staging / name).replace(output / name)
    return sorted(output / name for name in assets)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, help="Stage one Gradle project's JAR for CI and print its path")
    args = parser.parse_args()
    settings = properties((ROOT / "gradle.properties").read_text())
    release = settings["modVersion"]
    if not re.fullmatch(r"(?:0|[1-9]\d*)(?:\.(?:0|[1-9]\d*)){2}(?:-rc\.[1-9]\d*)?", release):
        parser.error(f"Invalid release version {release!r}")
    targets = projects(settings["neoMinecraftVersion"])
    output = ROOT / "build/distributions" / f"v{release}"
    if args.project is not None:
        project = args.project.resolve()
        if project not in targets:
            parser.error(f"Unknown Gradle project {project}")
        targets = {project: targets[project]}
        output = project / "build/release-asset" / f"v{release}"
    try:
        jars = stage(targets, release, output)
    except (ValueError, OSError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, f"{error}\n")
    print(jars[0] if args.project is not None else output)
