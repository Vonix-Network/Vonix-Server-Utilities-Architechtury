import os
import sys
import json
import asyncio
import subprocess
from pathlib import Path

# Determine directories correctly
if getattr(sys, 'frozen', False):
    WEB_DIR = Path(sys.argv[0]).parent.resolve()
else:
    WEB_DIR = Path(__file__).parent.resolve()

ROOT_DIR = WEB_DIR.parent

# Add root to path so we can import build_menu.py
if str(ROOT_DIR) not in sys.path:
    sys.path.insert(0, str(ROOT_DIR))

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse
import uvicorn
import build_menu as bm

app = FastAPI()

# Mount static files
app.mount("/static", StaticFiles(directory=str(WEB_DIR)), name="static")

@app.get("/")
async def get_index():
    return FileResponse(str(WEB_DIR / "index.html"))

@app.get("/api/projects")
async def get_projects():
    # Rescan folders dynamically on every page load!
    bm.PROJECTS = bm.scan_projects()
    bm.JAVA_REQUIRED = {p["mc_version"]: p["java_major"] for p in bm.PROJECTS}
    # Return serializable info
    return [{"label": p["label"], "mc_version": p["mc_version"], "platforms": p["platforms"]} for p in bm.PROJECTS]

@app.websocket("/api/build")
async def websocket_build(websocket: WebSocket):
    await websocket.accept()
    
    # Send helper
    async def send_log(msg: str, color: str = ""):
        await websocket.send_text(json.dumps({"type": "log", "message": msg, "color": color}))

    try:
        data = await websocket.receive_text()
        req = json.loads(data)
        
        project_idx = req.get("project_idx")
        platform_req = req.get("platform")
        task_req = req.get("task")

        # Determine projects
        if project_idx == "all":
            projects_to_build = bm.PROJECTS
        else:
            projects_to_build = [bm.PROJECTS[int(project_idx)]]

        detected_java = bm.detect_java()

        # Pre-calculate total builds for the progress bar
        total_builds = 0
        for project in projects_to_build:
            if platform_req == "all":
                total_builds += len(project["platforms"])
            elif platform_req in project["platforms"]:
                total_builds += 1
            elif platform_req == "forge" and "neoforge" in project["platforms"]:
                total_builds += 1
            elif platform_req == "neoforge" and "forge" in project["platforms"]:
                total_builds += 1

        current_build = 0

        # Build loop
        for project in projects_to_build:
            await send_log(f"\n── {project['label']} ──────────────────", "magenta bold")

            # Determine platforms
            platforms = []
            if platform_req == "all":
                platforms = [None]
            elif platform_req in project["platforms"]:
                platforms.append(platform_req)
            elif platform_req == "forge" and "neoforge" in project["platforms"]:
                platforms.append("neoforge")
            elif platform_req == "neoforge" and "forge" in project["platforms"]:
                platforms.append("forge")

            if not platforms:
                await send_log(f"Skipping {project['label']} (Platform {platform_req} not supported)", "yellow")
                continue

            java_exe, exact = bm.find_best_java(project["java_major"], detected_java)
            if not java_exe:
                await send_log(f"Java {project['java_major']} not found for {project['mc_version']}. Skipping.", "red bold")
                continue

            if not exact:
                await send_log(f"Warning: Exact Java not found. Using fallback.", "yellow")

            # Run Gradle processes
            for plat in platforms:
                plat_label = plat or "all"
                await send_log(f"\nBuilding: MC {project['mc_version']} / {plat_label}", "cyan bold")
                
                gradle = str(project["dir"] / project["gradle_cmd"])
                
                if plat:
                    if task_req == "clean": tasks = [f"{plat}:clean"]
                    elif task_req == "cleanbuild": tasks = [f"{plat}:clean", f"{plat}:build"]
                    else: tasks = [f"{plat}:build"]
                else:
                    if task_req == "clean": tasks = ["clean"]
                    elif task_req == "cleanbuild": tasks = ["clean", "build"]
                    else: tasks = ["build"]

                cmd_str = f'"{gradle}" ' + " ".join(tasks) + " --stacktrace"
                env = os.environ.copy()
                env["JAVA_HOME"] = str(java_exe.parent.parent)

                # Execute asynchronously
                process = await asyncio.create_subprocess_shell(
                    cmd_str,
                    cwd=str(project["dir"]),
                    env=env,
                    stdout=asyncio.subprocess.PIPE,
                    stderr=asyncio.subprocess.STDOUT
                )

                while True:
                    line = await process.stdout.readline()
                    if not line:
                        break
                    line_str = line.decode("utf-8", errors="replace").rstrip()
                    
                    # Basic color mapping for Gradle output
                    color = "dim"
                    if "BUILD SUCCESSFUL" in line_str: color = "green bold"
                    elif "FAILED" in line_str or "error:" in line_str: color = "red bold"
                    elif "warning:" in line_str: color = "yellow"
                    elif "> Task" in line_str: color = "cyan"

                    await send_log("  " + line_str, color)

                await process.wait()

                if process.returncode == 0:
                    await send_log(f"✓ Build succeeded for MC {project['mc_version']} / {plat_label}", "green bold")
                    
                    # Copy release jars
                    try:
                        import shutil
                        releases_dir = ROOT_DIR / "releases"
                        releases_dir.mkdir(exist_ok=True)
                        
                        copied = 0
                        for lib_dir in project["dir"].rglob("build/libs"):
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
                            await send_log(f"  Copied {copied} release jar(s) to releases/ folder.", "cyan dim")
                    except Exception as e:
                        await send_log(f"  Failed to copy release jars: {e}", "red")
                    
                    # Update progress
                    current_build += 1
                    percent = (current_build / total_builds) * 100 if total_builds > 0 else 100
                    await websocket.send_text(json.dumps({"type": "progress", "percent": percent}))
                else:
                    await send_log(f"✗ Build FAILED for MC {project['mc_version']} / {plat_label}", "red bold")
                    await websocket.send_text(json.dumps({"type": "status", "status": "error", "message": f"Build failed for {project['mc_version']}"}))
                    return

        # Done
        await websocket.send_text(json.dumps({"type": "status", "status": "done"}))

    except WebSocketDisconnect:
        pass
    except Exception as e:
        await websocket.send_text(json.dumps({"type": "status", "status": "error", "message": str(e)}))

import threading
import time

try:
    from PyQt6.QtCore import Qt, QUrl
    from PyQt6.QtWidgets import QApplication, QMainWindow, QVBoxLayout, QWidget, QPushButton, QHBoxLayout, QLabel
    from PyQt6.QtWebEngineWidgets import QWebEngineView
    PYQT_AVAILABLE = True
except ImportError:
    PYQT_AVAILABLE = False

if PYQT_AVAILABLE:
    class TitleBar(QWidget):
        def __init__(self, parent):
            super().__init__(parent)
            self.parent = parent
            self.setAttribute(Qt.WidgetAttribute.WA_StyledBackground, True)
            self.setFixedHeight(35)
            self.setStyleSheet("background-color: #0B032D; color: white;")
            
            layout = QHBoxLayout(self)
            layout.setContentsMargins(15, 0, 0, 0)
            layout.setSpacing(0)
            
            self.title = QLabel("Vonix Server Utilities - Build Manager")
            self.title.setStyleSheet("font-weight: 600; font-family: 'Inter', sans-serif; font-size: 13px;")
            layout.addWidget(self.title)
            
            layout.addStretch()
            
            self.btn_min = QPushButton("—")
            self.btn_min.setFixedSize(45, 35)
            self.btn_min.setStyleSheet("QPushButton { border: none; background: transparent; color: white; font-weight: bold; } QPushButton:hover { background: rgba(255,255,255,0.1); }")
            self.btn_min.clicked.connect(self.parent.showMinimized)
            layout.addWidget(self.btn_min)
            
            self.btn_close = QPushButton("✕")
            self.btn_close.setFixedSize(45, 35)
            self.btn_close.setStyleSheet("QPushButton { border: none; background: transparent; color: white; font-weight: bold; } QPushButton:hover { background: #E81123; }")
            self.btn_close.clicked.connect(self.parent.close)
            layout.addWidget(self.btn_close)
            
            self.start_pos = None

        def mousePressEvent(self, event):
            if event.button() == Qt.MouseButton.LeftButton:
                self.start_pos = event.globalPosition().toPoint()

        def mouseMoveEvent(self, event):
            if self.start_pos is not None:
                delta = event.globalPosition().toPoint() - self.start_pos
                self.parent.move(self.parent.pos() + delta)
                self.start_pos = event.globalPosition().toPoint()

        def mouseReleaseEvent(self, event):
            self.start_pos = None

    class FramelessBrowser(QMainWindow):
        def __init__(self):
            super().__init__()
            self.setWindowFlags(Qt.WindowType.FramelessWindowHint)
            self.resize(1050, 750)
            
            central_widget = QWidget()
            self.setCentralWidget(central_widget)
            layout = QVBoxLayout(central_widget)
            layout.setContentsMargins(0, 0, 0, 0)
            layout.setSpacing(0)
            
            self.title_bar = TitleBar(self)
            layout.addWidget(self.title_bar)
            
            self.browser = QWebEngineView()
            self.browser.setUrl(QUrl("http://127.0.0.1:8000"))
            layout.addWidget(self.browser)

def run_server():
    uvicorn.run("gui-build-menu:app", host="127.0.0.1", port=8000)

if __name__ == "__main__":
    print("Starting Vonix Server Utilities Native Web GUI...")
    if PYQT_AVAILABLE:
        threading.Thread(target=run_server, daemon=True).start()
        time.sleep(1) # wait for uvicorn to start
        app = QApplication(sys.argv)
        win = FramelessBrowser()
        win.show()
        app.exec() # Main thread blocked by UI, exiting UI will kill the script (and daemon thread)
    else:
        def open_browser():
            time.sleep(1.5)
            subprocess.Popen(['start', 'msedge', '--app=http://127.0.0.1:8000'], shell=True)
        threading.Thread(target=open_browser, daemon=True).start()
        run_server()
