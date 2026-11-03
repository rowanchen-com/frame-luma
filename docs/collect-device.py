#!/usr/bin/env python3
"""Collect a local, read-only adaptation report. Python 3.9+, standard library only."""

import argparse
import datetime
import ipaddress
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys


ROOT = Path(__file__).resolve().parent.parent
PROPERTIES = (
    "ro.product.manufacturer", "ro.product.brand", "ro.product.model",
    "ro.product.device", "ro.product.name", "ro.build.version.release",
    "ro.build.version.sdk", "ro.build.version.security_patch", "ro.build.display.id",
    "ro.build.version.incremental", "ro.build.fingerprint", "ro.board.platform",
    "ro.hardware", "ro.soc.manufacturer", "ro.soc.model", "ro.product.cpu.abilist",
    "ro.config.low_ram",
)
INTERFACE_WORDS = re.compile(r"tv|display|factory|pixel|color|capture|picture|panel|ambient|light", re.I)
USB_FIELDS = re.compile(
    r"\b(mVendorId|vendor_id|vendorId|mProductId|product_id|productId|"
    r"mClass|class|mSubclass|subclass|mProtocol|protocol|mId|id|"
    r"mAddress|address|mDirection|direction|mType|type|mMaxPacketSize|"
    r"max_packet_size|mInterval|interval)\s*[=:]\s*(0x[0-9a-f]+|[0-9]+)\b", re.I
)


def scrub(text, mask_addresses=True):
    text = re.sub(r"\b(?:[0-9a-f]{2}:){5}[0-9a-f]{2}\b", "<mac>", text, flags=re.I)
    if mask_addresses:
        text = re.sub(r"(?<![\w.])(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?![\w.])", "<ip>", text)

        def ipv6(match):
            try:
                return "<ip>" if ipaddress.ip_address(match.group(0).split("%", 1)[0]).version == 6 else match.group(0)
            except ValueError:
                return match.group(0)

        text = re.sub(r"(?<![\w:.])(?:[0-9a-f]{0,4}:){2,}[0-9a-f:]{0,4}(?:%[\w]+)?(?![\w:.])", ipv6, text, flags=re.I)
    text = re.sub(r"(?i)(https?://)[^\s]+", r"\1<url>", text)
    text = re.sub(
        r"(?i)\b(m?serial(?:_?number)?|serialno|sn|ssid|bssid|token|password|passwd|"
        r"account(?:_id)?|user_id|android_id)\b(\s*[:=]\s*)[^\s,;]+",
        r"\1\2<redacted>", text,
    )
    text = re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f]", "", text)
    return text.replace("```", "''' ").strip()


class Adb:
    def __init__(self, executable, serial, timeout):
        self.executable, self.serial, self.timeout = executable, serial, timeout

    def query(self, *arguments):
        try:
            result = subprocess.run(
                [self.executable, "-s", self.serial, *arguments],
                stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                encoding="utf-8", errors="replace", timeout=self.timeout,
            )
        except subprocess.TimeoutExpired:
            return "[unavailable: command timed out]"
        except OSError:
            return "[unavailable: could not execute adb]"
        if result.returncode:
            detail = scrub(result.stderr or result.stdout)[:240]
            if result.stdout.strip():
                return result.stdout.replace("\r", "").strip() + "\n[partial: command returned " + str(result.returncode) + "; " + detail + "]"
            return "[unavailable: " + (detail or "command rejected") + "]"
        return result.stdout.replace("\r", "").strip()

    def shell(self, *arguments):
        return self.query("shell", shlex.join(arguments))


def select_lines(value, predicate, limit=80):
    if value.startswith("[unavailable:"):
        return value
    lines = value.splitlines()
    selected = list(dict.fromkeys(line.strip() for line in lines if predicate(line) and not line.startswith("[partial:")))
    result = "\n".join(selected[:limit]) or "(no matching entries reported)"
    if len(selected) > limit:
        result += "\n[additional matching entries omitted]"
    result += "".join("\n" + line for line in lines if line.startswith("[partial:"))
    return result


def display_summary(value):
    if value.startswith("[unavailable:"):
        return value
    sizes = re.findall(r"\b([0-9]{3,5})\s*[xX]\s*([0-9]{3,5})\b", value)
    sizes += re.findall(r"\bwidth[=:]\s*([0-9]{3,5}),?\s*height[=:]\s*([0-9]{3,5})", value, re.I)
    sizes = list(dict.fromkeys(f"{w}x{h}" for w, h in sizes))[:20]
    rates = list(dict.fromkeys(re.findall(r"\b(?:fps|refreshRate)[=: ]+([0-9]+(?:\.[0-9]+)?)", value)))[:20]
    return "Dimensions mentioned: " + (", ".join(sizes) or "not parsed") + "\nRefresh rates: " + (", ".join(rates) or "not parsed")


def usb_summary(value):
    if value.startswith("[unavailable:"):
        return value
    rows, identities = [], []
    vendor = None
    for line in value.splitlines():
        fields = USB_FIELDS.findall(line)
        if fields:
            rows.append(" ".join(f"{key}={number}" for key, number in fields))
            for key, number in fields:
                normalized = key.lower().replace("_", "").removeprefix("m")
                if normalized == "vendorid":
                    vendor = int(number, 16 if number.lower().startswith("0x") else 10)
                elif normalized == "productid" and vendor is not None:
                    product = int(number, 16 if number.lower().startswith("0x") else 10)
                    identities.append(f"VID=0x{vendor:04x} ({vendor}) PID=0x{product:04x} ({product})")
    if not rows:
        return "(no numeric USB descriptors parsed; this is not proof that USB is absent)"
    rows = list(dict.fromkeys(rows))
    summary = "Devices:\n" + "\n".join(dict.fromkeys(identities))
    summary += "\n\nNumeric descriptor fields (ordered, repeated identical rows omitted):\n" + "\n".join(rows[:120])
    if len(rows) > 120:
        summary += "\n[additional descriptor rows omitted; device identities above are retained]"
    return summary


def runtime_summary(value):
    if value.startswith("[unavailable:"):
        return value
    for line in value.splitlines():
        if not line.strip().startswith("{"):
            continue
        try:
            state = json.loads(line)
            engine = state.get("engine") or {}
            report = {key: state[key] for key in (
                "enabled", "power", "ready", "running", "runtime_uid",
                "requires_usb_permission", "background_service_allowed", "error"
            ) if key in state}
            report["engine"] = {key: engine[key] for key in (
                "engine_revision", "running", "phase", "device_id", "device_name",
                "capture_source", "period_ms", "capture_elapsed_ms", "actual_period_ms",
                "first_update_ms", "output_interval_ms", "output_write_ms", "output_frames",
                "progressive", "sample_points", "sample_regions", "batches", "sweeps",
                "light_ready", "light_module_id", "light_model", "light_unit_count",
                "requires_configuration", "error", "layout"
            ) if key in engine}
            report["recovery"] = {key: state.get("recovery", {})[key] for key in (
                "enabled", "secure_settings", "bound", "notification_access",
                "notification_bound", "sync_enabled", "sync_callbacks", "key_filter",
                "force_stop_recovery"
            ) if key in state.get("recovery", {})}
            return json.dumps(report, ensure_ascii=False, indent=2)
        except (ValueError, TypeError):
            pass
    return "(no running provider report; the app was not started by this collector)"


def find_adb(explicit):
    if explicit:
        return explicit
    found = shutil.which("adb")
    if found:
        return found
    for name in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        sdk = os.environ.get(name)
        if sdk:
            candidate = Path(sdk) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
            if candidate.is_file():
                return str(candidate)
    raise SystemExit("adb not found. Install Android platform-tools, add adb to PATH, or use --adb PATH.")


def main():
    if sys.version_info < (3, 9):
        raise SystemExit("Python 3.9 or newer is required.")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="ADB device ID from adb devices; never included in the report")
    parser.add_argument("--adb", help="path to adb when not on PATH")
    parser.add_argument("--output", type=Path, help="output Markdown path; default: .local/reports/device-report-TIME.md")
    parser.add_argument("--logs", action="store_true", help="include recent FrameLuma warning/error tags; review before sharing")
    parser.add_argument("--timeout", type=int, default=8, help="per-command timeout in seconds (default: 8)")
    args = parser.parse_args()
    if args.timeout < 1:
        parser.error("--timeout must be positive")
    adb = Adb(find_adb(args.adb), args.serial, args.timeout)
    if adb.query("get-state") != "device":
        raise SystemExit("Device is not ready. Check adb devices and allow debugging on the TV. This collector will not reconnect or reset adb.")
    policy = json.loads((ROOT / "gradle/config.json").read_text(encoding="utf-8-sig"))
    package = policy["application_id"]
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+", package):
        raise SystemExit("Invalid application_id in gradle/config.json")
    report = ["# FrameLuma device report", "", "Collected via read-only ADB queries. Review before sharing.",
              "Unsupported or denied commands are recorded, not treated as missing hardware.",
              "No app launch, permission changes, light commands, APK pull or upload was performed.", ""]

    def section(title, content, mask_addresses=True):
        report.extend(["## " + title, "", "```text", scrub(content, mask_addresses) or "(empty)", "```", ""])

    print("Reading build, display, USB and interface metadata...", flush=True)
    properties = "for key in " + " ".join(PROPERTIES) + "; do printf '%s=' \"$key\"; getprop \"$key\"; done"
    # These selected build properties exclude identifiers and network settings; keep numeric
    # firmware versions such as 1.2.3.4 rather than mistaking them for IPv4 addresses.
    section("Build properties", adb.query("shell", properties), mask_addresses=False)
    section("ADB caller and SELinux", adb.shell("id") + "\n" + adb.shell("getenforce"))
    section("Memory", select_lines(adb.shell("cat", "/proc/meminfo"), lambda line: line.startswith(("MemTotal:", "MemAvailable:"))))
    section("Android window size", adb.shell("wm", "size") + "\n" + adb.shell("wm", "density"))
    section("Display mode hints", display_summary(adb.shell("dumpsys", "display")))
    section("Binder interface candidates", select_lines(adb.shell("service", "list"), lambda line: bool(INTERFACE_WORDS.search(line))))
    section("HIDL interface candidates", select_lines(adb.shell("lshal", "--neat"), lambda line: bool(INTERFACE_WORDS.search(line))))
    section("System package candidates", select_lines(adb.shell("pm", "list", "packages", "-s"), lambda line: bool(INTERFACE_WORDS.search(line))))
    section("Shared Java libraries", adb.shell("pm", "list", "libraries"))
    for directory in ("/system/framework", "/system_ext/framework", "/product/framework", "/vendor/framework"):
        section("Framework filenames: " + directory, select_lines(adb.shell("ls", "-1", directory), lambda line: line.endswith(".jar")))
    section("USB numeric descriptor fields", usb_summary(adb.shell("dumpsys", "usb")))
    section("FrameLuma package", select_lines(adb.shell("dumpsys", "package", package), lambda line: bool(re.search(r"\bversion(?:Name|Code)=", line))))
    pid = adb.shell("pidof", package)
    if re.fullmatch(r"[0-9]+(?:\s+[0-9]+)*", pid):
        section("FrameLuma running state", runtime_summary(adb.shell("dumpsys", "activity", "provider", package + "/frameluma.runtime.StateProvider")))
    else:
        section("FrameLuma running state", "App not running or process query unavailable. No attempt was made to start it.")
    if args.logs:
        section("Recent FrameLuma warnings", adb.shell(
            "logcat", "-d", "-v", "brief", "-t", "160", "FrameLumaEngine:W",
            "FrameLumaService:W", "FrameLumaHost:W", "FrameLumaStrip3:W", "FrameLumaRecovery:W", "*:S"
        )[:24000])
    report.extend(["## Contributor notes", "", "Add controller name and length, current input source, reproduction steps, and expected/actual behavior.",
                   "Display modes and wm size do not establish the vendor pixel API's coordinate domain.",
                   "Candidate names do not establish that an interface is usable or permission has been granted.", ""])
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%d-%H%M%S-%f")
    output = args.output or ROOT / ".local/reports" / ("device-report-" + stamp + ".md")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(report), encoding="utf-8")
    print("Saved locally: " + str(output.resolve()))
    print("Review this file, then attach it to a device-support issue. Nothing was uploaded.")


if __name__ == "__main__":
    main()
