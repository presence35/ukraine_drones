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

# Ensure UTF-8 output on Windows cp1252 consoles
try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass

ROOT = Path(__file__).resolve().parents[1]
STRINGS = ROOT / "app/src/main/java/ua/ukrainedrones/domain/Strings.kt"

def sha6(s: str) -> str:
    return hashlib.sha256(s.encode("utf-8")).hexdigest()[:6]

def _find_matching_paren(text: str, open_idx: int) -> int:
    depth = 0
    in_str = False
    esc = False
    for i in range(open_idx, len(text)):
        c = text[i]
        if in_str:
            if esc:
                esc = False
            elif c == '\\':
                esc = True
            elif c == '"':
                in_str = False
        else:
            if c == '"':
                in_str = True
            elif c == '(':
                depth += 1
            elif c == ')':
                depth -= 1
                if depth == 0:
                    return i
    return -1

def extract_en() -> dict:
    text = STRINGS.read_text(encoding="utf-8")
    en_map = {}
    for m in re.finditer(r"private fun en(\w+)\(\) = (\w+)\(", text):
        start = m.end() - 1  # '('
        end = _find_matching_paren(text, start)
        if end == -1:
            continue
        block = text[start+1:end]
        for km in re.finditer(r'(\w+)\s*=\s*"((?:[^"\\]|\\.)*)"', block):
            k, v = km.group(1), km.group(2)
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

def _kotlin_escape(s: str) -> str:
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n').replace('\r', '\\r').replace('$', '\\$') + '"'

def _extract_en_sections():
    text = STRINGS.read_text(encoding="utf-8")
    sections = {}
    order = []
    for m in re.finditer(r"private fun en(\w+)\(\) = (\w+)\(", text):
        name = m.group(1)
        key = name[0].lower() + name[1:]
        if key == "explainers":
            key = "explainers"
        start = m.end() - 1
        end = _find_matching_paren(text, start)
        if end == -1:
            continue
        block = text[start+1:end]
        keys = []
        for km in re.finditer(r'(\w+)\s*=\s*"((?:[^"\\]|\\.)*)"', block):
            keys.append((km.group(1), km.group(2)))
        sections[key] = keys
        order.append(key)
    return sections, order

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
    manual_dict = {k: (ua, en_snap, h) for k, ua, en_snap, h in manual}
    # Validate coverage
    missing = [k for k in en_map if k not in ua_map or not str(ua_map[k]).strip()]
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
            print(f"STALE manual: {k} EN changed — needs re-translation (ua.json value will be used, manual hash will be updated)")
        else:
            if ua_map.get(k) != ua_val:
                print(f"NOTE: {inp} differs for MANUALLY KEPT {k} — keeping code manual value {ua_val!r} over ua.json {ua_map.get(k)!r}")
                # keep manual value
                ua_map[k] = ua_val
    if stale:
        print(f"\n{len(stale)} stale MANUALLY KEPT — will be updated from ua.json.", file=sys.stderr)

    # Build new uaManualPatch with all UA values (using ua_map, preserving non-stale manual)
    sections, order = _extract_en_sections()
    # Need to decide per-key UA value: use ua_map if present else keep EN? For missing, fallback to EN to keep buildable
    lines = []
    lines.append("    private fun uaManualPatch(base: StringSet): StringSet = base.copy(")
    # Map section key to StringSet field name
    section_field = {
        "onboarding": "onboarding",
        "settings": "settings",
        "status": "status",
        "updates": "updates",
        "threat": "threat",
        "misc": "misc",
        "widget": "widget",
        "guide": "guide",
        "explainers": "explainers",
    }
    for sec in order:
        field = section_field.get(sec)
        if not field or sec not in sections:
            continue
        keys = sections[sec]
        if not keys:
            continue
        # For explainers, handle only simple keys (visualLabel etc.), items are complex and kept as base
        if sec == "explainers":
            # only handle visualLabel, scenarioLabel, gotIt
            expl_keys = [k for k, _ in keys if k in ua_map]
            if not expl_keys:
                continue
            lines.append(f"        {field} = base.{field}.copy(")
            for k, _ in keys:
                if k not in ua_map:
                    continue
                # check if stale manual? already handled, keep ua_map
                v = ua_map.get(k, "")
                # if manual and not stale, v already is manual kept (we forced)
                # add MANUALLY KEPT comment if this key is manual
                if k in manual_dict and k not in stale:
                    en_snap = manual_dict[k][1]
                    h = manual_dict[k][2]
                    lines.append(f"            {k} = {_kotlin_escape(v)} // MANUALLY KEPT EN:{_kotlin_escape(en_snap)} hash:{h},")
                elif k in manual_dict and k in stale:
                    # stale manual: update snapshot/hash to current EN
                    cur_en = en_map[k]
                    cur_h = sha6(cur_en)
                    lines.append(f"            {k} = {_kotlin_escape(v)} // MANUALLY KEPT EN:{_kotlin_escape(cur_en)} hash:{cur_h}")
                else:
                    lines.append(f"            {k} = {_kotlin_escape(v)},")
            lines.append("        ),")
            continue
        lines.append(f"        {field} = base.{field}.copy(")
        for k, _ in keys:
            if k not in ua_map:
                continue
            v = ua_map[k]
            if not isinstance(v, str):
                v = str(v)
            # If empty (template), skip -> will fallback to EN (base)
            if not v.strip():
                continue
            if k in manual_dict and k not in stale:
                en_snap = manual_dict[k][1]
                h = manual_dict[k][2]
                lines.append(f"            {k} = {_kotlin_escape(v)} // MANUALLY KEPT EN:{_kotlin_escape(en_snap)} hash:{h},")
            elif k in manual_dict and k in stale:
                cur_en = en_map[k]
                cur_h = sha6(cur_en)
                lines.append(f"            {k} = {_kotlin_escape(v)} // MANUALLY KEPT EN:{_kotlin_escape(cur_en)} hash:{cur_h},")
            else:
                lines.append(f"            {k} = {_kotlin_escape(v)},")
        lines.append("        ),")
    lines.append("    )")
    new_patch = "\n".join(lines)
    # Replace in Strings.kt
    text = STRINGS.read_text(encoding="utf-8")
    old_pat = re.compile(r"    private fun uaManualPatch\(.*?\)(.*?)\n    \)", re.S)
    m = old_pat.search(text)
    if not m:
        print("Failed to find uaManualPatch in Strings.kt", file=sys.stderr)
        return 1
    new_text = text[:m.start()] + new_patch + text[m.end():]
    STRINGS.write_text(new_text, encoding="utf-8")
    print(f"Updated {STRINGS} — UA now from {inp} ({len([k for k in ua_map if k in en_map])} keys)")
    if missing:
        print(f"NOTE: {len(missing)} keys missing in ua.json — kept as EN fallback (translate before release)")
    if stale:
        print(f"Updated {len(stale)} stale manual hashes.")
    return 0 if not missing else 1

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
