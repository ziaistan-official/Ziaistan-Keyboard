package juloo.keyboard2;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

public class UnicodeDatasetGenerator {

    public static class UnicodeRange {
        public String name;
        public int start;
        public int end;
        public UnicodeRange(String name, int start, int end) {
            this.name = name;
            this.start = start;
            this.end = end;
        }
    }

    public static List<UnicodeRange> getRanges() {
        List<UnicodeRange> ranges = new ArrayList<>();
        ranges.add(new UnicodeRange("Basic Latin", 0x0020, 0x007F));
        ranges.add(new UnicodeRange("Latin-1 Supplement", 0x00A0, 0x00FF));
        ranges.add(new UnicodeRange("Urdu/Arabic", 0x0600, 0x06FF));
        ranges.add(new UnicodeRange("Arabic Supplement", 0x0750, 0x077F));
        ranges.add(new UnicodeRange("Devanagari (Hindi)", 0x0900, 0x097F));
        ranges.add(new UnicodeRange("Bengali", 0x0980, 0x09FF));
        ranges.add(new UnicodeRange("Cyrillic (Russian)", 0x0400, 0x04FF));
        ranges.add(new UnicodeRange("Greek", 0x0370, 0x03FF));
        ranges.add(new UnicodeRange("Hebrew", 0x0590, 0x05FF));
        ranges.add(new UnicodeRange("CJK Unified Ideographs", 0x4E00, 0x4FFF)); // Partial for brevity
        ranges.add(new UnicodeRange("Hiragana", 0x3040, 0x309F));
        ranges.add(new UnicodeRange("Katakana", 0x30A0, 0x30FF));
        ranges.add(new UnicodeRange("Mathematical Operators", 0x2200, 0x22FF));
        ranges.add(new UnicodeRange("Box Drawing", 0x2500, 0x257F));
        ranges.add(new UnicodeRange("Block Elements", 0x2580, 0x259F));
        ranges.add(new UnicodeRange("Geometric Shapes", 0x25A0, 0x25FF));
        ranges.add(new UnicodeRange("Emoji", 0x1F600, 0x1F64F));
        ranges.add(new UnicodeRange("Emoji Transport", 0x1F680, 0x1F6FF));
        return ranges;
    }

    public static List<String> getCharactersForRange(UnicodeRange range) {
        List<String> chars = new ArrayList<>();
        for (int i = range.start; i <= range.end; i++) {
            if (Character.isValidCodePoint(i)) {
                chars.add(new String(Character.toChars(i)));
            }
        }
        return chars;
    }
}
