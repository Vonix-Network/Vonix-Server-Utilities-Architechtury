# Vonix Build Menu — Native QML Rewrite Plan

Replace the webview-based app (PyQt6 + WebEngine + FastAPI) with a fully native **PySide6 + QML** app
that renders using Qt's own GPU-accelerated scene graph. No browser engine, no HTTP server,
no WebSockets — just a fast, native, premium-feeling desktop application.

## Why QML over the current stack

| | Current (PyQt6 + WebEngine + FastAPI) | New (PySide6 + QML) |
|---|---|---|
| **Renderer** | Bundled Chromium (~200MB) | Qt Scene Graph (GPU, no browser) |
| **Feels like** | Browser in a frame | True native app |
| **Server needed?** | Yes — uvicorn on :8000 | No server whatsoever |
| **JS bridge** | WebSocket protocol | Direct Python ↔ QML property bindings |
| **EXE size (Nuitka)** | ~200-250 MB | ~30-60 MB |
| **Nuitka support** | "Imperfect" (Qt plugin warns) | First-class (`--enable-plugin=pyside6`) |
| **Dependencies** | fastapi, uvicorn, PyQt6, PyQt6-WebEngine | PySide6 only |
| **Animations** | CSS transitions | QML `Behavior`, `NumberAnimation` — GPU-native |

---

## Folder Structure

New folder: `Vonix Build Menu Native/`
(Separate from the webview version — the old `Vonix Build Menu/` stays untouched)

```
Vonix Build Menu Native/
├── main.py              ← Entry point + QML engine setup + Python backend
├── build_backend.py     ← All build logic from build_menu.py merged in (standalone, no external deps)
├── qml/
│   └── Main.qml         ← Full UI — layout, controls, terminal, progress bar
├── requirements.txt
└── README.md
```

> build_menu.py logic is merged directly into build_backend.py — no external dependency on
> the parent build_menu.py. The app is fully self-contained.

---

## Path Detection (dual-mode)

`main.py` detects at startup:
- **Root mode**: script/exe is at the architectury root → scan `ROOT_DIR` directly
- **Subfolder mode**: running from inside `Vonix Build Menu Native/` → `ROOT_DIR = parent`

Detection: if `build_backend.py` (or a known marker) exists in the same dir as the exe → root mode.

---

## Architecture

### `main.py`
- Creates `QGuiApplication` (no widget system)
- Creates `QQmlApplicationEngine`
- Registers `BuildController(QObject)` into QML context as `controller`
- Loads `qml/Main.qml`

### `BuildController(QObject)` — signals & slots
- `@Signal` / `@Property`: `projects`, `isBuilding`, `progress`, `logLines`
- `@Slot`: `startBuild(projectIdx, platform, task)`, `scanProjects()`
- Runs Gradle in a `QThread` worker — streams output via Qt signals

### `qml/Main.qml`
Recreates the full current UI natively:
- Dark purple/magenta gradient background (`Rectangle` + `LinearGradient`)
- Glow blur effects (`RadialGradient`)
- Frameless window (`Qt.FramelessWindowHint`) + custom draggable title bar
- Left pane: `ComboBox` controls + Build button with hover animations
- Right pane: `ListView` / `TextArea` terminal (monospace, dark, pink scrollbar)
- Progress bar: `Rectangle` with `Behavior on width { NumberAnimation }` — GPU-smooth
- Inter font via `FontLoader`

---

## EXE Build Command

```powershell
python -m nuitka `
  --onefile `
  --enable-plugin=pyside6 `
  --include-data-dir=qml=qml `
  --windows-console-mode=disable `
  --output-filename=VonixBuildMenu.exe `
  main.py
```

PySide6's Nuitka plugin is fully supported. Single EXE, ~40-60MB, no external files.

---

## Requirements

```
PySide6>=6.5.0
rich>=13.0.0
nuitka>=4.0.0  # for building EXE
```

---

## Status: PLANNED — not yet implemented
