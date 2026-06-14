#!/usr/bin/env python3
import sys

def get_category_glyphs(ranges):
    glyphs = []
    for start, end in ranges:
        for i in range(start, end + 1):
            try:
                c = chr(i)
                if not (0 <= i <= 31) and not (127 <= i <= 159):
                    glyphs.append(c)
            except:
                pass
    return glyphs

high_freq_symbols = [
    ".", ",", "?", "!", "\"", "'", ":", ";", "-", "/",
    "(", ")", "<", ">", "+", "=", "|", "~", "_", "۔",
    "،", "؟", "؛", "٪", "°", "ﷺ", "ﷲ", "﷽", "ﷻ"
]

groups = [
    ("Common", [
        (0x0600, 0x06FF), (0x0750, 0x077F), (0x08A0, 0x08FF), # Arabic/Urdu
        (0x0021, 0x007E), (0x00A1, 0x00FF), (0x0100, 0x017F), # Latin
    ]),
    ("Symbol", [
        (0x2000, 0x206F), (0x20A0, 0x20CF), (0x2100, 0x214F), # General/Currency/Letter
        (0x2E00, 0x2E7F), # Supplemental Punctuation
        (0x2B00, 0x2BFF), # Misc Symbols and Arrows
    ]),
    ("Math", [
        (0x2190, 0x21FF), (0x2200, 0x22FF), (0x2300, 0x23FF), # Arrows/Math/Tech
        (0x27C0, 0x27EF), (0x2980, 0x29FF), # Misc Math
        (0x1D6A8, 0x1D7FF), # Math Symbols
    ]),
    ("Shape", [
        (0x2500, 0x257F), (0x2580, 0x259F), (0x25A0, 0x25FF), # Box/Block/Geometric
        (0x2600, 0x26FF), (0x2700, 0x27BF), # Misc Symbols/Dingbats
        (0x1F780, 0x1F7FF), # Geometric Shapes Extended
    ]),
    ("Emoji", [
        (0x1F600, 0x1F64F), (0x1F680, 0x1F6FF), (0x1F900, 0x1F9FF), # Emoticons/Transport/Misc
        (0x1F300, 0x1F5FF), # Misc Symbols & Pictographs
        (0x1F1E6, 0x1F1FF), # Flags
    ]),
    ("Ancient", [
        (0x13000, 0x1342F), # Egyptian Hieroglyphs
        (0x12000, 0x123FF), # Cuneiform
        (0x10140, 0x1018F), # Ancient Greek Numbers
        (0x10100, 0x1013F), # Aegean Numbers
        (0x10300, 0x1032F), # Old Italic
        (0x10330, 0x1034F), # Gothic
        (0x10400, 0x1044F), # Deseret
        (0x10800, 0x1083F), # Cypriot
        (0x10840, 0x1085F), # Imperial Aramaic
        (0x10900, 0x1091F), # Phoenician
    ]),
    ("Phonetic", [
        (0x0250, 0x02AF), (0x1D00, 0x1D7F), (0x1D80, 0x1DBF), # IPA/Phonetic
    ]),
    ("Indic", [
        (0x0900, 0x097F), # Devanagari
        (0x0980, 0x09FF), # Bengali
        (0x0A00, 0x0A7F), # Gurmukhi
        (0x0A80, 0x0AFF), # Gujarati
        (0x0B00, 0x0B7F), # Oriya
        (0x0B80, 0x0BFF), # Tamil
        (0x0C00, 0x0C7F), # Telugu
        (0x0C80, 0x0CFF), # Kannada
        (0x0D00, 0x0D7F), # Malayalam
        (0x0D80, 0x0DFF), # Sinhala
    ]),
    ("Scripts", [
        (0x0700, 0x074F), # Syriac
        (0x0780, 0x07BF), # Thaana
        (0x0E00, 0x0E7F), # Thai
        (0x0E80, 0x0EFF), # Lao
        (0x0F00, 0x0FFF), # Tibetan
        (0x1000, 0x109F), # Myanmar
        (0x10A0, 0x10FF), # Georgian
        (0x1200, 0x137F), # Ethiopic
        (0x13A0, 0x13FF), # Cherokee
        (0x1780, 0x17FF), # Khmer
        (0x1800, 0x18AF), # Mongolian
    ]),
    ("Extra", [
        (0x1400, 0x167F), # Canadian Aboriginal
        (0x1680, 0x16FF), # Ogham, Runic
        (0x2800, 0x28FF), # Braille Patterns
        (0x2C00, 0x2C5F), # Glagolitic
        (0x2C60, 0x2C7F), # Latin Extended-C
        (0x2D00, 0x2D2F), # Georgian Supplement
        (0x2D30, 0x2D7F), # Tifinagh
        (0xAB30, 0xAB6F), # Latin Extended-E
        (0xA720, 0xA7FF), # Latin Extended-D
        (0xA4D0, 0xA4FF), # Lisu
    ])
]

seen = set()
final_groups = []

for name, ranges in groups:
    glyphs = get_category_glyphs(ranges)
    unique_glyphs = []
    for g in glyphs:
        if g not in seen:
            unique_glyphs.append(g)
            seen.add(g)

    if name == "Common":
        for hf in reversed(high_freq_symbols):
            if hf not in seen:
                unique_glyphs.insert(0, hf)
                seen.add(hf)
            else:
                if hf in unique_glyphs:
                    unique_glyphs.remove(hf)
                    unique_glyphs.insert(0, hf)

    if unique_glyphs:
        final_groups.append((name, unique_glyphs))

with open("res/raw/glyphs.txt", "w", encoding="utf-8") as f:
    total_count = 0
    all_glyphs = []
    group_info = []

    for name, glyphs in final_groups:
        all_glyphs.extend(glyphs)
        total_count += len(glyphs)
        group_info.append(f"{name}:{total_count}")

    all_glyphs = all_glyphs[:20000]

    for g in all_glyphs:
        f.write(g + "\n")
    f.write("\n")
    f.write(" ".join(group_info) + "\n")
