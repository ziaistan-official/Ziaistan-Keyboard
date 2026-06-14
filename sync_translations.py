import xml.etree.ElementTree as ET
import glob, os, sys








VALUE_DIR_TO_METADATA = {
        "cs": "cs-CZ",
        "de": "de-DE",
        "en": "en-US",
        "es": "es-ES",
        "fa": "fa-IR",
        "fil": "fil",
        "fr": "fr-FR",
        "in": "id",
        "it": "it-IT",
        "ja": "ja-JP",
        "ko": "ko-KR",
        "lv": "lv",
        "nl": "nl-NL",
        "pl": "pl-PL",
        "pt": "pt-BR",
        "ro": "ro",
        "ru": "ru-RU",
        "tr": "tr-TR",
        "uk": "uk",
        "vi": "vi",
        "zh-rCN": "zh-CN",
        }


def parse_strings_file(file):
    def key(ent): return ent.get("name"), ent.get("product")
    resrcs = ET.parse(file).getroot()
    return { key(ent): ent for ent in resrcs if ent.tag == "string" }


def write_updated_strings(out, strings):
    out.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n')
    for key, string, comment in strings:
        out.write("    ")
        if comment: out.write("<!-- ")
        out.write(ET.tostring(string, "unicode").strip())
        if comment: out.write(" -->")
        out.write("\n")
    out.write('</resources>\n')


def print_status(fname, strings):

    c = sum(1 for _, _, comment in strings if comment)
    status = "uptodate" if c == 0 else "missing %d strings" % c
    print("%s: %s" % (fname, status))


def sync(baseline, strings):
    return [
            (key, strings[key], False) if key in strings else
            (key, base_string, True)
            for key, base_string in baseline.items() ]

def sync_metadata(value_dir, strings):
    if ("short_description", None) not in strings:
        return
    locale = os.path.basename(value_dir).removeprefix("values-")
    if not locale in VALUE_DIR_TO_METADATA:
        raise Exception("Locale '%s' not known, please add it into sync_translations.py" % locale)
    meta_dir = "fastlane/metadata/android/" + VALUE_DIR_TO_METADATA[locale]
    def sync_meta_file(fname, string_name):
        if string_name in strings:
            string = strings[string_name]
            if not os.path.isdir(meta_dir):
                os.makedirs(meta_dir)
            txt_file = os.path.join(meta_dir, fname)
            with open(txt_file, "w", encoding="utf-8") as out:
                out.write(string.text
                          .replace("\\n", "\n")
                          .replace("\\'", "'")
                          .removeprefix('"')
                          .removesuffix('"'))
                out.write("\n")
    sync_meta_file("title.txt", ("app_name_release", None))
    sync_meta_file("short_description.txt", ("short_description", None))
    sync_meta_file("full_description.txt", ("store_description", None))

if len(sys.argv) < 2 or sys.argv[1] != "sync":
    print("Syncing of translation files is no longer needed. Run with 'sync_translation.py sync' to do it anyway.")
    exit(1)

baseline = parse_strings_file("res/values/strings.xml")

for value_dir in glob.glob("res/values-*"):
    strings_file = os.path.join(value_dir, "strings.xml")
    if os.path.isfile(strings_file):
        local_strings = dict(parse_strings_file(strings_file))
        synced_strings = sync(baseline, local_strings)
        with open(strings_file, "w", encoding="utf-8") as out:
            write_updated_strings(out, synced_strings)
        sync_metadata(value_dir, local_strings)
        print_status(strings_file, synced_strings)

sync_metadata("en", baseline)
