#!/usr/bin/env python3
"""Refresh the bundled Starbucks menu snapshot from the public web endpoint.

The app fetches this same endpoint live on launch; the snapshot is what it
shows instantly (and offline) before that fetch lands. Run from anywhere:

    python3 samples/starbucks-menu/tools/fetch_menu.py

Writes composeApp/src/commonMain/composeResources/files/menu.json, wrapping
the raw API payload with the fetch time and source URL so the UI can say
how fresh its data is. No third-party dependencies.
"""
import datetime as _dt
import json
import pathlib
import sys
import urllib.request

SOURCE_URL = "https://www.starbucks.com/apiproxy/v1/ordering/menu"
OUT = (
    pathlib.Path(__file__).resolve().parent.parent
    / "composeApp" / "src" / "commonMain" / "composeResources" / "files" / "menu.json"
)
# The endpoint serves the same JSON the starbucks.com menu page renders; it
# answers plain requests but, like most web endpoints, expects a browser-ish
# User-Agent.
HEADERS = {
    "Accept": "application/json",
    "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Safari/537.36",
}


def count_products(categories):
    return sum(len(c.get("products", [])) + count_products(c.get("children", [])) for c in categories)


def main() -> int:
    req = urllib.request.Request(SOURCE_URL, headers=HEADERS)
    with urllib.request.urlopen(req, timeout=60) as resp:
        payload = json.load(resp)
    menus = payload.get("menus")
    if not isinstance(menus, list) or not menus:
        print("unexpected payload shape: no 'menus' list", file=sys.stderr)
        return 1
    snapshot = {
        "fetchedAt": _dt.datetime.now(_dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "sourceUrl": SOURCE_URL,
        "menu": payload,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(snapshot, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"wrote {OUT} ({OUT.stat().st_size:,} bytes, {len(menus)} top-level menus, {count_products(menus)} products)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
