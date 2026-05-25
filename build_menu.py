#!/usr/bin/env python3
"""
Vonix Server Utilities — Build Menu
=====================================
Unified build interface for all four Architectury template versions.
Auto-detects installed Java versions and selects the correct JDK for each
Minecraft version. All output is live-streamed and logged.
"""

import os
import re
import sys
import json
import time
import shutil
import subprocess
import datetime
from pathlib import Path

# Force UTF-8 encoding on stdout/stderr to support unicode drawing characters when piped/redirected
if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
if hasattr(sys.stderr, "reconfigure"):
    try:
        sys.stderr.reconfigure(encoding="utf-8")
    except Exception:
        pass

# ── ANSI colours ──────────────────────────────────────────────────────────────

RESET  = "\033[0m"
BOLD   = "\033[1m"
DIM    = "\033[2m"
RED    = "\033[91m"
GREEN  = "\033[92m"
YELLOW = "\033[93m"
BLUE   = "\033[94m"
CYAN   = "\033[96m"
WHITE  = "\033[97m"
MAGENTA= "\033[95m"

def c(color, text): return f"{color}{text}{RESET}"

# ── Project registry ──────────────────────────────────────────────────────────

ROOT = Path(__file__).parent.resolve()

def scan_projects() -> list[dict]:
    """
    Dynamically scans the current directory for Architectury-like project folders.
    Looks for folders containing 'gradle.properties'.
    Extracts 'minecraft_version' and 'enabled_platforms' to build the project config.
    """
    projects = []
    
    # Iterate through all subdirectories in ROOT
    for child in ROOT.iterdir():
        if not child.is_dir() or child.name.startswith('.') or child.name == 'build_logs':
            continue
            
        gradle_props = child / "gradle.properties"
        if not gradle_props.exists():
            continue
            
        # Parse gradle.properties
        mc_version = None
        enabled_platforms = []
        
        try:
            with open(gradle_props, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if "=" in line:
                        key, val = line.split("=", 1)
                        key = key.strip()
                        val = val.strip()
                        if key == "minecraft_version":
                            mc_version = val
                        elif key == "enabled_platforms":
                            enabled_platforms = [p.strip() for p in val.split(",") if p.strip()]
        except Exception:
            continue
            
        if not mc_version:
            continue
            
        # Determine Java major version based on MC version
        # Minecraft 1.20.5+ requires Java 21. Older usually use Java 17.
        java_major = 17
        parts = mc_version.split('.')
        if len(parts) >= 2:
            minor = int(parts[1])
            patch = int(parts[2]) if len(parts) > 2 else 0
            if minor > 20 or (minor == 20 and patch >= 5):
                java_major = 21

        # Fallback platforms if none defined
        if not enabled_platforms:
            enabled_platforms = ["fabric", "forge"]
            
        # Format label: "1.21.1  [Fabric + NeoForge]"
        label_plats = " + ".join([(p.capitalize() if p != "neoforge" else "NeoForge") for p in enabled_platforms])
        label = f"{mc_version}  [{label_plats}]"
        
        projects.append({
            "label": label,
            "dir": child,
            "mc_version": mc_version,
            "java_major": java_major,
            "platforms": enabled_platforms,
            "gradle_cmd": "gradlew.bat" if sys.platform == "win32" else "./gradlew"
        })
        
    # Sort projects chronologically by MC version
    def parse_ver(ver_str):
        return tuple(int(x) if x.isdigit() else 0 for x in ver_str.split('.'))
        
    projects.sort(key=lambda p: parse_ver(p["mc_version"]))
    return projects

PROJECTS = scan_projects()
JAVA_REQUIRED = {p["mc_version"]: p["java_major"] for p in PROJECTS}

# ── Java detection ────────────────────────────────────────────────────────────

# Common installation root directories on Windows
WIN_JAVA_ROOTS = [
    Path(r"C:\Program Files\Eclipse Adoptium"),
    Path(r"C:\Program Files\Microsoft"),
    Path(r"C:\Program Files\Java"),
    Path(r"C:\Program Files\BellSoft"),
    Path(r"C:\Program Files\Azul Systems"),
    Path(r"C:\Program Files\Amazon Corretto"),
    Path(r"C:\Program Files\Zulu"),
    Path(os.path.expanduser(r"~\AppData\Local\Programs\Eclipse Adoptium")),
    Path(os.path.expanduser(r"~\AppData\Local\Programs\Microsoft")),
    Path(os.path.expanduser(r"~\.jdks")),
]

def _java_version(java_exe: Path) -> int | None:
    """Return the Java major version number for the given java executable, or None."""
    try:
        result = subprocess.run(
            [str(java_exe), "-version"],
            capture_output=True, text=True, timeout=5
        )
        output = result.stderr or result.stdout
        # "version \"21.0.3\"" or "version \"1.8.0_372\""
        m = re.search(r'version "(?:1\.)?(\d+)', output)
        if m:
            return int(m.group(1))
    except Exception:
        pass
    return None


def detect_java() -> dict[int, Path]:
    """
    Returns a dict mapping Java major version → path to java executable.
    When multiple JDKs of the same major exist, prefers the newest.
    """
    found: dict[int, list[Path]] = {}

    def _register(exe: Path):
        ver = _java_version(exe)
        if ver:
            found.setdefault(ver, []).append(exe)

    # 1. Environment variables: JAVA_HOME, JAVA17_HOME, JAVA21_HOME, etc.
    for key, val in os.environ.items():
        if key == "JAVA_HOME" or re.match(r"JAVA\d+_HOME", key):
            exe = Path(val) / "bin" / ("java.exe" if sys.platform == "win32" else "java")
            if exe.exists():
                _register(exe)

    # 2. PATH
    java_in_path = shutil.which("java")
    if java_in_path:
        _register(Path(java_in_path))

    # 3. Common Windows installation paths
    if sys.platform == "win32":
        java_paths = [
            Path(os.environ.get("PROGRAMFILES", "")) / "Eclipse Adoptium",
            Path(os.environ.get("PROGRAMFILES", "")) / "Java",
            Path(os.environ.get("LOCALAPPDATA", "")) / "Programs" / "Eclipse Adoptium",
            Path.home() / ".jdks",
        ]
        for root in java_paths:
            if not root.exists():
                continue
            for child in root.iterdir():
                exe = child / "bin" / "java.exe"
                if exe.exists():
                    _register(exe)

    # Build result: prefer the first detected path per version (env vars take priority)
    result: dict[int, Path] = {}
    for ver, paths in found.items():
        result[ver] = paths[0]

    return result


def find_best_java(required_major: int, detected: dict[int, Path]) -> tuple[Path | None, bool]:
    """
    Return (path, exact_match).
    Prefers the exact required version; falls back to any higher version.
    """
    if required_major in detected:
        return detected[required_major], True
    # Try any higher version (may still work, with a warning)
    for v in sorted(detected.keys()):
        if v > required_major:
            return detected[v], False
    return None, False


# ── Build logs ────────────────────────────────────────────────────────────────

LOG_DIR = ROOT / "build_logs"
LOG_DIR.mkdir(exist_ok=True)


def _log_path(mc_version: str, platform: str) -> Path:
    ts = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    return LOG_DIR / f"{ts}-{mc_version}-{platform}.log"


# ── Build runner ──────────────────────────────────────────────────────────────

def run_gradle(project: dict, platform: str | None, task: str, java_home: Path) -> bool:
    """
    Run Gradle for the given project and platform.
    Streams output live and writes to a log file.
    Returns True if the build succeeded.
    """
    proj_dir = project["dir"]
    gradle = str(proj_dir / project["gradle_cmd"])

    if not proj_dir.exists():
        print(c(RED, f"  [!] Project directory not found: {proj_dir}"))
        return False

    # Determine Gradle tasks
    if platform:
        if task == "clean":
            tasks = [f"{platform}:clean"]
        elif task == "cleanbuild":
            tasks = [f"{platform}:clean", f"{platform}:build"]
        else:
            tasks = [f"{platform}:build"]
    else:
        if task == "clean":
            tasks = ["clean"]
        elif task == "cleanbuild":
            tasks = ["clean", "build"]
        else:
            tasks = ["build"]

    cmd = [gradle] + tasks + ["--stacktrace"]
    env = os.environ.copy()
    env["JAVA_HOME"] = str(java_home.parent.parent)  # exe is bin/java.exe

    plat_label = platform or "all"
    log_file = _log_path(project["mc_version"], plat_label)

    print()
    print(c(BOLD + CYAN, f"  Building: MC {project['mc_version']} / {plat_label}"))
    print(c(DIM, f"  Command : {' '.join(cmd)}"))
    print(c(DIM, f"  JAVA_HOME: {env['JAVA_HOME']}"))
    print(c(DIM, f"  Log     : {log_file}"))
    print(c(DIM, "  " + "─" * 60))

    try:
        with open(log_file, "w", encoding="utf-8") as log:
            proc = subprocess.Popen(
                cmd, cwd=str(proj_dir), env=env,
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=True, bufsize=1,
                shell=(sys.platform == "win32")
            )
            for line in proc.stdout:
                sys.stdout.write("  " + line)
                log.write(line)
                log.flush()
            proc.wait()

        if proc.returncode == 0:
            print(c(GREEN + BOLD, f"\n  ✓ Build succeeded for MC {project['mc_version']} / {plat_label}"))
            
            # Copy release jars
            try:
                releases_dir = ROOT / "releases"
                releases_dir.mkdir(exist_ok=True)
                copied = 0
                for lib_dir in proj_dir.rglob("build/libs"):
                    if "common" in lib_dir.parts:
                        continue
                    for jar in lib_dir.glob("*.jar"):
                        name = jar.name
                        if "-sources" not in name and "-dev" not in name and "-shadow" not in name and "-common" not in name:
                            parts = name.rsplit("-", 1)
                            if len(parts) == 2:
                                new_name = f"{parts[0]}-{project['mc_version']}-{parts[1]}"
                            else:
                                new_name = f"{project['mc_version']}-{name}"
                            shutil.copy2(jar, releases_dir / new_name)
                            copied += 1
                if copied > 0:
                    print(c(DIM, f"  Copied {copied} release jar(s) to releases/ folder."))
            except Exception as e:
                print(c(RED, f"  Failed to copy release jars: {e}"))
                
            return True
        else:
            print(c(RED + BOLD, f"\n  ✗ Build FAILED for MC {project['mc_version']} / {plat_label}"))
            print(c(RED, f"    See log: {log_file}"))
            return False

    except FileNotFoundError:
        print(c(RED, f"  [!] Gradle wrapper not found in {proj_dir}. Run: gradle wrapper"))
        return False
    except KeyboardInterrupt:
        print(c(YELLOW, "\n  [!] Build interrupted by user."))
        return False


# ── UI helpers ────────────────────────────────────────────────────────────────

def clear():
    os.system("cls" if sys.platform == "win32" else "clear")


def header(detected_java: dict[int, Path]):
    print()
    print(c(BOLD + CYAN, "  ╔══════════════════════════════════════════════════╗"))
    print(c(BOLD + CYAN, "  ║    Vonix Server Utilities — Build Menu           ║"))
    print(c(BOLD + CYAN, "  ╚══════════════════════════════════════════════════╝"))
    print()
    print(c(BOLD, "  Java status:"))
    for major in sorted(detected_java.keys()):
        path = detected_java[major]
        print(c(GREEN, f"    ✓ Java {major}") + c(DIM, f"  →  {path}"))
    for required in sorted({p["java_major"] for p in PROJECTS}):
        if required not in detected_java:
            print(c(RED, f"    ✗ Java {required}") + c(DIM, "  — NOT FOUND (required for some versions)"))
    print()


def choose(prompt: str, options: list[str], allow_zero: bool = True) -> int:
    """
    Present numbered options. Returns 0-based index, or -1 if user chose 0 (exit/back).
    """
    for i, opt in enumerate(options, 1):
        print(f"  {c(YELLOW, str(i))})  {opt}")
    if allow_zero:
        print(f"  {c(DIM, '0')})  {c(DIM, 'Back / Exit')}")
    print()
    while True:
        try:
            raw = input(f"  {c(BOLD, prompt)}: ").strip()
            n = int(raw)
            if allow_zero and n == 0:
                return -1
            if 1 <= n <= len(options):
                return n - 1
            print(c(RED, f"  Enter a number between 0 and {len(options)}."))
        except ValueError:
            print(c(RED, "  Please enter a number."))
        except (EOFError, KeyboardInterrupt):
            print()
            return -1


def yn(prompt: str) -> bool:
    try:
        return input(f"  {prompt} [y/N]: ").strip().lower() in ("y", "yes")
    except (EOFError, KeyboardInterrupt):
        return False


# ── Main menu flow ─────────────────────────────────────────────────────────────

def select_projects() -> list[dict]:
    """Ask user which project(s) to build. Returns list of project dicts."""
    options = [p["label"] for p in PROJECTS] + ["Build ALL versions"]
    print(c(BOLD, "  Select Minecraft version:"))
    idx = choose("Choice", options)
    if idx == -1:
        return []
    if idx == len(PROJECTS):  # "Build ALL"
        return list(PROJECTS)
    return [PROJECTS[idx]]


def select_platform(project: dict) -> list[str | None]:
    """
    Returns list of platform strings to build, or [None] for all.
    """
    platforms = project["platforms"]
    options = [p.capitalize() + " only" for p in platforms] + ["All platforms (default)"]
    print(c(BOLD, "  Select build target:"))
    idx = choose("Choice", options)
    if idx == -1:
        return []
    if idx == len(platforms):  # All
        return [None]
    return [platforms[idx]]


def select_task() -> str | None:
    """Returns task string or None on exit."""
    options = ["Build (default)", "Clean + Build", "Clean only"]
    print(c(BOLD, "  Select task:"))
    idx = choose("Choice", options)
    if idx == -1:
        return None
    return ["build", "cleanbuild", "clean"][idx]


def confirm_java(project: dict, detected: dict[int, Path]) -> Path | None:
    """
    Verify that the required Java is present (or offer fallback).
    Returns the java executable Path to use, or None to skip.
    """
    required = project["java_major"]
    java_exe, exact = find_best_java(required, detected)

    if java_exe is None:
        print(c(RED + BOLD, f"\n  ✗ Java {required} is required for MC {project['mc_version']}"))
        print(c(RED, "    No compatible JDK detected on this system."))
        print(c(DIM, "    Install a JDK and set JAVA_HOME, then re-run."))
        if not yn("  Skip this version and continue?"):
            return None
        return None

    if not exact:
        actual = _java_version(java_exe) or "?"
        print(c(YELLOW + BOLD, f"\n  ⚠ Java {required} not found; will use Java {actual}."))
        print(c(YELLOW, "    This may cause compilation errors on Minecraft " + project["mc_version"]))
        if not yn("  Continue anyway?"):
            return None

    return java_exe


# ── Entry point ───────────────────────────────────────────────────────────────

def main():
    # Enable ANSI on Windows
    if sys.platform == "win32":
        os.system("")

    detected = detect_java()

    while True:
        clear()
        header(detected)

        # Step 1 — choose project(s)
        projects = select_projects()
        if not projects:
            print(c(DIM, "\n  Goodbye.\n"))
            break

        results = []
        global_platforms = None
        global_task = None

        for project in projects:
            print()
            print(c(BOLD + MAGENTA, f"  ── {project['label']} ──────────────────"))

            # Step 2 — choose platform(s)
            if global_platforms is None:
                platforms = select_platform(project)
                if len(projects) > 1 and platforms:
                    global_platforms = platforms
            else:
                platforms = []
                for p in global_platforms:
                    if p is None:
                        platforms = [None]
                        break
                    elif p in project["platforms"]:
                        platforms.append(p)
                    elif p == "forge" and "neoforge" in project["platforms"]:
                        platforms.append("neoforge")
                    elif p == "neoforge" and "forge" in project["platforms"]:
                        platforms.append("forge")

            if not platforms:
                continue

            # Step 3 — choose task
            if global_task is None:
                task = select_task()
                if len(projects) > 1 and task:
                    global_task = task
            else:
                task = global_task

            if task is None:
                continue

            # Step 4 — Java check
            java_exe = confirm_java(project, detected)
            if java_exe is None:
                continue

            # Step 5 — build
            for plat in platforms:
                ok = run_gradle(project, plat, task, java_exe)
                results.append((project["mc_version"], plat or "all", ok))

        # Summary
        if results:
            print()
            print(c(BOLD + CYAN, "  ── Build Summary ────────────────────────────"))
            for mc, plat, ok in results:
                status = c(GREEN, "✓ PASS") if ok else c(RED, "✗ FAIL")
                print(f"    {status}  MC {mc} / {plat}")
            print()

        if not yn("  Run another build?"):
            print(c(DIM, "\n  Goodbye.\n"))
            break


if __name__ == "__main__":
    main()
