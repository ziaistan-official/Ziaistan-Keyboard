package juloo.keyboard2;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Switch;
import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.PopupMenu;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.Intent;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class SidePanelSettingsActivity extends Activity {

    private SharedPreferences prefs;
    private Typeface specialFont;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(this);
        String themeName = "system";
        try {
            themeName = p.getString("app_theme", "system");
        } catch (ClassCastException e) {

        }

        int themeId = R.style.settingsTheme;
        switch (themeName) {
            case "ocean": themeId = R.style.AppTheme_Ocean; break;
            case "forest": themeId = R.style.AppTheme_Forest; break;
            case "sunset": themeId = R.style.AppTheme_Sunset; break;
            case "midnight": themeId = R.style.AppTheme_Midnight; break;
            default: themeId = R.style.settingsTheme; break;
        }
        setTheme(themeId);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_side_panel_settings);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        try {
            specialFont = Typeface.createFromAsset(getAssets(), "special_font.ttf");
        } catch (Exception e) {
            specialFont = Typeface.DEFAULT;
        }

        android.widget.Spinner spinner = findViewById(R.id.spinner_animation_style);


        String[] styles = {
            "Classic", "Fluid", "Elastic", "Ribbon", "Arrow",
            "Plasma", "Vortex", "Magnet", "Chain", "Beam", "Pixel",
            "Jelly", "Ghost", "Lava", "Taffy", "Snake", "Worm", "Gum", "Roots", "Lightning", "Blob",

            "Venom", "Tesla Coil", "Zipper", "Portal", "Origami", "Sonar", "Blackhole", "Heavy Chain", "Matrix", "Arrow Cluster",

            "Cyberpunk", "Liquid Glass", "Mechanical RGB", "Magma Ember", "Ink Parchment",
            "Cosmic Nebula", "Sakura Garden", "Retro 8Bit", "Golden Era", "Deep Ocean",
            "Neon Rain", "Candy Crush", "Steampunk", "Holographic", "Spirit Realm",
            "Golden Luxury", "Sakura Breeze", "Bioluminescence", "Retro Arcade", "Crystal Prism",
            "Vaporwave", "Noir Rain", "Paper Cutout", "Star Field", "Gears",

            "Random"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, styles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        String currentStyle = "Classic";
        try {
            currentStyle = prefs.getString("side_panel_animation_style", "Classic");
        } catch (ClassCastException e) {

        }

        int selection = 0;
        for (int i = 0; i < styles.length; i++) {

            String s1 = styles[i].replace(" ", "_");
            String s2 = currentStyle.replace(" ", "_");
            if (s1.equalsIgnoreCase(s2)) {
                selection = i;
                break;
            }
        }
        spinner.setSelection(selection);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                prefs.edit().putString("side_panel_animation_style", styles[position].replace(" ", "_").toUpperCase()).apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });


        Switch switchEnable = findViewById(R.id.switch_enable_side_panel);
        switchEnable.setChecked(prefs.getBoolean("enable_side_panel", false));
        switchEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("enable_side_panel", isChecked).apply();
        });


        LinearLayout mainContainer = (LinearLayout) ((ViewGroup)switchEnable.getParent());


        boolean hasHaptic = false;
        for(int i=0; i<mainContainer.getChildCount(); i++) {
             View v = mainContainer.getChildAt(i);
             if (v instanceof Switch && "Haptic Feedback".equals(((Switch)v).getText())) hasHaptic = true;
        }

        if (!hasHaptic) {
            Switch switchHaptic = new Switch(this);
            switchHaptic.setText("Haptic Feedback");
            switchHaptic.setTextSize(16);
            switchHaptic.setPadding(0, 16, 0, 16);
            switchHaptic.setChecked(prefs.getBoolean("side_panel_haptic", true));
            switchHaptic.setOnCheckedChangeListener((v, isChecked) -> prefs.edit().putBoolean("side_panel_haptic", isChecked).apply());
            mainContainer.addView(switchHaptic, 1);

            Switch switchSound = new Switch(this);
            switchSound.setText("Sound Feedback");
            switchSound.setTextSize(16);
            switchSound.setPadding(0, 16, 0, 16);
            switchSound.setChecked(prefs.getBoolean("side_panel_sound", true));
            switchSound.setOnCheckedChangeListener((v, isChecked) -> prefs.edit().putBoolean("side_panel_sound", isChecked).apply());
            mainContainer.addView(switchSound, 2);
        }


        new Thread(() -> ActionRegistry.getAllActions(this)).start();

        setupTabs("left", 5, findViewById(R.id.container_left_tabs));
        setupTabs("right", 5, findViewById(R.id.container_right_tabs));
        setupTabs("bottom", 3, findViewById(R.id.container_bottom_tabs));
    }

    private void setupTabs(String side, int count, LinearLayout container) {
        if (container.getChildCount() > 0) return;

        for (int i = 0; i < count; i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 8, 0, 8);

            TextView label = new TextView(this);
            label.setText("Tab " + (i + 1));
            label.setTextColor(0xFFFFFFFF);
            label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(label);

            Button editBtn = new Button(this);
            editBtn.setText("Edit");
            editBtn.setOnClickListener(v -> showActionListDialog(side, index));
            row.addView(editBtn);

            container.addView(row);
        }
    }

    private String fastActionConfig = null;

    private void showActionListDialog(String side, int index) {
        String key = "side_panel_" + side + "_" + index;
        String glyphKey = key + "_glyph";
        String fastKey = key + "_fast";

        String currentConfig = prefs.getString(key, "");
        List<String> actions = new ArrayList<>();
        if (!currentConfig.isEmpty()) {
            String[] parts = currentConfig.split(",");
            for (String p : parts) actions.add(p);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit " + side + " Tab " + (index + 1));

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_side_action, null);
        builder.setView(view);

        final ListView listActions = view.findViewById(R.id.list_configured_actions);
        final Button btnAdd = view.findViewById(R.id.btn_add_action);
        final Button btnCopyFrom = view.findViewById(R.id.btn_copy_from);
        final TextView labelFast = view.findViewById(R.id.label_fast_action);
        final Button btnAssignFast = view.findViewById(R.id.btn_assign_fast_action);
        final EditText inputGlyph = view.findViewById(R.id.input_tab_glyph);

        final LinearLayout editorContainer = view.findViewById(R.id.editor_container);
        if (editorContainer != null) editorContainer.setVisibility(View.GONE);

        final ArrayAdapter<String> listAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, actions) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                String item = getItem(position);
                String display = item;
                String glyph = "";
                if (item != null && item.contains(":")) {
                    String[] parts = item.split(":", 4);
                    display = parts.length > 2 ? parts[2] : parts[1];
                    glyph = parts.length > 3 ? parts[3] : "";
                }
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTypeface(specialFont);
                tv.setText((glyph.isEmpty() ? "" : glyph + " ") + display);
                return tv;
            }
        };
        listActions.setAdapter(listAdapter);

        fastActionConfig = prefs.getString(fastKey, null);
        if (fastActionConfig != null) {
            String label = fastActionConfig;
            String glyph = "";
            if (fastActionConfig.contains(":")) {
                String[] parts = fastActionConfig.split(":", 4);
                label = (parts.length > 2) ? parts[2] : parts[1];
                glyph = (parts.length > 3) ? parts[3] : "";
            }
            labelFast.setText((glyph.isEmpty() ? "" : glyph + " ") + label);
            labelFast.setTypeface(specialFont);
        }

        inputGlyph.setText(prefs.getString(glyphKey, ""));
        inputGlyph.setTypeface(specialFont);

        btnAssignFast.setOnClickListener(v -> {
            showActionPickerDialog(selectedAction -> {
                String safeLabel = selectedAction.label.replace(":", "-");
                String config = selectedAction.id + ":" + safeLabel;
                showActionDetailDialog(config, updated -> {
                    fastActionConfig = updated;
                    String display = updated;
                    String glyph = "";
                    if (updated.contains(":")) {
                        String[] parts = updated.split(":", 4);
                        display = parts.length > 2 ? parts[2] : parts[1];
                        glyph = parts.length > 3 ? parts[3] : "";
                    }
                    labelFast.setText((glyph.isEmpty() ? "" : glyph + " ") + display);
                    labelFast.setTypeface(specialFont);
                });
            });
        });

        btnCopyFrom.setOnClickListener(v -> {
            showCopyFromDialog((sourceSide, sourceIndex) -> {
                String sKey = "side_panel_" + sourceSide + "_" + sourceIndex;
                String sGlyphKey = sKey + "_glyph";
                String sFastKey = sKey + "_fast";

                String sActions = prefs.getString(sKey, "");
                String sGlyph = prefs.getString(sGlyphKey, "");
                String sFast = prefs.getString(sFastKey, null);

                actions.clear();
                if (!sActions.isEmpty()) {
                    for (String part : sActions.split(",")) actions.add(part);
                }
                listAdapter.notifyDataSetChanged();

                inputGlyph.setText(sGlyph);
                fastActionConfig = sFast;
                if (sFast != null) {
                    String label = sFast;
                    if (sFast.contains(":")) {
                        String[] parts = sFast.split(":", 4);
                        label = parts.length > 2 ? parts[2] : parts[1];
                    }
                    labelFast.setText(label);
                } else {
                    labelFast.setText("None");
                }
            });
        });

        labelFast.setOnClickListener(v -> {
            if (fastActionConfig != null) {
                showActionDetailDialog(fastActionConfig, updated -> {
                    fastActionConfig = updated;
                    String display = updated;
                    String glyph = "";
                    if (updated.contains(":")) {
                        String[] parts = updated.split(":", 4);
                        display = parts.length > 2 ? parts[2] : parts[1];
                        glyph = parts.length > 3 ? parts[3] : "";
                    }
                    labelFast.setText((glyph.isEmpty() ? "" : glyph + " ") + display);
                });
            }
        });

        listActions.setOnItemClickListener((parent, view1, position, id) -> {
            showActionDetailDialog(actions.get(position), updatedConfig -> {
                actions.set(position, updatedConfig);
                listAdapter.notifyDataSetChanged();
            });
        });

        listActions.setOnItemLongClickListener((parent, view1, position, id) -> {
            PopupMenu popup = new PopupMenu(this, view1);
            popup.getMenu().add("Edit");
            popup.getMenu().add("Delete");

            popup.setOnMenuItemClickListener(item -> {
                if ("Edit".equals(item.getTitle())) {
                    showActionDetailDialog(actions.get(position), updatedConfig -> {
                        actions.set(position, updatedConfig);
                        listAdapter.notifyDataSetChanged();
                    });
                } else if ("Delete".equals(item.getTitle())) {
                    actions.remove(position);
                    listAdapter.notifyDataSetChanged();
                }
                return true;
            });
            popup.show();
            return true;
        });

        btnAdd.setOnClickListener(v -> {
            if (actions.size() >= 6) return;
            showActionPickerDialog(selectedAction -> {
                String safeLabel = selectedAction.label.replace(":", "-");
                String configString = selectedAction.id + ":" + safeLabel;
                showActionDetailDialog(configString, finalConfig -> {
                    actions.add(finalConfig);
                    listAdapter.notifyDataSetChanged();
                });
            });
        });

        builder.setPositiveButton("Save All", (d, w) -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < actions.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(actions.get(i));
            }
            SharedPreferences.Editor edit = prefs.edit();
            edit.putString(key, sb.toString());
            edit.putString(glyphKey, inputGlyph.getText().toString());
            edit.putString(fastKey, fastActionConfig);
            edit.apply();
        });
        builder.setNegativeButton("Cancel", null);

        builder.show();
    }

    private interface OnActionSelected {
        void onSelect(ActionRegistry.ActionDefinition action);
    }

    private interface OnActionUpdated {
        void onUpdate(String updatedConfig);
    }

    private interface OnTabSelected {
        void onSelect(String side, int index);
    }

    private void showCopyFromDialog(OnTabSelected callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Copy From Tab...");

        List<String> tabNames = new ArrayList<>();
        final List<String[]> tabData = new ArrayList<>();

        String[] sides = {"left", "right", "bottom"};
        int[] counts = {5, 5, 3};

        for (int s = 0; s < sides.length; s++) {
            for (int i = 0; i < counts[s]; i++) {
                tabNames.add(sides[s].toUpperCase().charAt(0) + sides[s].substring(1) + " Tab " + (i + 1));
                tabData.add(new String[]{sides[s], String.valueOf(i)});
            }
        }

        builder.setItems(tabNames.toArray(new String[0]), (d, which) -> {
            String[] data = tabData.get(which);
            callback.onSelect(data[0], Integer.parseInt(data[1]));
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showActionDetailDialog(String config, OnActionUpdated callback) {
        String[] parts = config.split(":", 4);
        String id = parts[0];
        String label = parts.length > 2 ? parts[2] : (parts.length > 1 ? parts[1] : id);
        String glyph = parts.length > 3 ? parts[3] : "";

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Action Details");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        TextView labelTitle = new TextView(this);
        labelTitle.setText("Custom Label");
        layout.addView(labelTitle);

        EditText inputLabel = new EditText(this);
        inputLabel.setText(label);
        layout.addView(inputLabel);

        TextView glyphTitle = new TextView(this);
        glyphTitle.setText("Custom Glyph (Icon)");
        glyphTitle.setPadding(0, 20, 0, 0);
        layout.addView(glyphTitle);

        EditText inputGlyph = new EditText(this);
        inputGlyph.setText(glyph);
        inputGlyph.setTypeface(specialFont);
        layout.addView(inputGlyph);

        builder.setView(layout);

        builder.setPositiveButton("OK", (d, w) -> {
            String newLabel = inputLabel.getText().toString().replace(":", "-");
            String newGlyph = inputGlyph.getText().toString().replace(":", "-");

            // Extract base ID and data from original config
            String base;
            if (config.contains(":")) {
                int secondColon = config.indexOf(':', config.indexOf(':') + 1);
                if (secondColon == -1) base = config;
                else base = config.substring(0, secondColon);
            } else base = config;

            callback.onUpdate(base + ":" + newLabel + ":" + newGlyph);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showActionPickerDialog(OnActionSelected callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_action_picker, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        EditText search = view.findViewById(R.id.search_input);
        ListView list = view.findViewById(R.id.list_actions);
        Button cancel = view.findViewById(R.id.btn_cancel);

        ArrayAdapter<ActionRegistry.ActionDefinition> adapter = new ArrayAdapter<ActionRegistry.ActionDefinition>(this, android.R.layout.simple_list_item_1, new ArrayList<>()) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                ActionRegistry.ActionDefinition item = getItem(position);

                boolean showHeader = false;
                if (position == 0) {
                    showHeader = true;
                } else {
                    ActionRegistry.ActionDefinition prev = getItem(position - 1);
                    if (prev.category != item.category) showHeader = true;
                }

                LinearLayout root = new LinearLayout(getContext());
                root.setOrientation(LinearLayout.VERTICAL);

                if (showHeader) {
                    TextView header = new TextView(getContext());
                    header.setTypeface(null, android.graphics.Typeface.BOLD);
                    header.setTextColor(Color.CYAN);
                    header.setPadding(16, 16, 16, 8);
                    header.setTextSize(14);
                    header.setText(item.category.label);
                    root.addView(header);
                }

                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setPadding(16, 16, 16, 16);

                TextView text = new TextView(getContext());
                text.setTextColor(Color.WHITE);
                text.setTextSize(16);
                text.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                String icon = ActionRegistry.getIconForAction(item.label);
                text.setText(icon + " " + item.label);
                row.addView(text);

                Button testBtn = new Button(getContext());
                testBtn.setText("Test");
                testBtn.setFocusable(false);
                testBtn.setOnClickListener(v -> {
                    SidePanelController spc = SidePanelController.getInstance();
                    if (spc != null) {
                        String configString = item.id + ":" + item.label.replace(":", "-");
                        SidePanelController.ActionItem ai = spc.parseAction(configString);
                        if (ai != null) spc.performAction(ai);
                    } else {
                        Toast.makeText(getContext(), "Side Panel Service not active", Toast.LENGTH_SHORT).show();
                    }
                });
                row.addView(testBtn);
                root.addView(row);

                return root;
            }
        };
        list.setAdapter(adapter);

        new Thread(() -> {
            List<ActionRegistry.ActionDefinition> allActions = ActionRegistry.getAllActions(this);
            runOnUiThread(() -> {
                adapter.clear();
                adapter.addAll(allActions);
                adapter.notifyDataSetChanged();
            });
        }).start();

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                adapter.getFilter().filter(s);
            }
        });

        list.setOnItemClickListener((parent, view1, position, id) -> {
            callback.onSelect(adapter.getItem(position));
            dialog.dismiss();
        });

        cancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
