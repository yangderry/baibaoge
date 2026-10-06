#!/usr/bin/env python3
"""Baibaoge Backup Service — Dockerized Flask sync backup server for QNAP TS-551."""

import hashlib
import logging
import os
import time
from pathlib import Path

from flask import Flask, jsonify, request, send_from_directory
from werkzeug.utils import secure_filename

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
BACKUP_DIR = Path(os.environ.get("BACKUP_DIR", "/backup"))
MAX_BACKUPS = int(os.environ.get("MAX_BACKUPS", 20))
PORT = int(os.environ.get("PORT", 5000))

# Ensure backup directory exists
BACKUP_DIR.mkdir(parents=True, exist_ok=True)

# Logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("baibaoge-backup")

app = Flask(__name__)


def _list_backup_files():
    """Return a list of regular files in BACKUP_DIR sorted by mtime descending."""
    files = [f for f in BACKUP_DIR.iterdir() if f.is_file()]
    files.sort(key=lambda f: f.stat().st_mtime, reverse=True)
    return files


def _file_md5(filepath: Path) -> str:
    """Compute MD5 hex digest of a file."""
    h = hashlib.md5()
    with filepath.open("rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


def _cleanup_old_backups():
    """Keep only the MAX_BACKUPS most recent backup files."""
    files = _list_backup_files()
    if len(files) <= MAX_BACKUPS:
        return
    for old_file in files[MAX_BACKUPS:]:
        try:
            old_file.unlink()
            logger.info("Deleted old backup: %s", old_file.name)
        except OSError as exc:
            logger.warning("Failed to delete %s: %s", old_file.name, exc)


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@app.route("/health", methods=["GET"])
def health():
    """Health check."""
    return jsonify({"status": "ok", "backup_dir": str(BACKUP_DIR), "max_backups": MAX_BACKUPS})


@app.route("/latest-version", methods=["GET"])
def latest_version():
    """Return metadata of the most recent backup file."""
    files = _list_backup_files()
    if not files:
        return jsonify({"exists": False, "message": "No backups found"}), 404

    latest = files[0]
    stat = latest.stat()
    return jsonify(
        {
            "exists": True,
            "filename": latest.name,
            "size": stat.st_size,
            "md5": _file_md5(latest),
            "created_at": int(stat.st_mtime),
            "created_at_iso": time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(stat.st_mtime)),
        }
    )


@app.route("/upload", methods=["POST"])
def upload():
    """Upload a backup file with MD5 integrity check."""
    if "file" not in request.files:
        return jsonify({"success": False, "error": "Missing 'file' in multipart form"}), 400

    uploaded = request.files["file"]
    if not uploaded.filename:
        return jsonify({"success": False, "error": "Empty filename"}), 400

    expected_md5 = request.form.get("md5", "").strip().lower()
    if not expected_md5:
        return jsonify({"success": False, "error": "Missing 'md5' field"}), 400

    # Sanitize and build a unique filename: timestamp + original name
    safe_name = secure_filename(uploaded.filename)
    timestamp = time.strftime("%Y%m%d_%H%M%S")
    saved_name = f"{timestamp}_{safe_name}"
    dest_path = BACKUP_DIR / saved_name

    # Save file
    uploaded.save(str(dest_path))

    # Verify MD5
    actual_md5 = _file_md5(dest_path)
    if actual_md5 != expected_md5:
        try:
            dest_path.unlink()
        except OSError:
            pass
        return (
            jsonify(
                {
                    "success": False,
                    "error": "MD5 mismatch",
                    "expected": expected_md5,
                    "actual": actual_md5,
                }
            ),
            400,
        )

    # Rolling cleanup
    _cleanup_old_backups()

    logger.info("Uploaded backup: %s (size=%d, md5=%s)", saved_name, dest_path.stat().st_size, actual_md5)
    return jsonify(
        {
            "success": True,
            "filename": saved_name,
            "size": dest_path.stat().st_size,
            "md5": actual_md5,
        }
    )


@app.route("/download", methods=["GET"])
@app.route("/download/<filename>", methods=["GET"])
def download(filename=None):
    """Download a backup file; without a filename, the latest backup is served."""
    if filename is None:
        files = _list_backup_files()
        if not files:
            return jsonify({"success": False, "error": "No backups found"}), 404
        safe_name = files[0].name
    else:
        safe_name = secure_filename(filename)
        file_path = BACKUP_DIR / safe_name
        if not file_path.is_file():
            return jsonify({"success": False, "error": "File not found"}), 404
    return send_from_directory(str(BACKUP_DIR), safe_name, as_attachment=True)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    logger.info("Baibaoge Backup Service starting on port %d, backup_dir=%s", PORT, BACKUP_DIR)
    app.run(host="0.0.0.0", port=PORT)
