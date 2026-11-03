#!/usr/bin/env python3
"""Offline Android resource checks. Usage: py -I tools/translation-check.py [de pt-rBR ...].

CLDR cardinal categories for the shipped/planned languages are listed explicitly:
Portuguese (including Brazil), Spanish and French: one/many/other; German: one/other;
Indonesian: other; Turkish: one/other. Android accepts all six quantity names; older ICU
versions fall back to other when they do not select many. New locales must be
reviewed here rather than silently assuming English rules. No third-party packages.
"""

from collections import Counter
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


RES = Path(__file__).resolve().parents[1] / "app/src/main/res"
CATEGORIES = {
    "de": {"one", "other"}, "pt": {"one", "many", "other"},
    "es": {"one", "many", "other"}, "fr": {"one", "many", "other"},
    "id": {"other"}, "tr": {"one", "other"},
}
QUANTITIES = {"zero", "one", "two", "few", "many", "other"}
# java.util.Formatter, including indexed arguments, flags, width and date formats.
FORMAT = re.compile(r"%(?:\d+\$)?[-#+ 0,(<]*(?:\d+)?(?:\.\d+)?(?:[tT][a-zA-Z]|[bBhHsScCdoxXeEfgGaA%n])")
BRAND = "Free\u2060Period"


def placeholders(value):
    """Keep exact tokens and their multiplicity; indexed tokens may be reordered."""
    return Counter(FORMAT.findall(value))


def apostrophe_error(value):
    # Android accepts apostrophes inside double-quoted text or after an odd
    # number of backslashes. XML entities have already been decoded by ET.
    quoted = False
    escaped = False
    for char in value:
        if escaped:
            escaped = False
        elif char == "\\":
            escaped = True
        elif char == '"':
            quoted = not quoted
        elif char == "'" and not quoted:
            return True
    return False


def text_of(element):
    return "".join(element.itertext())


def read_resources(path, errors):
    try:
        # Parsing also rejects bare ampersands and malformed numeric entities.
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as exc:
        errors.append(f"{path}: {exc}")
        return {}
    if root.tag != "resources":
        errors.append(f"{path}: expected <resources> root")
    result = {}
    for node in root:
        if node.tag not in ("string", "plurals"):
            continue
        key = node.get("name")
        if not key or key in result:
            errors.append(f"{path}: missing or duplicate resource name {key!r}")
        result[key] = node
    return result


def check_text(source, target, label, errors, warnings):
    if placeholders(source) != placeholders(target):
        errors.append(f"{label}: placeholders differ from EN")
    if apostrophe_error(target):
        errors.append(f"{label}: unescaped apostrophe")
    if source.count(BRAND) != target.count(BRAND):
        errors.append(f"{label}: keep Free&#8288;Period and its word joiner")
    # Soft hyphens and Android escape sequences do not occupy extra display space.
    def display_length(value):
        return len(re.sub(r"\\(.)", r"\1", value).replace("\u00ad", ""))
    original = display_length(source)
    if original and display_length(target) > 1.6 * original:
        warnings.append(f"{label}: length {display_length(target) / original:.2f} x EN")


def check_locale(locale, source, errors, warnings):
    if not re.fullmatch(r"[a-z]{2,3}(?:-r[A-Z]{2}|-[A-Za-z0-9+]+)?", locale):
        errors.append(f"Invalid locale qualifier: {locale!r}")
        return
    path = RES / f"values-{locale}" / "strings.xml"
    target = read_resources(path, errors)
    expected = {k: v for k, v in source.items() if v.get("translatable") != "false"}
    for key in sorted(expected.keys() - target.keys()):
        errors.append(f"{locale}/{key}: missing key")
    for key in sorted(target.keys() - expected.keys()):
        reason = "non-translatable key must not appear" if key in source else "extra key"
        errors.append(f"{locale}/{key}: {reason}")
    # Android uses the legacy resource qualifier in for Indonesian (BCP-47 id).
    language = locale.split("-")[0]
    categories = CATEGORIES.get("id" if language == "in" else language)
    if categories is None:
        errors.append(f"{locale}: add reviewed CLDR cardinal categories to CATEGORIES")
    for key in expected.keys() & target.keys():
        original, translated = expected[key], target[key]
        label = f"{locale}/{key}"
        if translated.get("translatable") == "false":
            errors.append(f"{label}: translation must not be marked non-translatable")
        if original.tag != translated.tag:
            errors.append(f"{label}: resource type differs from EN")
            continue
        if original.tag == "string":
            check_text(text_of(original), text_of(translated), label, errors, warnings)
            continue
        items = {}
        for item in translated:
            quantity = item.get("quantity")
            if item.tag != "item" or quantity not in QUANTITIES or quantity in items:
                errors.append(f"{label}: invalid or duplicate plural item {quantity!r}")
            items[quantity] = item
        for quantity in sorted((categories or {"other"}) - items.keys()):
            errors.append(f"{label}: missing plural category {quantity}")
        originals = {i.get("quantity"): i for i in original}
        for quantity, item in items.items():
            # Target-only categories (pt many) use the EN other contract.
            reference = originals.get(quantity, originals.get("other"))
            if reference is not None:
                check_text(text_of(reference), text_of(item), f"{label}[{quantity}]", errors, warnings)
    strings = sum(n.tag == "string" for n in target.values())
    plurals = sum(n.tag == "plurals" for n in target.values())
    print(f"{locale}: {strings} strings, {plurals} plurals")


def main(args):
    errors, warnings = [], []
    source = read_resources(RES / "values/strings.xml", errors)
    locales = args or sorted(p.parent.name[7:] for p in RES.glob("values-*/strings.xml"))
    for locale in locales:
        check_locale(locale, source, errors, warnings)
    for warning in sorted(warnings):
        print(f"WARN: {warning}")
    for error in sorted(errors):
        print(f"ERROR: {error}")
    print(f"{len(errors)} errors, {len(warnings)} length warnings; {len(locales)} locales checked")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
