package juloo.keyboard2.passwordmanager;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class PasswordGenerator {

    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String NUMBERS = "0123456789";
    private static final String SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?";























    private static final String LATIN_EXT = "ÀÁÂÃÄÅÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝÞßàáâãäåçèéêëìíîïðñòóôõöøùúûüýþÿ";
    private static final String CYRILLIC = "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя";
    private static final String ARABIC = "ابتثجحخدذرزسشصضطظعغفقكلمنهوي";
    private static final String DEVANAGARI = "अआइईउऊऋएऐओऔकखगघङचछजझञटठडढणतथदधनपफबभमयरलवशषसह";
    private static final String BENGALI = "অআইঈউঊঋএঐওঔকখগঘঙচছজঝঞটঠডঢণতথদধনপফবভমযরলশষসহ";
    private static final String GREEK = "ΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩαβγδεζηθικλμνξοπρστυφχψω";
    private static final String HEBREW = "אבגדהוזחטיכלמנסעפצקרשת";
    private static final String THAI = "กขฃคฅฆงจฉชซฌญฎฏฐฑฒณดตถทธนบปผฝพฟภมยรลวศษสหฬอฮ";



    private static final String CJK_SAMPLE = "的一是在不了有和人这中大为上个国我以要他时来用们生到作地于出就分对成会可主发年动同工也能下过子说产种面而方后多定行学法所民得经十三之进着等部度家电力里如水化高自二理起小物现实量都两体制机当使点从业本去把性好应开它合还因由其些然前外天政四日那社义事平形相全表间样想向道命此位理望常教";

    private static final String EMOJIS = "😀😃😄😁😆😅😂🤣😊😇🙂🙃😉😌😍🥰😘😗😙😚😋😛😝😜🤪🤨🧐🤓😎🤩🥳🤡🤠🤥🤫🤭🧐🤓😈👿👹👺💀☠️👻👽";

    private static final String AMBIGUOUS = "0O1lI";

    public static class Options {
        public boolean useLowercase = true;
        public boolean useUppercase = true;
        public boolean useNumbers = true;
        public boolean useSymbols = true;
        public boolean useMultilingual = true;
        public boolean useEmojis = true;
        public boolean excludeAmbiguous = false;
        public int length = 40;
    }

    public static String generatePassword(Options options) {
        List<String> units = new ArrayList<>();

        if (options.useLowercase) addChars(units, LOWERCASE);
        if (options.useUppercase) addChars(units, UPPERCASE);
        if (options.useNumbers) addChars(units, NUMBERS);
        if (options.useSymbols) addChars(units, SYMBOLS);

        if (options.useMultilingual) {
            addChars(units, LATIN_EXT);
            addChars(units, CYRILLIC);
            addChars(units, ARABIC);
            addChars(units, DEVANAGARI);
            addChars(units, BENGALI);
            addChars(units, GREEK);
            addChars(units, HEBREW);
            addChars(units, THAI);
            addChars(units, CJK_SAMPLE);
        }

        if (options.useEmojis) {
             int i = 0;
             while (i < EMOJIS.length()) {
                 int cp = EMOJIS.codePointAt(i);
                 int charCount = Character.charCount(cp);
                 units.add(EMOJIS.substring(i, i + charCount));
                 i += charCount;
             }
        }

        if (options.excludeAmbiguous) {
            units.removeIf(s -> AMBIGUOUS.contains(s));
        }

        if (units.isEmpty()) return "";

        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();

        while (password.length() < options.length) {
            String unit = units.get(random.nextInt(units.size()));
            password.append(unit);
        }

        return password.toString();
    }

    private static void addChars(List<String> units, String chars) {
        for (char c : chars.toCharArray()) {
            units.add(String.valueOf(c));
        }
    }
}
