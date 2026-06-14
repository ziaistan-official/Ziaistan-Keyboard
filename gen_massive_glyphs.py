#!/usr/bin/env python3
import xml.etree.ElementTree as ET
import sys

def get_glyphs_by_category():
    categories = {
        "latin": [],
        "arabic": [],
        "symbols": [],
        "math": [],
        "other": []
    }

    ranges = {
        "latin": [(0x0021, 0x007E), (0x00A1, 0x00FF), (0x0100, 0x017F)],
        "arabic": [(0x0600, 0x06FF), (0x0750, 0x077F), (0x08A0, 0x08FF), (0xFB50, 0xFDFF), (0xFE70, 0xFEFF)],
        "symbols": [(0x2000, 0x206F), (0x20A0, 0x20CF), (0x2100, 0x214F), (0x2150, 0x218F), (0x2460, 0x24FF), (0x2500, 0x257F), (0x2580, 0x259F), (0x25A0, 0x25FF), (0x2600, 0x26FF), (0x2700, 0x27BF)],
        "math": [(0x2070, 0x209F), (0x2190, 0x21FF), (0x2200, 0x22FF), (0x2300, 0x23FF)],
    }

    for cat, cat_ranges in ranges.items():
        for start, end in cat_ranges:
            for i in range(start, end + 1):
                c = chr(i)
                if c.isprintable() and c not in ['\n', '\r', '\t']:
                    categories[cat].append(c)

    return categories

def escape(s):
    if s == '"': return '&quot;'
    if s == "'": return "&apos;"
    if s == "<": return "&lt;"
    if s == ">": return "&gt;"
    if s == "&": return "&amp;"
    if s == "@": return "\\@"
    if s == "#": return "\\#"
    if s == "?": return "\\?"
    if s == "\\": return "\\\\"
    if s == "/": return "\\/"
    return s

# High frequency symbols to be placed in the last rows (ergonomic access)
# Taken from original special_glyphs.xml
high_freq_symbols = [
    ".", ",", "?", "!", "\"", "'", ":", ";", "-", "/",
    "(", ")", "<", ">", "+", "=", "|", "~", "_", "۔",
    "،", "؟", "؛", "؛", "٪", "°", "ﷺ", "ﷲ", "﷽", "ﷻ"
]

cats = get_glyphs_by_category()

arabic_all = cats["arabic"]
latin_all = cats["latin"]
symbols_all = cats["symbols"]
math_all = cats["math"]

# Remove high freq from categories to avoid duplicates
all_others = latin_all + symbols_all + math_all
for s in high_freq_symbols:
    if s in all_others: all_others.remove(s)
    if s in arabic_all: arabic_all.remove(s)

# Goal: 16 rows x 10 keys x 9 slots = 1440 slots per state.
# Total 2 states (Normal, Fn) = 2880 slots.

# Ergonomic placement: High frequency symbols in row 15 (last row) and maybe row 14.
# Each row has 90 slots. 30 high freq symbols fit easily in one row's center/primary slots.
# We'll fill the grid and then overwrite the last row.

normal_glyphs = all_others[:1080] + arabic_all[:360]
fn_glyphs = all_others[1080:2160] + arabic_all[360:720]

while len(normal_glyphs) < 1440: normal_glyphs.append(" ")
while len(fn_glyphs) < 1440: fn_glyphs.append(" ")

# Overwrite row 15 (indices 1350 to 1439) with high freq symbols and favorites
for i, s in enumerate(high_freq_symbols):
    if i < 90:
        normal_glyphs[1350 + i] = s

root = ET.Element("keyboard", name="Special Glyphs & Symbols", script="latin", bottom_row="true")

modmap = ET.SubElement(root, "modmap")
# Map normal to fn
for i in range(1440):
    a = normal_glyphs[i]
    b = fn_glyphs[i]
    if a.strip() and b.strip():
        fn = ET.SubElement(modmap, "fn")
        fn.set("a", a)
        fn.set("b", b)

synonyms = ["c", "nw", "ne", "sw", "se", "w", "e", "n", "s"]

# Content Rows
for r in range(16):
    row_elem = ET.SubElement(root, "row")
    for k in range(10):
        key_elem = ET.SubElement(row_elem, "key", width="1.0")
        base_idx = (r * 10 + k) * 9
        for s_idx, syn in enumerate(synonyms):
            val = normal_glyphs[base_idx + s_idx]
            if val.strip():
                key_elem.set(syn, ":tiny " + val)

# Indent and write
def indent(elem, level=0):
    i = "\n" + level*"  "
    if len(elem):
        if not elem.text or not elem.text.strip():
            elem.text = i + "  "
        if not elem.tail or not elem.tail.strip():
            elem.tail = i
        for elem in elem:
            indent(elem, level+1)
        if not elem.tail or not elem.tail.strip():
            elem.tail = i
    else:
        if level and (not elem.tail or not elem.tail.strip()):
            elem.tail = i

indent(root)
tree = ET.ElementTree(root)
with open("srcs/layouts/special_glyphs.xml", "wb") as f:
    f.write(b'<?xml version="1.0" encoding="utf-8"?>\n')
    tree.write(f, encoding="utf-8", xml_declaration=False)
