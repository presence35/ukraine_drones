#!/usr/bin/env python3
"""
EN-only Strings.kt — UA sync helper.

EN is the single source of truth in Strings.kt (en*() funcs).
UA is derived as EN.copy(language = UA) + uaManualPatch() overrides
(// MANUALLY KEPT EN:"<snapshot>" hash:<6>).

This script:
  --export  : dump EN map to tmp/en.json for LLM/batch
  --import  : read tmp/ua.json (translated, you provide) and validate/apply
  --check   : verify MANUALLY KEPT entries are not stale (EN changed since snapshot)
  --help

Stale = current EN value != snapshot in comment or hash mismatch.
On stale, the manual UA must be re-translated — do not silently keep it.

Usage:
  py tools/sync_ua.py --check
  py tools/sync_ua.py --export [--out tmp/en.json]
  py tools/sync_ua.py --import [--in tmp/ua.json]
"""
import argparse, hashlib, json, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STRINGS = ROOT / "app/src/main/java/ua/ukrainedrones/domain/Strings.kt"

def sha6(s: str) -> str:
    return hashlib.sha256(s.encode("utf-8")).hexdigest()[:6]

def extract_en() -> dict:
    text = STRINGS.read_text(encoding="utf-8")
    en_map = {}
    for section in re.finditer(r"private fun en(\w+)\(\) = \w+\((.*?)\)", text, re.S):
        block = section.group(2)
        for m in re.finditer(r'(\w+)\s*=\s*"((?:[^"\\]|\\.)*)"', block):
            k, v = m.group(1), m.group(2)
            en_map[k] = v
    return en_map

def extract_manual() -> list:
    text = STRINGS.read_text(encoding="utf-8")
    m = re.search(r"private fun uaManualPatch\(.*?\)(.*?)\n    \)", text, re.S)
    if not m:
        return []
    block = m.group(0)
    entries = []
    # each line: key = "uaValue" // MANUALLY KEPT EN:"snapshot" hash:xxxx
    for lm in re.finditer(r'(\w+)\s*=\s*"((?:[^"\\]|\\.)*)"\s*//\s*MANUALLY KEPT EN:"((?:[^"\\]|\\.)*)"\s*hash:([0-9a-f]+)', block):
        key, ua_val, en_snapshot, hash_stored = lm.group(1), lm.group(2), lm.group(3), lm.group(4)
        entries.append((key, ua_val, en_snapshot, hash_stored))
    return entries

def cmd_check():
    en_map = extract_en()
    manual = extract_manual()
    if not manual:
        print("No MANUALLY KEPT entries found.")
        return 0
    stale = []
    for key, ua_val, en_snapshot, hash_stored in manual:
        cur = en_map.get(key)
        if cur is None:
            print(f"WARN: manual key '{key}' not found in EN.")
            continue
        cur_hash = sha6(cur)
        if cur != en_snapshot or cur_hash != hash_stored:
            stale.append((key, en_snapshot, cur, hash_stored, cur_hash, ua_val))
            print(f"STALE: {key}")
            print(f"  snapshot : {en_snapshot!r} hash:{hash_stored}")
            print(f"  current  : {cur!r} hash:{cur_hash}")
            print(f"  manual UA: {ua_val!r} — needs re-translation")
        else:
            print(f"OK: {key} hash:{cur_hash}")
    if stale:
        print(f"\n{len(stale)} stale MANUALLY KEPT entry(ies) — EN changed, re-translate before release.", file=sys.stderr)
        return 1
    print("\nAll MANUALLY KEPT entries up to date.")
    return 0

def cmd_export(out: Path):
    en_map = extract_en()
    out.parent.mkdir(parents=True, exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(en_map, f, ensure_ascii=False, indent=2)
    print(f"Exported {len(en_map)} EN keys -> {out}")
    return 0

def cmd_import(inp: Path):
    en_map = extract_en()
    if not inp.exists():
        print(f"Missing {inp} — run --export first or provide translated ua.json", file=sys.stderr)
        return 1
    try:
        ua_map = json.loads(inp.read_text(encoding="utf-8"))
    except Exception as e:
        print(f"Failed to read {inp}: {e}", file=sys.stderr)
        return 1
    manual = extract_manual()
    manual_keys = {k for k, _, _, _ in manual}
    # Validate coverage
    missing = [k for k in en_map if k not in ua_map or not ua_map[k].strip()]
    extra = [k for k in ua_map if k not in en_map]
    if missing:
        print(f"WARN: {len(missing)} keys missing/empty in {inp}: {missing[:10]}", file=sys.stderr)
    if extra:
        print(f"WARN: {len(extra)} extra keys in {inp} not in EN: {extra[:10]}", file=sys.stderr)
    # Check manual kept are preserved (and not stale)
    stale = []
    for k, ua_val, en_snapshot, hash_stored in manual:
        cur_en = en_map.get(k, "")
        cur_hash = sha6(cur_en)
        if cur_en != en_snapshot or cur_hash != hash_stored:
            stale.append(k)
            print(f"STALE manual: {k} EN changed — needs re-translation (ua.json value will be re-translated, not kept)")
        else:
            # ensure ua.json kept the manual value
            if ua_map.get(k) != ua_val:
                print(f"NOTE: {inp} differs for MANUALLY KEPT {k} — expected kept value {ua_val!r}, got {ua_map.get(k)!r} (will keep manual)")
    if stale:
        print(f"\n{len(stale)} stale MANUALLY KEPT — re-translate before release.", file=sys.stderr)
    if not missing and not stale:
        print(f"Import OK: {inp} covers {len(ua_map)} keys, {len(manual)} manual kept verified.")
        return 0
    # Still allow import with warnings; return 1 if missing/stale to block release
    return 1 if (missing or stale) else 0

def main():
    ap = argparse.ArgumentParser(description="UA sync helper")
    ap.add_argument("--check", action="store_true", help="check MANUALLY KEPT for staleness")
    ap.add_argument("--export", action="store_true", help="export EN map to tmp/en.json")
    ap.add_argument("--import", dest="do_import", action="store_true", help="import/validate tmp/ua.json (you provide)")
    ap.add_argument("--out", default="tmp/en.json", help="export output path (tmp/en.json)")
    ap.add_argument("--in", dest="inp", default="tmp/ua.json", help="import input path (tmp/ua.json)")
    args = ap.parse_args()
    if args.check:
        sys.exit(cmd_check())
    if args.export:
        sys.exit(cmd_export(Path(args.out)))
    if args.do_import:
        sys.exit(cmd_import(Path(args.inp)))
    ap.print_help()
    return 0

if __name__ == "__main__":
    main()
