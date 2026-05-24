#!/usr/bin/env python3
"""
Vonix Server Utilities — Build Script
Minecraft 1.21.1 | Fabric + NeoForge
"""

import os
import sys
import glob
import shutil
import subprocess
from pathlib import Path

try:
    from rich.console import Console
    from rich.panel import Panel
    from rich.table import Table
    from rich.live import Live
    from rich.spinner import Spinner
    from rich.text import Text
    from rich import box
    from rich.prompt import Prompt
    from rich.rule import Rule
    from rich.columns import Columns
except ImportError:
    print("Installing required dependency: rich")
    subprocess.run([sys.executable, "-m", "pip", "install", "rich", "-q"], check=True)
    from rich.console import Console
    from rich.panel import Panel
    from rich.table import Table
    from rich.live import Live
    from rich.spinner import Spinner
    from rich.text import Text
    from rich import box
    from rich.prompt import Prompt
    from rich.rule import Rule
    from rich.columns import Columns

console = Console()

ROOT_DIR = Path(__file__).parent.resolve()
BUILDS_DIR = ROOT_DIR / "Builds"
GRADLEW = ROOT_DIR / "gradlew.bat"

# ── Java 21 detection ─────────────────────────────────────────────────────────

def find_java21() -> Path | None:
    """Search known Adoptium and fallback locations for a JDK 21 install."""
    candidates = []

    # Adoptium per-user install (primary — where this machine has it)
    adoptium_local = Path(os.environ.get("LOCALAPPDATA", ""), "Programs", "Eclipse Adoptium")
    if adoptium_local.exists():
        candidates += sorted(adoptium_local.glob("jdk-21*"), reverse=True)

    # Adoptium system-wide
    for base in [
        Path("C:/Program Files/Eclipse Adoptium"),
        Path("C:/Program Files/Java"),
    ]:
        if base.exists():
            candidates += sorted(base.glob("jdk-21*"), reverse=True)

    # Env vars
    for var in ("JAVA_21_HOME", "JAVA_21"):
        val = os.environ.get(var)
        if val:
            candidates.insert(0, Path(val))

    for path in candidates:
        java_exe = path / "bin" / "java.exe"
        if java_exe.exists():
            return path

    return None


# ── Helpers ───────────────────────────────────────────────────────────────────

def make_env(java_home: Path) -> dict:
    env = os.environ.copy()
    env["JAVA_HOME"] = str(java_home)
    env["PATH"] = str(java_home / "bin") + os.pathsep + env.get("PATH", "")
    return env


def prepare_builds_dir():
    if BUILDS_DIR.exists():
        shutil.rmtree(BUILDS_DIR)
    BUILDS_DIR.mkdir()


def collect_jars(platform: str) -> list[Path]:
    """Copy production JARs (skip -dev / -dev-shadow / -sources / -javadoc)."""
    skip = {"-dev.jar", "-dev-shadow.jar", "-sources.jar", "-javadoc.jar"}
    libs = ROOT_DIR / platform / "build" / "libs"
    collected = []
    if not libs.exists():
        return collected
    for jar in libs.glob("*.jar"):
        if any(jar.name.endswith(s) for s in skip):
            continue
        dest = BUILDS_DIR / f"[{platform}] {jar.name}"
        shutil.copy2(jar, dest)
        collected.append(dest)
    return collected


def run_gradle(tasks: list[str], java_home: Path) -> bool:
    """Run gradlew with the given tasks, streaming output live. Returns True on success."""
    cmd = [str(GRADLEW)] + tasks + ["--build-cache", "--configure-on-demand"]
    if len(tasks) > 1:
        cmd.append("--parallel")

    env = make_env(java_home)

    console.print()
    console.rule("[bold cyan]Gradle Output")
    console.print()

    proc = subprocess.Popen(
        cmd,
        cwd=ROOT_DIR,
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        bufsize=1,
        shell=(sys.platform == "win32")
    )

    task_style = {
        "> Task": "cyan",
        "FAILED":  "bold red",
        "BUILD SUCCESSFUL": "bold green",
        "BUILD FAILED":     "bold red",
        "[ERROR]":  "red",
        "[WARN]":   "yellow",
        "[INFO]":   "dim",
        "warning:": "yellow",
        "error:":   "red",
    }

    for line in proc.stdout:
        line = line.rstrip()
        if not line:
            continue
        color = None
        for keyword, style in task_style.items():
            if keyword in line:
                color = style
                break
        if color:
            console.print(f"  [{'dim' if color == 'dim' else color}]{line}[/]")
        else:
            console.print(f"  {line}")

    proc.wait()
    console.print()
    return proc.returncode == 0


def print_summary(collected: list[Path], success: bool):
    console.print()
    console.rule("[bold]Build Summary")

    if success and collected:
        table = Table(box=box.ROUNDED, show_header=True, header_style="bold magenta")
        table.add_column("Platform", style="cyan", no_wrap=True)
        table.add_column("File", style="white")
        table.add_column("Size", style="green", justify="right")
        for jar in collected:
            platform = jar.name.split("]")[0].lstrip("[")
            size_kb = jar.stat().st_size // 1024
            table.add_row(platform, jar.name, f"{size_kb:,} KB")
        console.print(table)
        console.print(f"\n[dim]Output folder:[/] [cyan]{BUILDS_DIR}[/]")
    elif success:
        console.print("[yellow]Build succeeded but no JARs were collected.[/]")
    else:
        console.print("[bold red]Build failed. Check the output above for errors.[/]")


# ── Menu ──────────────────────────────────────────────────────────────────────

MENU_OPTIONS = {
    "1": ("Build Fabric + NeoForge", [":fabric:build", ":neoforge:build"], ["fabric", "neoforge"]),
    "2": ("Build Fabric only",        [":fabric:build"],                    ["fabric"]),
    "3": ("Build NeoForge only",       [":neoforge:build"],                  ["neoforge"]),
    "4": ("Clean all build dirs",      None,                                 None),
    "5": ("Clean Builds folder only",  None,                                 None),
    "0": ("Exit",                      None,                                 None),
}


def print_menu(java_home: Path | None):
    console.print(Panel.fit(
        Text.assemble(
            ("Vonix Server Utilities", "bold white"), "\n",
            ("Minecraft 1.21.1  •  Fabric + NeoForge", "dim"),
        ),
        title="[bold cyan]Build Menu[/]",
        border_style="cyan",
    ))

    java_str = str(java_home) if java_home else "[bold red]NOT FOUND — builds will fail[/]"
    output_str = str(BUILDS_DIR)

    info = Table.grid(padding=(0, 2))
    info.add_column(style="dim")
    info.add_column()
    info.add_row("Java 21", java_str)
    info.add_row("Output ", output_str)
    console.print(info)
    console.print()

    table = Table(box=box.SIMPLE, show_header=False, padding=(0, 1))
    table.add_column(style="bold yellow", no_wrap=True, width=4)
    table.add_column(style="white")
    for key, (label, *_) in MENU_OPTIONS.items():
        color = "red" if key == "0" else "yellow"
        table.add_row(f"[{color}][{key}][/{color}]", label)
    console.print(table)
    console.print()


def do_clean_all():
    for platform in ("common", "fabric", "neoforge"):
        d = ROOT_DIR / platform / "build"
        if d.exists():
            shutil.rmtree(d)
            console.print(f"  [dim]Cleaned[/] {d}")
    if BUILDS_DIR.exists():
        shutil.rmtree(BUILDS_DIR)
        console.print(f"  [dim]Cleaned[/] {BUILDS_DIR}")
    console.print("[green]Done.[/]")


def do_clean_builds():
    if BUILDS_DIR.exists():
        shutil.rmtree(BUILDS_DIR)
        console.print(f"[green]Cleaned:[/] {BUILDS_DIR}")
    else:
        console.print("[dim]Builds folder does not exist.[/]")


def main():
    java_home = find_java21()

    while True:
        console.clear()
        print_menu(java_home)

        choice = Prompt.ask("[bold cyan]Choice[/]", choices=list(MENU_OPTIONS.keys()), show_choices=False)
        label, tasks, platforms = MENU_OPTIONS[choice]

        if choice == "0":
            console.print("[dim]Goodbye.[/]")
            break

        console.print(f"\n[bold cyan]» {label}[/]")

        if choice == "4":
            do_clean_all()
        elif choice == "5":
            do_clean_builds()
        else:
            if not java_home:
                console.print("[bold red]Java 21 not found. Cannot build.[/]")
                Prompt.ask("\nPress Enter to return to menu")
                continue

            prepare_builds_dir()
            success = run_gradle(tasks, java_home)

            collected = []
            if success:
                for p in platforms:
                    collected += collect_jars(p)

            print_summary(collected, success)

        Prompt.ask("\n[dim]Press Enter to return to menu[/]")


if __name__ == "__main__":
    main()
