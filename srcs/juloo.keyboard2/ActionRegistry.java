package juloo.keyboard2;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.net.Uri;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActionRegistry {

    public enum ActionCategory {
        BUTTONS("Keyboard Buttons"),
        KEY_VALUES("Key Values"),
        GLOBAL("Global Actions"),
        TOOLS("Tools & Media"),
        SETTINGS("Settings Shortcuts"),
        APPS("All Activities");

        public final String label;
        ActionCategory(String label) { this.label = label; }
    }

    public static class ActionDefinition {
        public String id;
        public String label;
        public ActionCategory category;
        public String data;

        public ActionDefinition(String id, String label, ActionCategory category, String data) {
            this.id = id;
            this.label = label;
            this.category = category;
            this.data = data;
        }

        @Override public String toString() { return label; }
    }


    private static final Map<String, String> ICON_MAP = new HashMap<>();

    public static Map<String, String> getIconMap() {
        return ICON_MAP;
    }

    static {

        ICON_MAP.put("Back", "←"); // Using keyboard font PUA symbols
        ICON_MAP.put("Home", "\uE00B");
        ICON_MAP.put("Recents", "↔");
        ICON_MAP.put("Notifications", "⏱");
        ICON_MAP.put("Quick Settings", "⌯");
        ICON_MAP.put("Power Menu", "⏻");
        ICON_MAP.put("Split Screen", "◫");
        ICON_MAP.put("Lock Screen", "⚷");
        ICON_MAP.put("Screenshot", "⍞");


        ICON_MAP.put("Toggle Wi-Fi", "ᯤ");
        ICON_MAP.put("Toggle Bluetooth", "ᛒ");
        ICON_MAP.put("Network Operators", "⇅");
        ICON_MAP.put("APN Settings", "⇈");
        ICON_MAP.put("Data Usage", "⇋");
        ICON_MAP.put("Radio Info", "∐");
        ICON_MAP.put("4G Switcher", "4g");


        ICON_MAP.put("Cycle Side Panel Theme", "⎚");
        ICON_MAP.put("Toggle Flashlight", "✶");
        ICON_MAP.put("Toggle Rotation Lock", "↻");
        ICON_MAP.put("Kill App", "╳");
        ICON_MAP.put("Kill All", "☢");
        ICON_MAP.put("Battery Saver", "🔋");


        ICON_MAP.put("Media Play/Pause", "⏯");
        ICON_MAP.put("Media Next", "⏭");
        ICON_MAP.put("Media Previous", "⏮");
        ICON_MAP.put("Volume Up", "🔊");
        ICON_MAP.put("Volume Down", "🔉");
        ICON_MAP.put("Mute Volume", "🔇");


        ICON_MAP.put("Paste", "\uE032");
        ICON_MAP.put("Copy", "\uE030");
        ICON_MAP.put("Double Space (No Correct)", "␣␣");
        ICON_MAP.put("Cut", "\uE031");
        ICON_MAP.put("Delete", "\uE011");
        ICON_MAP.put("Enter", "\uE00E");
        ICON_MAP.put("Search", "🔍");
        ICON_MAP.put("Voice", "\uE015");
        ICON_MAP.put("Shift", "\uE00A");
        ICON_MAP.put("Space", "\uE00D");
        ICON_MAP.put("Settings", "\uE004");


        ICON_MAP.put("Camera", "📷");
        ICON_MAP.put("Clock", "⏰");
        ICON_MAP.put("Calendar", "📅");
        ICON_MAP.put("Calculator", "🧮");
        ICON_MAP.put("Contacts", "👥");
        ICON_MAP.put("Gallery", "🖼");
        ICON_MAP.put("Maps", "🗺");
        ICON_MAP.put("Music", "🎵");
        ICON_MAP.put("Email", "📧");
        ICON_MAP.put("Browser", "🌍");
        ICON_MAP.put("Settings", "⚙️");
        ICON_MAP.put("Phone", "📞");
        ICON_MAP.put("Message", "💬");


        ICON_MAP.put("Star", "⭐");
        ICON_MAP.put("Heart", "❤️");
        ICON_MAP.put("Check", "✓");
        ICON_MAP.put("Cross", "✕");
        ICON_MAP.put("Warning", "⚠️");
        ICON_MAP.put("Info", "ℹ️");
        ICON_MAP.put("Menu", "🍔");
        ICON_MAP.put("More", "⋯");
        ICON_MAP.put("Edit", "✏️");
        ICON_MAP.put("Trash", "🗑");
        ICON_MAP.put("Save", "💾");
        ICON_MAP.put("Download", "📥");
        ICON_MAP.put("Upload", "📤");
        ICON_MAP.put("Link", "🔗");
        ICON_MAP.put("Lock", "🔒");
        ICON_MAP.put("Unlock", "🔓");
        ICON_MAP.put("Eye", "👁");
        ICON_MAP.put("Eye Off", "🚫");
        ICON_MAP.put("Filter", "Y");
        ICON_MAP.put("Folder", "📁");
        ICON_MAP.put("File", "📄");
        ICON_MAP.put("Image", "🖼");
        ICON_MAP.put("Video", "🎥");
        ICON_MAP.put("Sound", "🔊");
        ICON_MAP.put("Chart", "📈");
        ICON_MAP.put("Location", "📍");
        ICON_MAP.put("Pin", "📌");
        ICON_MAP.put("Flag", "🚩");
        ICON_MAP.put("Chat", "💬");
        ICON_MAP.put("User", "👤");
        ICON_MAP.put("Group", "👥");
        ICON_MAP.put("Add", "➕");
        ICON_MAP.put("Remove", "➖");
        ICON_MAP.put("Play", "▶️");
        ICON_MAP.put("Pause", "⏸");
        ICON_MAP.put("Stop", "⏹");
        ICON_MAP.put("Record", "⏺");
        ICON_MAP.put("Shuffle", "🔀");
        ICON_MAP.put("Loop", "🔁");
        ICON_MAP.put("Brightness", "🔆");
        ICON_MAP.put("Dark", "🌙");
        ICON_MAP.put("Keyboard", "⌨️");
        ICON_MAP.put("Mouse", "🖱");
        ICON_MAP.put("Smartphone", "📱");
        ICON_MAP.put("Desktop", "🖥");
        ICON_MAP.put("Watch", "⌚");
        ICON_MAP.put("Game", "🎮");
        ICON_MAP.put("Cloud", "☁️");
        ICON_MAP.put("Fire", "🔥");
    }

    public static String getIconForAction(String label) {
        if (label == null) return "⚡";
        if (ICON_MAP.containsKey(label)) return ICON_MAP.get(label);

        String labelLower = label.toLowerCase();
        for (Map.Entry<String, String> entry : ICON_MAP.entrySet()) {
            if (labelLower.contains(entry.getKey().toLowerCase())) return entry.getValue();
        }

        return "⚡";
    }

    public static String getActionGlyph(KeyValue kv) {
        if (kv == null) return null;

        String action = KeyValue.getRawActionName(kv);

        switch (action.toLowerCase()) {
            case "copy": return "⎙";
            case "paste": return "⎘";
            case "cut": return "✁";
            case "select_all": return "⇔";
            case "undo": return "↶";
            case "redo": return "↷";
            case "backspace": return "⌫";
            case "delete": return "⌦";
            case "enter": return "↲";
            case "tab": return "⇆";
            case "shift": return "⇧";
            case "capslock": return "⇪";
            case "esc": return "⎋";
            case "space": return "␣";
            case "up": return "↑";
            case "down": return "↓";
            case "left": return "←";
            case "right": return "→";
            case "home": return "Home";
            case "end": return "End";
            case "page_up": return "PUp";
            case "page_down": return "PDown";
            case "voice_typing": return "🎙";
            case "switch_emoji": return "☻";
            case "switch_clipboard": return "⌨";
            case "switch_back_clipboard": return "⇠";
            case "switch_back_emoji": return "⇠";
            case "search_replace": return "⌕";
            case "config": return "⚙";
            case "mouse_pad": return "⍞";
            case "typing_history": return "♲";
            case "switch_glyphs": return "⌘";
            case "delete_word": return "⌫";
            case "move_word_backward_1": return "↞";
            case "move_word_forward_1": return "↠";
            case "paste_as_plain_text": return "⎘";
            case "switch_text": return "Ⓐ";
            case "switch_numeric": return "➊";
            case "change_method": return "Abc";
            case "caps_lock": return "⇪";
            case "open_password_manager": return "⚙";
            case "generate_password": return "⚄";
            case "autofill_password": return "✎";
            case "kill_app": return "╳";
            case "kill_all": return "☢";
            case "flashlight": return "✹";
            case "rotation_lock": return "↻";
            case "wifi_toggle": return "ᯤ";
            case "bluetooth_toggle": return "ᛒ";
            case "volume_up": return "⧈";
            case "volume_down": return "⧇";
            case "volume_mute": return "⧆";
            case "media_play_pause": return "⏯";
            case "media_next": return "⏭";
            case "media_previous": return "⏮";
            case "switch_forward": return "⟫";
            case "switch_backward": return "⟪";
            case "switch_greekmath": return "π";
            case "capslock_lock": return "⇪";
            case "selection_mode": return "⁞";
            case "selection_cancel": return "✕";
            case "share": return "☍";
            case "assist": return "✦";
            case "autofill": return "✎";
            case "forward_delete_word": return "⌦";
            case "select_all,delete_word": return "∅";
            case "select_all,copy": return "❐";
            case "select_all,cut": return "✁";
            case "double_space": return "␣␣";
            default:
                if (kv.getKind() == KeyValue.Kind.Editing || kv.getKind() == KeyValue.Kind.Event || kv.getKind() == KeyValue.Kind.Keyevent || kv.getKind() == KeyValue.Kind.Modifier || kv.getKind() == KeyValue.Kind.Macro) {
                    return "⚙";
                }
                return null;
        }
    }

    private static List<ActionDefinition> cachedActions = null;

    public static synchronized List<ActionDefinition> getAllActions(Context context) {
        if (cachedActions != null) return cachedActions;
        List<ActionDefinition> list = new ArrayList<>();

        // 1. Keyboard Buttons (Priority 1)
        addButton(list, "backspace", "Backspace (Delete)");
        addButton(list, "enter", "Enter / Return");
        addButton(list, "space", "Spacebar");
        addButton(list, "tab", "Tab");
        addButton(list, "shift", "Shift");
        addButton(list, "capslock", "Caps Lock");
        addButton(list, "esc", "Escape");
        addButton(list, "up", "Arrow Up");
        addButton(list, "down", "Arrow Down");
        addButton(list, "left", "Arrow Left");
        addButton(list, "right", "Arrow Right");
        addButton(list, "home", "Home");
        addButton(list, "end", "End");
        addButton(list, "page_up", "Page Up");
        addButton(list, "page_down", "Page Down");
        addButton(list, "delete", "Forward Delete");

        // 2. KeyValues from KeyValue.java (Priority 2)
        addKeyValues(list);

        // 3. Global Actions
        addGlobal(list, AccessibilityService.GLOBAL_ACTION_BACK, "Back (System)");
        addGlobal(list, AccessibilityService.GLOBAL_ACTION_HOME, "Home");
        addGlobal(list, AccessibilityService.GLOBAL_ACTION_RECENTS, "Recents");
        addGlobal(list, AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "Notifications");
        addGlobal(list, AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, "Quick Settings");
        addGlobal(list, AccessibilityService.GLOBAL_ACTION_POWER_DIALOG, "Power Menu");
        addGlobal(list, AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN, "Split Screen");
        addGlobal(list, AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, "Lock Screen");
        addGlobal(list, AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, "Screenshot");
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            addGlobal(list, AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE, "Dismiss Notifications");
        }


        addCustom(list, "kill_app", "Kill Current App");
        addCustom(list, "kill_all", "Kill All Foreground Apps");
        addCustom(list, "prev_app", "Previous App");
        addCustom(list, "next_app", "Next App");
        addCustom(list, "flashlight", "Toggle Flashlight");
        addCustom(list, "rotation_lock", "Toggle Rotation Lock");
        addCustom(list, "wifi_toggle", "Toggle Wi-Fi");
        addCustom(list, "bluetooth_toggle", "Toggle Bluetooth");
        addCustom(list, "cycle_theme", "Cycle Side Panel Theme");


        addKey(list, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Media Play/Pause");
        addKey(list, KeyEvent.KEYCODE_MEDIA_NEXT, "Media Next");
        addKey(list, KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Media Previous");
        addKey(list, KeyEvent.KEYCODE_VOLUME_UP, "Volume Up");
        addKey(list, KeyEvent.KEYCODE_VOLUME_DOWN, "Volume Down");
        addKey(list, KeyEvent.KEYCODE_VOLUME_MUTE, "Mute Volume");
        addKey(list, KeyEvent.KEYCODE_BRIGHTNESS_UP, "Brightness Up");
        addKey(list, KeyEvent.KEYCODE_BRIGHTNESS_DOWN, "Brightness Down");
        addKey(list, KeyEvent.KEYCODE_PASTE, "Paste");
        addKey(list, KeyEvent.KEYCODE_COPY, "Copy");
        addKey(list, KeyEvent.KEYCODE_CUT, "Cut");


        addIntent(list, Intent.ACTION_VOICE_COMMAND, "Voice Assistant");
        addIntent(list, Intent.ACTION_WEB_SEARCH, "Web Search");
        addIntent(list, MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA, "Camera");
        addIntent(list, AlarmClock.ACTION_SHOW_ALARMS, "Clock / Alarms");
        addIntent(list, Intent.ACTION_MAIN, "Calculator", "android.intent.category.APP_CALCULATOR");
        addIntent(list, Intent.ACTION_MAIN, "Calendar", "android.intent.category.APP_CALENDAR");
        addIntent(list, Intent.ACTION_MAIN, "Browser", "android.intent.category.APP_BROWSER");


        addSetting(list, Settings.ACTION_SETTINGS, "Settings (Main)");
        addSetting(list, Settings.ACTION_WIFI_SETTINGS, "Wi-Fi Settings");
        addSetting(list, Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth Settings");
        addSetting(list, Settings.ACTION_DISPLAY_SETTINGS, "Display Settings");
        addSetting(list, Settings.ACTION_BATTERY_SAVER_SETTINGS, "Battery Saver");
        addSetting(list, Settings.ACTION_NETWORK_OPERATOR_SETTINGS, "Network Operators");
        addSetting(list, Settings.ACTION_APN_SETTINGS, "APN Settings");
        if (android.os.Build.VERSION.SDK_INT >= 24) {
             addSetting(list, Settings.ACTION_DATA_USAGE_SETTINGS, "Data Usage");
        }


        PackageManager pm = context.getPackageManager();
        List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_ACTIVITIES);

        for (PackageInfo pkg : packages) {
            if (pkg.activities != null) {
                for (ActivityInfo activity : pkg.activities) {
                    if (activity.exported) {
                        CharSequence labelSeq = activity.loadLabel(pm);
                        String label = labelSeq != null ? labelSeq.toString() : activity.name;
                        CharSequence appLabelSeq = pkg.applicationInfo.loadLabel(pm);
                        String appLabel = appLabelSeq != null ? appLabelSeq.toString() : pkg.packageName;

                        String fullLabel = appLabel + ": " + label;
                        if (fullLabel.length() > 50) fullLabel = fullLabel.substring(0, 50) + "...";

                        list.add(new ActionDefinition(
                            "app:" + pkg.packageName + "/" + activity.name,
                            fullLabel,
                            ActionCategory.APPS,
                            pkg.packageName + "/" + activity.name
                        ));
                    }
                }
            }
        }

        Collections.sort(list, (a, b) -> {
            int catDiff = a.category.ordinal() - b.category.ordinal();
            if (catDiff != 0) return catDiff;
            if (a.category == ActionCategory.BUTTONS || a.category == ActionCategory.KEY_VALUES) {
                // Keep insertion order for these
                return 0;
            }
            return a.label.compareToIgnoreCase(b.label);
        });

        cachedActions = list;
        return list;
    }

    private static void addButton(List<ActionDefinition> list, String id, String label) {
        list.add(new ActionDefinition(id, label, ActionCategory.BUTTONS, id));
    }

    private static void addKeyValues(List<ActionDefinition> list) {
        // Common Customization Buttons -> Category BUTTONS (Toppest)
        addButton(list, "config", "Keyboard Settings");
        addButton(list, "cycle_theme", "Cycle Themes");
        addButton(list, "mouse_pad", "Mouse Pad");
        addButton(list, "voice_typing", "Voice Typing");
        addButton(list, "clipboard", "Clipboard Pane");
        addButton(list, "search_replace", "Find & Replace");
        addButton(list, "double_space", "Double Space (No Correct)");

        // Special Events
        for (KeyValue.Event e : KeyValue.Event.values()) {
            String name = e.name().toLowerCase();
            if (findAction(list, name)) continue;
            list.add(new ActionDefinition(name, "Event: " + e.name(), ActionCategory.KEY_VALUES, name));
        }
        // Editing Actions
        for (KeyValue.Editing e : KeyValue.Editing.values()) {
            String name = e.name().toLowerCase();
            if (findAction(list, name)) continue;
            list.add(new ActionDefinition(name, "Edit: " + e.name(), ActionCategory.KEY_VALUES, name));
        }
        // Modifiers
        for (KeyValue.Modifier m : KeyValue.Modifier.values()) {
            String name = m.name().toLowerCase();
            if (findAction(list, name)) continue;
            list.add(new ActionDefinition(name, "Mod: " + m.name(), ActionCategory.KEY_VALUES, name));
        }
    }

    private static boolean findAction(List<ActionDefinition> list, String id) {
        for (ActionDefinition ad : list) if (ad.id.equals(id)) return true;
        return false;
    }

    private static void addGlobal(List<ActionDefinition> list, int id, String label) {
        list.add(new ActionDefinition("global:" + id, label, ActionCategory.GLOBAL, String.valueOf(id)));
    }

    private static void addKey(List<ActionDefinition> list, int code, String label) {
        list.add(new ActionDefinition("key:" + code, label, ActionCategory.TOOLS, String.valueOf(code)));
    }

    private static void addIntent(List<ActionDefinition> list, String action, String label) {
        list.add(new ActionDefinition("intent:" + action, label, ActionCategory.TOOLS, action));
    }

    private static void addIntent(List<ActionDefinition> list, String action, String label, String category) {
        list.add(new ActionDefinition("intent_cat:" + action + "|" + category, label, ActionCategory.TOOLS, action + "|" + category));
    }

    private static void addSetting(List<ActionDefinition> list, String action, String label) {
        list.add(new ActionDefinition("setting:" + action, label, ActionCategory.SETTINGS, action));
    }

    private static void addCustom(List<ActionDefinition> list, String command, String label) {
        list.add(new ActionDefinition("custom:" + command, label, ActionCategory.TOOLS, command));
    }
}
