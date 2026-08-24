#!/usr/bin/env python3
"""RF Mission Stack 6.2 — Instalator cross-platform"""
import os, sys, platform, subprocess, zipfile, shutil, argparse
from pathlib import Path

VERSION = "6.2"
ZIP_NAME = "rf-mission-stack-6.2.zip"

def detect():
    s = platform.system()
    if "ANDROID_ROOT" in os.environ or "TERMUX_VERSION" in os.environ:
        return "android"
    return {"Windows":"windows","Darwin":"macos","Linux":"linux"}.get(s,"linux")

def run(cmd, **kw):
    print(f"  $ {cmd}")
    return subprocess.run(cmd, shell=True, **kw)

def check_env(project_dir):
    env = project_dir / ".env"
    if not env.exists():
        print("[!] Brak pliku .env — generuję teraz...")
        run(f'python3 "{project_dir / "gen_env.py"}"')
        print("[!] Ustaw FCM_SERVER_KEY w .env jeśli potrzebujesz push notifications.")

def main():
    p = argparse.ArgumentParser(description=f"RF Mission Stack {VERSION} Installer")
    p.add_argument("--dir", default="rf-mission-stack", help="Katalog instalacji")
    p.add_argument("--no-start", action="store_true", help="Tylko rozpakuj, nie uruchamiaj")
    p.add_argument("--android-studio", action="store_true", help="Pokaż instrukcje Android Studio")
    args = p.parse_args()

    plat = detect()
    install_dir = Path(args.dir).resolve()
    print(f"\n=== RF Mission Stack {VERSION} — Instalator ===")
    print(f"Platforma: {plat} | Cel: {install_dir}\n")

    # Rozpakuj projekt z wbudowanego ZIP
    here = Path(__file__).parent
    zip_path = here / ZIP_NAME
    if zip_path.exists():
        install_dir.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(zip_path) as z:
            z.extractall(install_dir)
        print(f"[OK] Rozpakowano do {install_dir}")
    else:
        print(f"[!] Nie znaleziono {ZIP_NAME} — zakładam że pliki już istnieją w {install_dir}")

    project_dir = install_dir / "project"
    if not project_dir.exists():
        project_dir = install_dir

    check_env(project_dir)

    if args.android_studio:
        print("""
=== Instrukcje budowania APK ===
1. Otwórz Android Studio
2. File → Open → wybierz katalog: project/android/
3. Build → Generate Signed Bundle/APK → APK
4. Zainstaluj na urządzeniu: adb install app-release.apk
   (lub skopiuj APK i zainstaluj ręcznie)
""")
        return

    if args.no_start:
        print("[OK] Instalacja zakończona (--no-start)")
        return

    if plat in ("linux", "macos"):
        if shutil.which("docker"):
            print("[docker] Uruchamiam stack...")
            run(f'cd "{project_dir}" && docker compose up -d')
        else:
            print("[pip] Docker niedostępny — uruchamiam API lokalnie...")
            run(f'pip3 install -r "{project_dir}/api/requirements.txt" -q')
            run(f'cd "{project_dir}" && python3 gen_env.py 2>/dev/null; uvicorn api.main:app --reload &')
    elif plat == "windows":
        bat = project_dir / "start.bat"
        bat.write_text(f"@echo off\ncd /d \"{project_dir}\"\ndocker compose up -d\npause\n")
        stop = project_dir / "stop.bat"
        stop.write_text(f"@echo off\ncd /d \"{project_dir}\"\ndocker compose down\npause\n")
        print(f"[OK] Utworzono start.bat i stop.bat w {project_dir}")
        run(f'start "" "{bat}"')
    elif plat == "android":
        print("[Termux] Instaluję zależności...")
        run("pkg install -y python redis")
        run(f'pip install -r "{project_dir}/api/requirements.txt" -q')
        print("[Termux] Uruchamiam Redis i API...")
        run("redis-server --daemonize yes")
        run(f'cd "{project_dir}" && uvicorn api.main:app --host 0.0.0.0 --port 8000 &')

    print(f"""
=== RF Mission Stack {VERSION} uruchomiony! ===
API:        http://localhost:8000
Docs:       http://localhost:8000/docs
Grafana:    http://localhost:3000  (admin/admin)
WebSocket:  ws://localhost:8000/ws
""")

if __name__ == "__main__":
    main()
