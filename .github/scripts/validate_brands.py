#!/usr/bin/env python3
"""Waliduje data/brands.json: poprawny JSON, wymagane pola, unikalne nazwy i aliasy."""
import json
import re
import sys
import unicodedata

PATH = sys.argv[1] if len(sys.argv) > 1 else "data/brands.json"
RATINGS = {"green", "yellow", "red"}
errors = []


def norm(s: str) -> str:
    s = s.lower().replace("ł", "l")
    s = "".join(c for c in unicodedata.normalize("NFD", s) if unicodedata.category(c) != "Mn")
    return re.sub(r"[^a-z0-9]+", " ", s).strip()


try:
    with open(PATH, encoding="utf-8") as fh:
        db = json.load(fh)
except json.JSONDecodeError as e:
    print(f"::error file={PATH},line={e.lineno},col={e.colno}::Niepoprawny JSON: {e.msg}")
    sys.exit(1)

if not isinstance(db.get("version"), int) or db["version"] < 1:
    errors.append('"version" musi być liczbą całkowitą >= 1')
if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", str(db.get("updated", ""))):
    errors.append('"updated" musi mieć format RRRR-MM-DD')
brands = db.get("brands")
if not isinstance(brands, list) or not brands:
    errors.append('"brands" musi być niepustą listą')
    brands = []

seen_names = {}
seen_aliases = {}
for i, b in enumerate(brands):
    where = f'brands[{i}] ({b.get("name", "?")})'
    for key, typ in (("name", str), ("aliases", list), ("country", str), ("rating", str),
                     ("note", str), ("counterfeit_warning", bool)):
        if not isinstance(b.get(key), typ):
            errors.append(f"{where}: pole \"{key}\" brak lub zły typ (oczekiwano {typ.__name__})")
    if b.get("rating") not in RATINGS:
        errors.append(f"{where}: rating musi być jednym z {sorted(RATINGS)}")
    name = b.get("name", "")
    if name in seen_names:
        errors.append(f"{where}: zduplikowana nazwa (także brands[{seen_names[name]}])")
    seen_names[name] = i
    for alias in set([name] + list(b.get("aliases") or [])):
        n = norm(str(alias))
        if not n:
            continue
        owner = seen_aliases.get(n)
        if owner is not None and owner != name:
            errors.append(f'{where}: alias "{alias}" koliduje z marką "{owner}"')
        seen_aliases[n] = name

if errors:
    for e in errors:
        print(f"::error file={PATH}::{e}")
    sys.exit(1)
print(f"OK: {len(brands)} marek, wersja {db['version']} ({db['updated']})")
