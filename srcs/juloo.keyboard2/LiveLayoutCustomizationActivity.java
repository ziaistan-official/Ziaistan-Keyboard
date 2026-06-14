package juloo.keyboard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.DialogInterface;
import android.net.Uri;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import juloo.keyboard2.prefs.LayoutsPreference;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.HashMap;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.function.Consumer;
import java.util.Map;

public class LiveLayoutCustomizationActivity extends Activity {

    public boolean isSelectModeActive() { return selectMode; }

    private Keyboard2View liveKeyboardView;
    private KeyboardData currentEditingLayout;
    private Stack<KeyboardData> undoStack = new Stack<>();
    private Stack<KeyboardData> redoStack = new Stack<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Glyph.init(getResources());
        Config config = Config.globalConfig();
        if (config != null) {
            setTheme(config.theme);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_layout_customization);

        liveKeyboardView = findViewById(R.id.live_keyboard_view);

        // Initial setup
        int layoutIdx = config.get_current_layout();
        if (layoutIdx >= 0 && layoutIdx < config.layouts.size()) {
            currentEditingLayout = config.layouts.get(layoutIdx);
        }

        if (currentEditingLayout == null) {
             currentEditingLayout = KeyboardData.load(getResources(), R.xml.latn_qwerty_us);
        }
        liveKeyboardView.setKeyboard(currentEditingLayout);

        // Restore from input pref or autosave
        String inputXml = config.globalPrefs().getString("layout_customizer_input", null);
        if (inputXml != null) {
            try {
                currentEditingLayout = KeyboardData.load_string_exn(inputXml);
                // Materialize surroundings so manual edits don't lose calculated ones
                if (currentEditingLayout.surroundings == null) {
                    Map<Character, List<Character>> map = KeyboardLayoutAnalyzer.getAdjacencyMap(currentEditingLayout);
                    currentEditingLayout = new KeyboardData(currentEditingLayout.rows, currentEditingLayout.keysWidth, currentEditingLayout.modmap, currentEditingLayout.script, currentEditingLayout.numpad_script, currentEditingLayout.name, currentEditingLayout.bottom_row, currentEditingLayout.embedded_number_row, currentEditingLayout.locale_extra_keys, currentEditingLayout.wordlist, map);
                }
                liveKeyboardView.setKeyboard(currentEditingLayout);
            } catch (Exception e) {}
        } else {
            String autosave = config.globalPrefs().getString("layout_autosave", null);
            if (autosave != null) {
                try {
                    currentEditingLayout = KeyboardData.load_string_exn(autosave);
                    liveKeyboardView.setKeyboard(currentEditingLayout);
                    Toast.makeText(this, "Restored previous session", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {}
            }
        }

        liveKeyboardView.setEditMode(true, new Keyboard2View.EditCallback() {
            @Override public void onKeyClick(KeyboardData.Key key) {
                if (surroundingMode && surroundingTarget != null) {
                    toggleSurrounding(surroundingTarget, key);
                    return;
                }
                int[] pos = findKeyIndices(key);
                if (pos != null) showKeyEditor(pos[0], pos[1]);
            }
            @Override public void onKeyDoubleClick(KeyboardData.Key key) {
                int[] pos = findKeyIndices(key);
                if (pos != null) quickEditKey(pos[0], pos[1]);
            }
            @Override public void onKeyLongPress(KeyboardData.Key key) { startKeyDrag(key); }
            @Override public void onRowPinch(KeyboardData.Row row, float scale) {
                int idx = currentEditingLayout.rows.indexOf(row);
                if (idx != -1) resizeRow(idx, scale);
            }
            @Override public void onRowDrag(KeyboardData.Row row, float dx, float dy) {
                int idx = currentEditingLayout.rows.indexOf(row);
                if (idx != -1) moveRow(idx, dx, dy);
            }
            @Override public void onSelectionChanged(java.util.Set<KeyboardData.Key> keys, java.util.Set<KeyboardData.Row> rows) {
                updateMultiSelectUI(keys, rows);
            }
        });

        setupToolbar();
        setupBottomToolbar();
        setupAutoGlyphs();
        setupDragToSwap();
    }

    private void setupDragToSwap() {
        findViewById(R.id.btn_resize_mode).setOnClickListener(v -> {
            resizeMode = !resizeMode;
            ((Button)v).setText(resizeMode ? "Drag Mode" : "Resize");
            Toast.makeText(this, resizeMode ? "Drag keys to swap positions" : "Pinch rows to resize", Toast.LENGTH_SHORT).show();
        });
    }

    private boolean resizeMode = false;
    public boolean isResizeMode() { return resizeMode; }

    public void swapKeys(KeyboardData.Key a, KeyboardData.Key b) {
        pushToUndo();
        // Global Swap
        int rA = -1, cA = -1, rB = -1, cB = -1;
        for (int r=0; r<currentEditingLayout.rows.size(); r++) {
            int c = currentEditingLayout.rows.get(r).keys.indexOf(a);
            if (c != -1) { rA = r; cA = c; }
            c = currentEditingLayout.rows.get(r).keys.indexOf(b);
            if (c != -1) { rB = r; cB = c; }
        }

        if (rA != -1 && rB != -1) {
            List<KeyboardData.Row> newRows = new ArrayList<>();
            for (int r=0; r<currentEditingLayout.rows.size(); r++) {
                List<KeyboardData.Key> keys = new ArrayList<>(currentEditingLayout.rows.get(r).keys);
                if (r == rA && r == rB) {
                    keys.set(cA, b);
                    keys.set(cB, a);
                } else if (r == rA) {
                    keys.set(cA, b);
                } else if (r == rB) {
                    keys.set(cB, a);
                }
                newRows.add(new KeyboardData.Row(keys, currentEditingLayout.rows.get(r).height, currentEditingLayout.rows.get(r).shift));
            }
            currentEditingLayout = new KeyboardData(currentEditingLayout, newRows);
            liveKeyboardView.setKeyboard(currentEditingLayout);
            Toast.makeText(this, "Keys Swapped", Toast.LENGTH_SHORT).show();
        }
    }

    private void editBottomRow() {
        try {
            pushToUndo();
            String customBottomXml = Config.globalPrefs().getString("custom_bottom_row_xml", null);
            KeyboardData bottomRowLayout;
            if (customBottomXml != null) {
                bottomRowLayout = KeyboardData.load_string_exn(customBottomXml);
            } else {
                int resId = getResources().getIdentifier("bottom_row", "xml", getPackageName());
                bottomRowLayout = KeyboardData.load(getResources(), resId != 0 ? resId : R.xml.bottom_row);
            }
            currentEditingLayout = new KeyboardData(bottomRowLayout.rows, bottomRowLayout.keysWidth, bottomRowLayout.modmap, bottomRowLayout.script, bottomRowLayout.numpad_script, "Bottom Row", bottomRowLayout.bottom_row, bottomRowLayout.embedded_number_row, bottomRowLayout.locale_extra_keys, bottomRowLayout.wordlist, bottomRowLayout.surroundings);
            liveKeyboardView.setKeyboard(currentEditingLayout);
            Toast.makeText(this, "Editing Bottom Row Layout", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load bottom_row.xml", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupAutoGlyphs() {
        findViewById(R.id.btn_auto_glyphs).setOnClickListener(v -> {
            pushToUndo();
            List<KeyboardData.Row> newRows = new ArrayList<>();

            for (KeyboardData.Row row : currentEditingLayout.rows) {
                List<KeyboardData.Key> newKeys = new ArrayList<>();
                for (KeyboardData.Key key : row.keys) {
                    KeyValue[] ks = Arrays.copyOf(key.keys, 9);
                    KeyValue[] circ = Arrays.copyOf(key.circle, 9);
                    KeyValue[] anti = Arrays.copyOf(key.anticircle_v2, 9);
                    boolean changed = false;

                    for (int i = 0; i < 9; i++) {
                        if (ks[i] != null && isActionKv(ks[i])) {
                            String glyph = ActionRegistry.getActionGlyph(ks[i]);
                            if (glyph != null && !glyph.equals(ks[i].getString())) {
                                ks[i] = ks[i].withSymbol(glyph);
                                changed = true;
                            }
                        }
                        if (circ[i] != null && isActionKv(circ[i])) {
                            String glyph = ActionRegistry.getActionGlyph(circ[i]);
                            if (glyph != null && !glyph.equals(circ[i].getString())) {
                                circ[i] = circ[i].withSymbol(glyph);
                                changed = true;
                            }
                        }
                        if (anti[i] != null && isActionKv(anti[i])) {
                            String glyph = ActionRegistry.getActionGlyph(anti[i]);
                            if (glyph != null && !glyph.equals(anti[i].getString())) {
                                anti[i] = anti[i].withSymbol(glyph);
                                changed = true;
                            }
                        }
                    }

                    if (changed) {
                        newKeys.add(new KeyboardData.Key(ks, circ, anti, key.anticircle, 0, key.width, key.shift, key.borderRadius, key.indication, key.labelScales, key.colorDark, key.colorLight));
                    } else {
                        newKeys.add(key);
                    }
                }
                newRows.add(new KeyboardData.Row(newKeys, row.height, row.shift));
            }
            currentEditingLayout = new KeyboardData(currentEditingLayout, newRows);
            liveKeyboardView.setKeyboard(currentEditingLayout);
            Toast.makeText(this, "Auto-Glyphs applied to action keys", Toast.LENGTH_SHORT).show();
        });
    }

    private boolean isActionKv(KeyValue kv) {
        if (kv == null) return false;
        KeyValue.Kind k = kv.getKind();
        return k == KeyValue.Kind.Editing || k == KeyValue.Kind.Event || k == KeyValue.Kind.Keyevent || k == KeyValue.Kind.Modifier;
    }

    private int[] findKeyIndices(KeyboardData.Key target) {
        for (int r = 0; r < currentEditingLayout.rows.size(); r++) {
            List<KeyboardData.Key> keys = currentEditingLayout.rows.get(r).keys;
            for (int c = 0; c < keys.size(); c++) {
                if (keys.get(c) == target) return new int[]{r, c};
            }
        }
        return null;
    }

    private void showKeyEditor(int rowIdx, int colIdx) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_key_editor, null);

        KeyboardExecutors.HIGH_PRIORITY_EXECUTOR.execute(() -> {
            List<ActionRegistry.ActionDefinition> actions = ActionRegistry.getAllActions(this);
            List<String> allChars = new ArrayList<>();
            for (UnicodeDatasetGenerator.UnicodeRange range : UnicodeDatasetGenerator.getRanges()) {
                allChars.addAll(UnicodeDatasetGenerator.getCharactersForRange(range));
            }

            runOnUiThread(() -> {
                setupEditorWithData(dialog, view, rowIdx, colIdx, actions, allChars);
            });
        });
    }

    private void setupEditorWithData(BottomSheetDialog dialog, View view, int rowIdx, int colIdx, List<ActionRegistry.ActionDefinition> actions, List<String> allChars) {
        KeyboardData.Key key = currentEditingLayout.rows.get(rowIdx).keys.get(colIdx);

        TabLayout tabs = view.findViewById(R.id.editor_tabs);

        View tabChars = view.findViewById(R.id.tab_characters);
        View tabActions = view.findViewById(R.id.tab_actions);
        View tabVisuals = view.findViewById(R.id.tab_visuals);
        View tabGlyphs = view.findViewById(R.id.tab_glyphs);
        View tabFnMap = view.findViewById(R.id.tab_fn_map);

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                tabChars.setVisibility(tab.getPosition() == 0 ? View.VISIBLE : View.GONE);
                tabActions.setVisibility(tab.getPosition() == 1 ? View.VISIBLE : View.GONE);
                tabVisuals.setVisibility(tab.getPosition() == 2 ? View.VISIBLE : View.GONE);
                tabGlyphs.setVisibility(tab.getPosition() == 3 ? View.VISIBLE : View.GONE);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        Spinner actionSpinner = view.findViewById(R.id.action_spinner);
        final android.widget.RadioGroup posGroup = view.findViewById(R.id.position_selector);
        final TabLayout gestureTabs = view.findViewById(R.id.gesture_type_tabs);
        final android.widget.CheckBox checkAppend = view.findViewById(R.id.check_append_mode);
        final Button btnClearAssignment = view.findViewById(R.id.btn_clear_assignment);

        gestureTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                tabFnMap.setVisibility(tab.getPosition() == 3 ? View.VISIBLE : View.GONE);
                // Also show/hide standard assignment UI
                view.findViewById(R.id.position_selector_scroll).setVisibility(tab.getPosition() == 3 ? View.GONE : View.VISIBLE);
                view.findViewById(R.id.current_assignment_text).setVisibility(tab.getPosition() == 3 ? View.GONE : View.VISIBLE);
                view.findViewById(R.id.btn_clear_assignment).setVisibility(tab.getPosition() == 3 ? View.GONE : View.VISIBLE);
                tabs.setVisibility(tab.getPosition() == 3 ? View.GONE : View.VISIBLE);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        final android.graphics.Typeface keyboardFont = Theme.getKeyFont(this);
        ArrayAdapter<ActionRegistry.ActionDefinition> actionAdapter = new ArrayAdapter<ActionRegistry.ActionDefinition>(this, android.R.layout.simple_spinner_item, actions) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                ActionRegistry.ActionDefinition ad = getItem(position);
                tv.setTypeface(keyboardFont);
                tv.setText(ActionRegistry.getIconForAction(ad.label) + " " + ad.label);
                return tv;
            }
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                ActionRegistry.ActionDefinition ad = getItem(position);
                tv.setTypeface(keyboardFont);
                tv.setText(ActionRegistry.getIconForAction(ad.label) + " " + ad.label);
                return tv;
            }
        };
        actionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        actionSpinner.setAdapter(actionAdapter);

        Spinner fnSpinner = view.findViewById(R.id.fn_map_spinner);
        fnSpinner.setAdapter(actionAdapter);

        TextView previewText = view.findViewById(R.id.preview_key_text);
        Config config = Config.globalConfig();
        if (config.use_system_font) {
            previewText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        } else {
            previewText.setTypeface(Theme.getKeyFont(this));
        }
        previewText.setText(key.keys[0] != null ? key.keys[0].getString() : "");

        // Pre-select current action if exists
        int prePos = getSelectedPosition(posGroup);
        int preGestureType = gestureTabs.getSelectedTabPosition();
        KeyValue currentKv = null;
        if (preGestureType == 0) currentKv = key.keys[prePos];
        else if (preGestureType == 1) currentKv = key.circle[prePos];
        else if (preGestureType == 2) currentKv = key.anticircle_v2[prePos];

        if (currentKv != null) {
            String canonical = KeyValue.getCanonicalName(currentKv);
            for (int i=0; i<actions.size(); i++) {
                if (actions.get(i).id.equals(canonical)) {
                    actionSpinner.setSelection(i);
                    break;
                }
            }
        }
        TextView currentAssignmentText = view.findViewById(R.id.current_assignment_text);
        final SeekBar hue = view.findViewById(R.id.hue_slider);
        final SeekBar sat = view.findViewById(R.id.sat_slider);
        final SeekBar val = view.findViewById(R.id.val_slider);
        final SeekBar alpha = view.findViewById(R.id.alpha_slider);
        final View colorPreview = view.findViewById(R.id.color_preview);
        final TabLayout colorTabs = view.findViewById(R.id.color_mode_tabs);
        final Button btnClearColor = view.findViewById(R.id.btn_clear_color);
        final SeekBar sizeSlider = view.findViewById(R.id.glyph_size_slider);

        final android.widget.RadioGroup.OnCheckedChangeListener posListener = (group, checkedId) -> {
            int pos = getSelectedPosition(posGroup);
            int gestureType = gestureTabs.getSelectedTabPosition();
            KeyValue kv = null;
            if (gestureType == 0) kv = key.keys[pos];
            else if (gestureType == 1) kv = key.circle[pos];
            else if (gestureType == 2) kv = key.anticircle_v2[pos];
            currentAssignmentText.setText("Current: " + (kv != null ? KeyValue.getCanonicalName(kv) : "(None)"));
            updateColorSliders(key, pos, colorTabs.getSelectedTabPosition() == 1, hue, sat, val, alpha);

            sizeSlider.setProgress((int)((key.labelScales[pos] > 0 ? key.labelScales[pos] : 1.0f) * 100));

            if (currentEditingLayout.modmap != null) {
                 KeyValue fnKey = (kv != null ? kv : key.keys[0]);
                 if (fnKey != null) {
                     KeyValue target = currentEditingLayout.modmap.get(Modmap.M.Fn, fnKey);
                     if (target != null) {
                         String targetId = KeyValue.getCanonicalName(target);
                         for (int i=0; i<actions.size(); i++) {
                             if (actions.get(i).id.equals(targetId)) {
                                 fnSpinner.setSelection(i);
                                 break;
                             }
                         }
                     } else {
                         fnSpinner.setSelection(0);
                     }
                 }
            }
        };
        posGroup.setOnCheckedChangeListener(posListener);
        gestureTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { posListener.onCheckedChanged(posGroup, posGroup.getCheckedRadioButtonId()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
        posListener.onCheckedChanged(posGroup, posGroup.getCheckedRadioButtonId());

        view.findViewById(R.id.btn_clear_fn).setOnClickListener(v -> {
            pushToUndo();
            int pos = getSelectedPosition(posGroup);
            KeyValue targetKey = key.keys[pos] != null ? key.keys[pos] : key.keys[0];
            if (targetKey != null && currentEditingLayout.modmap != null) {
                 currentEditingLayout.modmap.add(Modmap.M.Fn, targetKey, null);
                 Toast.makeText(this, "Fn mapping cleared", Toast.LENGTH_SHORT).show();
            }
        });

        btnClearAssignment.setOnClickListener(v -> {
            pushToUndo();
            int pos = getSelectedPosition(posGroup);
            int gestureType = gestureTabs.getSelectedTabPosition();
            clearAssignment(rowIdx, colIdx, pos, gestureType);
            liveKeyboardView.setKeyboard(currentEditingLayout);
            posListener.onCheckedChanged(posGroup, posGroup.getCheckedRadioButtonId());
        });

        SeekBar.OnSeekBarChangeListener colorListener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float[] hsv = {hue.getProgress(), sat.getProgress()/100f, val.getProgress()/100f};
                int color = android.graphics.Color.HSVToColor(alpha.getProgress(), hsv);
                colorPreview.setBackgroundColor(color);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        hue.setOnSeekBarChangeListener(colorListener);
        sat.setOnSeekBarChangeListener(colorListener);
        val.setOnSeekBarChangeListener(colorListener);
        alpha.setOnSeekBarChangeListener(colorListener);

        colorTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                // Update sliders to match current color for this theme mode
                int pos__ = getSelectedPosition(posGroup);
                updateColorSliders(key, pos__, tab.getPosition() == 1, hue, sat, val, alpha);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        btnClearColor.setOnClickListener(v -> {
            pushToUndo();
            int pos = getSelectedPosition(posGroup);
            updateKeyColor(rowIdx, colIdx, pos, null, colorTabs.getSelectedTabPosition() == 1);
            updateKeyLabelScale(rowIdx, colIdx, pos, 0);
            liveKeyboardView.setKeyboard(currentEditingLayout);
        });

        sizeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float scale = progress / 100.0f;
                    updateKeyLabelScale(rowIdx, colIdx, getSelectedPosition(posGroup), scale);
                    liveKeyboardView.setKeyboard(currentEditingLayout);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) { pushToUndo(); }
        });

        RecyclerView charRecycler = view.findViewById(R.id.char_recycler_view);
        charRecycler.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 5));

        RecyclerView glyphRecycler = view.findViewById(R.id.glyph_recycler_view);
        glyphRecycler.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 5));

        final List<String> allGlyphs = new ArrayList<>();
        final Map<String, String> glyphToIndexMap = new HashMap<>();
        int absoluteIdx = 1;
        for (int i=0; i<Glyph.getNumGroups(); i++) {
            Glyph.Group g = Glyph.getGroup(i);
            for (Glyph gl : g.glyphs) {
                String gStr = gl.kv().getString();
                allGlyphs.add(gStr);
                glyphToIndexMap.put(gStr, String.valueOf(absoluteIdx++));
            }
        }

        final List<String> currentGlyphsList = new ArrayList<>(allGlyphs);
        final CharAdapter glyphAdapter = new CharAdapter(currentGlyphsList, glyph -> {
            pushToUndo();
            int pos = getSelectedPosition(posGroup);
            int gestureType = gestureTabs.getSelectedTabPosition();
            if (gestureType == 3) {
                 Toast.makeText(this, "Glyphs cannot be assigned to Fn Map", Toast.LENGTH_SHORT).show();
                 return;
            }
            KeyValue current = (gestureType == 0) ? key.keys[pos] : (gestureType == 1 ? key.circle[pos] : key.anticircle_v2[pos]);
            String targetAction = current != null ? KeyValue.getRawActionName(current) : "";
            if (targetAction.isEmpty()) targetAction = key.keys[0] != null ? key.keys[0].getString() : "";
            updateKeyPositionEnhanced(rowIdx, colIdx, pos, gestureType, glyph + ":" + targetAction, null, false);
            liveKeyboardView.setKeyboard(currentEditingLayout);
            posListener.onCheckedChanged(posGroup, posGroup.getCheckedRadioButtonId());
            Toast.makeText(this, "Glyph assigned: " + glyph, Toast.LENGTH_SHORT).show();
        }, glyphToIndexMap);
        glyphRecycler.setAdapter(glyphAdapter);

        final List<String> filteredChars = new ArrayList<>(allChars);
        CharAdapter charAdapter = new CharAdapter(filteredChars, c -> {
            pushToUndo();
            int pos = getSelectedPosition(posGroup);
            int gestureType = gestureTabs.getSelectedTabPosition();
            updateKeyPositionEnhanced(rowIdx, colIdx, pos, gestureType, c, null, checkAppend.isChecked());
            liveKeyboardView.setKeyboard(currentEditingLayout);
            posListener.onCheckedChanged(posGroup, posGroup.getCheckedRadioButtonId());
            Toast.makeText(this, "Assigned " + c, Toast.LENGTH_SHORT).show();
        });
        charRecycler.setAdapter(charAdapter);

        TabLayout glyphGroupTabs = view.findViewById(R.id.glyph_group_tabs);
        glyphGroupTabs.addTab(glyphGroupTabs.newTab().setText("All"));
        for (int i=0; i<Glyph.getNumGroups(); i++) {
            glyphGroupTabs.addTab(glyphGroupTabs.newTab().setText(Glyph.getGroup(i).name));
        }

        Consumer<Integer> updateGlyphFilter = tabPos -> {
            currentGlyphsList.clear();
            if (tabPos == 0) {
                currentGlyphsList.addAll(allGlyphs);
            } else {
                Glyph.Group g = Glyph.getGroup(tabPos - 1);
                for (Glyph gl : g.glyphs) currentGlyphsList.add(gl.kv().getString());
            }
            glyphAdapter.notifyDataSetChanged();
        };

        glyphGroupTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { updateGlyphFilter.accept(tab.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        EditText glyphSearch = view.findViewById(R.id.glyph_search);
        glyphSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().toLowerCase();
                currentGlyphsList.clear();
                for (String g : allGlyphs) {
                    if (glyphToIndexMap.get(g).equals(query) || glyphToIndexMap.get(g).startsWith(query)) {
                         currentGlyphsList.add(g);
                    }
                }
                glyphAdapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        EditText charSearch = view.findViewById(R.id.char_search);
        charSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().toLowerCase();
                filteredChars.clear();
                for (String c : allChars) {
                    if (c.toLowerCase().contains(query)) filteredChars.add(c);
                }
                charAdapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        view.findViewById(R.id.btn_apply_key).setOnClickListener(v -> {
            pushToUndo();
            int applyPos = getSelectedPosition(posGroup);
            int applyGestureType = gestureTabs.getSelectedTabPosition();
            ActionRegistry.ActionDefinition selectedAction = (ActionRegistry.ActionDefinition) actionSpinner.getSelectedItem();
            ActionRegistry.ActionDefinition fnAction = (ActionRegistry.ActionDefinition) fnSpinner.getSelectedItem();

            float[] hsv = {hue.getProgress(), sat.getProgress()/100f, val.getProgress()/100f};
            int color = android.graphics.Color.HSVToColor(alpha.getProgress(), hsv);
            updateKeyColor(rowIdx, colIdx, applyPos, String.format("%d,%d,%d", android.graphics.Color.red(color), android.graphics.Color.green(color), android.graphics.Color.blue(color)), colorTabs.getSelectedTabPosition() == 1);

            float scale = sizeSlider.getProgress() / 100.0f;
            updateKeyLabelScale(rowIdx, colIdx, applyPos, scale);

            if (selectedAction != null) {
                if (applyGestureType == 3) {
                     KeyValue kv_ = (key.keys[applyPos] != null) ? key.keys[applyPos] : key.keys[0];
                     if (kv_ != null) {
                         ensureModmap();
                         currentEditingLayout.modmap.add(Modmap.M.Fn, kv_, KeyValue.getKeyByName(selectedAction.id));
                     }
                } else {
                     updateKeyPositionEnhanced(rowIdx, colIdx, applyPos, applyGestureType, null, selectedAction.id, checkAppend.isChecked());
                }
            }

            if (fnAction != null && applyGestureType != 3) {
                 KeyValue kv_ = (key.keys[applyPos] != null) ? key.keys[applyPos] : key.keys[0];
                 if (kv_ != null) {
                      ensureModmap();
                      currentEditingLayout.modmap.add(Modmap.M.Fn, kv_, KeyValue.getKeyByName(fnAction.id));
                 }
            }

            liveKeyboardView.setKeyboard(currentEditingLayout);
            dialog.dismiss();
            Toast.makeText(this, "Key Applied", Toast.LENGTH_SHORT).show();
        });

        dialog.setContentView(view);
        dialog.show();
    }

    private class CharAdapter extends RecyclerView.Adapter<CharAdapter.ViewHolder> {
        private List<String> chars;
        private java.util.function.Consumer<String> callback;
        private Map<String, String> sublabels;

        CharAdapter(List<String> chars, java.util.function.Consumer<String> callback) {
            this(chars, callback, null);
        }

        CharAdapter(List<String> chars, java.util.function.Consumer<String> callback, Map<String, String> sublabels) {
            this.chars = chars;
            this.callback = callback;
            this.sublabels = sublabels;
        }

        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.char_item, parent, false);
            return new ViewHolder(v);
        }

        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String c = chars.get(position);
            holder.tv.setText(c);
            holder.tv.setTypeface(Theme.getKeyFont(LiveLayoutCustomizationActivity.this));
            if (sublabels != null && sublabels.containsKey(c)) {
                holder.sub.setText(sublabels.get(c));
                holder.sub.setVisibility(View.VISIBLE);
            } else {
                holder.sub.setVisibility(View.GONE);
            }
            holder.itemView.setOnClickListener(v -> callback.accept(c));
        }

        @Override public int getItemCount() { return chars.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tv, sub;
            ViewHolder(View v) {
                super(v);
                tv = v.findViewById(R.id.char_text);
                sub = v.findViewById(R.id.char_subtext);
            }
        }
    }

    private void updateKeyColor(int rowIdx, int colIdx, int pos, String colorStr, boolean dark) {
        List<KeyboardData.Row> newRows = new ArrayList<>();
        for (int r = 0; r < currentEditingLayout.rows.size(); r++) {
            KeyboardData.Row row = currentEditingLayout.rows.get(r);
            if (r == rowIdx) {
                List<KeyboardData.Key> newKeys = new ArrayList<>(row.keys);
                KeyboardData.Key key = newKeys.get(colIdx);
                String[] newColors = Arrays.copyOf(dark ? key.colorDark : key.colorLight, 9);
                newColors[pos] = colorStr;
                if (dark) newKeys.set(colIdx, new KeyboardData.Key(key.keys, key.circle, key.anticircle_v2, key.anticircle, 0, key.width, key.shift, key.borderRadius, key.indication, key.labelScales, newColors, key.colorLight));
                else newKeys.set(colIdx, new KeyboardData.Key(key.keys, key.circle, key.anticircle_v2, key.anticircle, 0, key.width, key.shift, key.borderRadius, key.indication, key.labelScales, key.colorDark, newColors));
                newRows.add(new KeyboardData.Row(newKeys, row.height, row.shift));
            } else {
                newRows.add(row);
            }
        }
        currentEditingLayout = new KeyboardData(currentEditingLayout, newRows);
    }

    private void updateColorSliders(KeyboardData.Key key, int pos, boolean dark, SeekBar hue, SeekBar sat, SeekBar val, SeekBar alpha) {
        String[] colors = dark ? key.colorDark : key.colorLight;
        String colorStr = colors[pos];
        if (colorStr == null) {
            hue.setProgress(0); sat.setProgress(0); val.setProgress(100); alpha.setProgress(255);
            return;
        }
        try {
            String[] parts = colorStr.split(",");
            int r = Integer.parseInt(parts[0]);
            int g = Integer.parseInt(parts[1]);
            int b = Integer.parseInt(parts[2]);
            float[] hsv = new float[3];
            android.graphics.Color.RGBToHSV(r, g, b, hsv);
            hue.setProgress((int)hsv[0]);
            sat.setProgress((int)(hsv[1] * 100));
            val.setProgress((int)(hsv[2] * 100));
            alpha.setProgress(255);
        } catch (Exception e) {
            hue.setProgress(0); sat.setProgress(0); val.setProgress(100); alpha.setProgress(255);
        }
    }

    private void clearAssignment(int rowIdx, int colIdx, int pos, int gestureType) {
        List<KeyboardData.Row> newRows = new ArrayList<>();
        for (int r = 0; r < currentEditingLayout.rows.size(); r++) {
            KeyboardData.Row row = currentEditingLayout.rows.get(r);
            if (r == rowIdx) {
                List<KeyboardData.Key> newKeys = new ArrayList<>(row.keys);
                KeyboardData.Key key = newKeys.get(colIdx);
                KeyValue[] ks = Arrays.copyOf(key.keys, 9);
                KeyValue[] circ = Arrays.copyOf(key.circle, 9);
                KeyValue[] anti = Arrays.copyOf(key.anticircle_v2, 9);
                if (gestureType == 0) ks[pos] = null;
                else if (gestureType == 1) circ[pos] = null;
                else if (gestureType == 2) anti[pos] = null;
                newKeys.set(colIdx, new KeyboardData.Key(ks, circ, anti, key.anticircle, 0, key.width, key.shift, key.borderRadius, key.indication, key.labelScales, key.colorDark, key.colorLight));
                newRows.add(new KeyboardData.Row(newKeys, row.height, row.shift));
            } else {
                newRows.add(row);
            }
        }
        currentEditingLayout = new KeyboardData(currentEditingLayout, newRows);
    }

    private void quickEditKey(int rowIdx, int colIdx) {
        pushToUndo();
        KeyboardData.Key key = currentEditingLayout.rows.get(rowIdx).keys.get(colIdx);
        updateKeyPositionEnhanced(rowIdx, colIdx, 0, 0, key.shift == 0 ? "SHIFT" : null, null, false);
        liveKeyboardView.setKeyboard(currentEditingLayout);
    }

    private void startKeyDrag(KeyboardData.Key key) {
        Toast.makeText(this, "Drag key to swap position (Hold & Move)", Toast.LENGTH_SHORT).show();
    }

    private void resizeRow(int rowIdx, float scale) {
        List<KeyboardData.Row> newRows = new ArrayList<>();
        for (int r = 0; r < currentEditingLayout.rows.size(); r++) {
            KeyboardData.Row row = currentEditingLayout.rows.get(r);
            if (r == rowIdx) {
                newRows.add(new KeyboardData.Row(row.keys, row.height * scale, row.shift));
            } else {
                newRows.add(row);
            }
        }
        currentEditingLayout = new KeyboardData(currentEditingLayout, newRows);
        liveKeyboardView.setKeyboard(currentEditingLayout);
    }

    private void moveRow(int rowIdx, float dx, float dy) {
        List<KeyboardData.Row> newRows = new ArrayList<>();
        float shiftDelta = dx / (liveKeyboardView.getWidth() / currentEditingLayout.keysWidth);
        for (int r = 0; r < currentEditingLayout.rows.size(); r++) {
            KeyboardData.Row row = currentEditingLayout.rows.get(r);
            if (r == rowIdx) {
                newRows.add(new KeyboardData.Row(row.keys, row.height, Math.max(0, row.shift + shiftDelta)));
            } else {
                newRows.add(row);
            }
        }
        currentEditingLayout = new KeyboardData(currentEditingLayout, newRows);
        liveKeyboardView.setKeyboard(currentEditingLayout);
    }

    private void setupToolbar() {
        findViewById(R.id.btn_save_layout).setOnClickListener(v -> promptSaveLayout(false));
        findViewById(R.id.btn_export_layout).setOnClickListener(v -> promptSaveLayout(true));
        findViewById(R.id.layout_name).setOnClickListener(v -> importLayout());
        findViewById(R.id.btn_undo).setOnClickListener(v -> undo());
        findViewById(R.id.btn_redo).setOnClickListener(v -> redo());
        findViewById(R.id.btn_theme_creator).setOnClickListener(v -> showThemeCreator());
    }

    private void promptSaveLayout(boolean export) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(export ? "Export Layout" : "Save Layout");
        final EditText input = new EditText(this);
        input.setText(currentEditingLayout.name);
        builder.setView(input);
        builder.setPositiveButton("OK", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                currentEditingLayout = new KeyboardData(currentEditingLayout.rows, currentEditingLayout.keysWidth, currentEditingLayout.modmap, currentEditingLayout.script, currentEditingLayout.numpad_script, name, currentEditingLayout.bottom_row, currentEditingLayout.embedded_number_row, currentEditingLayout.locale_extra_keys, currentEditingLayout.wordlist, currentEditingLayout.surroundings);
                if (export) exportLayout();
                else saveLayoutInternal();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private boolean selectMode = false;

    private boolean surroundingMode = false;
    private KeyboardData.Key surroundingTarget = null;

    private void setupBottomToolbar() {
        findViewById(R.id.btn_add_row).setOnClickListener(v -> addRow());
        findViewById(R.id.btn_remove_row).setOnClickListener(v -> removeRow());
        findViewById(R.id.btn_add_key).setOnClickListener(v -> {
            java.util.Set<KeyboardData.Key> keys = liveKeyboardView.getSelectedKeys();
            if (keys.size() == 1) {
                showAddKeyDirectionMenu(keys.iterator().next());
            } else {
                addKeyToSelectedRow();
            }
        });
        findViewById(R.id.btn_remove_key).setOnClickListener(v -> removeKeyFromSelectedRow());

        findViewById(R.id.btn_select_mode).setOnClickListener(v -> {
            selectMode = !selectMode;
            ((Button)v).setText(selectMode ? "Exit Select" : "Select");
            if (!selectMode) liveKeyboardView.clearSelection();
        });

        View bulkBtn = findViewById(R.id.btn_bulk_edit);
        if (bulkBtn != null) bulkBtn.setOnClickListener(v -> showBulkEditor());

        Button btnSurr = findViewById(R.id.btn_edit_surroundings);
        if (btnSurr != null) {
            btnSurr.setOnClickListener(v -> {
                java.util.Set<KeyboardData.Key> keys = liveKeyboardView.getSelectedKeys();
                if (!surroundingMode) {
                    if (keys.size() != 1) {
                        Toast.makeText(this, "Select exactly one key to edit surroundings", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    surroundingTarget = keys.iterator().next();
                    if (getPrimaryChar(surroundingTarget) == 0) {
                        Toast.makeText(this, "Selected key must have a primary character", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    surroundingMode = true;
                    btnSurr.setText("Done Surroundings");
                    Toast.makeText(this, "Tap keys to add/remove neighbors, then click Done", Toast.LENGTH_LONG).show();
                } else {
                    surroundingMode = false;
                    surroundingTarget = null;
                    btnSurr.setText("Surroundings");
                    liveKeyboardView.clearSelection();
                }
            });
        }
    }

    private void addKeyToSelectedRow() {
        java.util.Set<KeyboardData.Row> rows = liveKeyboardView.getSelectedRows();
        if (rows.isEmpty()) {
            Toast.makeText(this, "Select a row first (Ctrl+Click)", Toast.LENGTH_SHORT).show();
            return;
        }
        pushToUndo();
        List<KeyboardData.Row> newRows = new ArrayList<>();
        for (KeyboardData.Row row : currentEditingLayout.rows) {
            if (rows.contains(row)) {
                List<KeyboardData.Key> newKeys = new ArrayList<>(row.keys);
                newKeys.add(new KeyboardData.Key(new KeyValue[9], new KeyValue[9], new KeyValue[9], null, 0, 1.0f, 0, -1f, null, new float[9], new String[9], new String[9]));
                KeyboardData.Row newRow = new KeyboardData.Row(newKeys, row.height, row.shift);
                newRows.add(newRow.updateWidth(currentEditingLayout.keysWidth));
            } else {
                newRows.add(row);
            }
        }
        currentEditingLayout = new KeyboardData(currentEditingLayout, newRows);
        liveKeyboardView.setKeyboard(currentEditingLayout);
    }

    private void removeKeyFromSelectedRow() {
        java.util.Set<KeyboardData.Key> keys = liveKeyboardView.getSelectedKeys();
        if (keys.isEmpty()) {
            Toast.makeText(this, "Select keys to remove (Ctrl+Click)", Toast.LENGTH_SHORT).show();
            return;
        }
        pushToUndo();
        List<KeyboardData.Row> newRows = new ArrayList<>();
        for (KeyboardData.Row row : currentEditingLayout.rows) {
            List<KeyboardData.Key> newKeys = new ArrayList<>();
            for (KeyboardData.Key key : row.keys) {
                if (!keys.contains(key)) newKeys.add(key);
            }
            KeyboardData.Row newRow = new KeyboardData.Row(newKeys, row.height, row.shift);
            newRows.add(newRow.updateWidth(currentEditingLayout.keysWidth));
        }
        currentEditingLayout = new KeyboardData(currentEditingLayout, newRows);
        liveKeyboardView.setKeyboard(currentEditingLayout);
        liveKeyboardView.clearSelection();
    }

    private void showThemeCreator() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_theme_creator, null);

        SeekBar kbHue = view.findViewById(R.id.theme_kb_hue);
        SeekBar kbSat = view.findViewById(R.id.theme_kb_sat);
        SeekBar kbVal = view.findViewById(R.id.theme_kb_val);
        SeekBar keyHue = view.findViewById(R.id.theme_key_hue);
        SeekBar keySat = view.findViewById(R.id.theme_key_sat);
        SeekBar keyVal = view.findViewById(R.id.theme_key_val);
        SeekBar labelHue = view.findViewById(R.id.theme_label_hue);
        SeekBar labelSat = view.findViewById(R.id.theme_label_sat);
        SeekBar labelVal = view.findViewById(R.id.theme_label_val);
        SeekBar activeHue = view.findViewById(R.id.theme_active_hue);
        SeekBar radius = view.findViewById(R.id.theme_radius);
        EditText nameInput = view.findViewById(R.id.theme_name_input);

        view.findViewById(R.id.btn_save_theme).setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter a theme name", Toast.LENGTH_SHORT).show();
                return;
            }

            int kbColor = android.graphics.Color.HSVToColor(new float[]{kbHue.getProgress(), kbSat.getProgress()/100f, kbVal.getProgress()/100f});
            int keyColor = android.graphics.Color.HSVToColor(new float[]{keyHue.getProgress(), keySat.getProgress()/100f, keyVal.getProgress()/100f});
            int labelColor = android.graphics.Color.HSVToColor(new float[]{labelHue.getProgress(), labelSat.getProgress()/100f, labelVal.getProgress()/100f});
            int activeColor = android.graphics.Color.HSVToColor(new float[]{activeHue.getProgress(), 0.8f, 0.8f});

            saveCustomTheme(name, kbColor, keyColor, labelColor, activeColor, radius.getProgress());
            dialog.dismiss();
        });

        dialog.setContentView(view);
        dialog.show();
    }

    private void saveCustomTheme(String name, int kbColor, int keyColor, int labelColor, int activeColor, int radius) {
        String themeId = "custom_" + name.toLowerCase().replaceAll("\\s+", "_");
        SharedPreferences prefs = Config.globalPrefs();
        SharedPreferences.Editor editor = prefs.edit();

        editor.putInt("theme_color_kb_" + themeId, kbColor);
        editor.putInt("theme_color_key_" + themeId, keyColor);
        editor.putInt("theme_color_label_" + themeId, labelColor);
        editor.putInt("theme_color_active_" + themeId, activeColor);
        editor.putInt("theme_radius_" + themeId, radius);

        // Add to theme list
        String themeList = prefs.getString("custom_theme_list", "");
        if (!themeList.contains(themeId)) {
            themeList = themeList.isEmpty() ? themeId : themeList + "," + themeId;
            editor.putString("custom_theme_list", themeList);
        }

        editor.putString("theme", themeId);
        editor.apply();

        Config.globalConfig().refresh(getResources(), Config.globalConfig().foldable_unfolded);
        liveKeyboardView.invalidate();
        Toast.makeText(this, "Theme '" + name + "' Saved & Applied", Toast.LENGTH_SHORT).show();
    }

    private void updateMultiSelectUI(java.util.Set<KeyboardData.Key> keys, java.util.Set<KeyboardData.Row> rows) {
        findViewById(R.id.btn_bulk_edit).setVisibility((!keys.isEmpty() || !rows.isEmpty()) ? View.VISIBLE : View.GONE);
    }

    private void showBulkEditor() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_bulk_editor, null);

        SeekBar widthSlider = view.findViewById(R.id.bulk_width_slider);
        SeekBar heightSlider = view.findViewById(R.id.bulk_height_slider);
        SeekBar hueLightSlider = view.findViewById(R.id.bulk_hue_light_slider);
        SeekBar hueDarkSlider = view.findViewById(R.id.bulk_hue_dark_slider);
        SeekBar radiusSlider = view.findViewById(R.id.bulk_radius_slider);

        view.findViewById(R.id.btn_apply_bulk).setOnClickListener(v -> {
            pushToUndo();
            float wScale = widthSlider.getProgress() / 100f;
            float hScale = heightSlider.getProgress() / 100f;

            String colorLight = null;
            if (hueLightSlider.getProgress() <= 360) {
                int c = android.graphics.Color.HSVToColor(new float[]{hueLightSlider.getProgress(), 0.8f, 0.8f});
                colorLight = String.format("%d,%d,%d", android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c));
            }

            String colorDark = null;
            if (hueDarkSlider.getProgress() <= 360) {
                int c = android.graphics.Color.HSVToColor(new float[]{hueDarkSlider.getProgress(), 0.8f, 0.8f});
                colorDark = String.format("%d,%d,%d", android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c));
            }

            float radius = radiusSlider.getProgress() / 10f;

            bulkEdit(liveKeyboardView.getSelectedKeys(), liveKeyboardView.getSelectedRows(), wScale, hScale, colorLight, colorDark, radius);
            liveKeyboardView.clearSelection();
            dialog.dismiss();
        });

        dialog.setContentView(view);
        dialog.show();
    }

    private void bulkEdit(java.util.Set<KeyboardData.Key> keys, java.util.Set<KeyboardData.Row> rows, float wScale, float hScale, String colorLight, String colorDark, float radius) {
        List<KeyboardData.Row> newRows = new ArrayList<>();
        for (KeyboardData.Row row : currentEditingLayout.rows) {
            float newHeight = row.height;
            if (rows.contains(row)) newHeight *= hScale;

            List<KeyboardData.Key> newKeys = new ArrayList<>();
            for (KeyboardData.Key key : row.keys) {
                float newWidth = key.width;
                float newRadius = key.borderRadius;
                String[] newColorLight = key.colorLight;
                String[] newColorDark = key.colorDark;
                if (keys.contains(key)) {
                    newWidth *= wScale;
                    newRadius = radius;
                    if (colorLight != null) {
                        newColorLight = Arrays.copyOf(key.colorLight, 9);
                        newColorLight[0] = colorLight;
                    }
                    if (colorDark != null) {
                        newColorDark = Arrays.copyOf(key.colorDark, 9);
                        newColorDark[0] = colorDark;
                    }
                }
                newKeys.add(new KeyboardData.Key(key.keys, key.circle, key.anticircle_v2, key.anticircle, 0, newWidth, key.shift, newRadius, key.indication, key.labelScales, newColorDark, newColorLight));
            }
            KeyboardData.Row newRow = new KeyboardData.Row(newKeys, newHeight, row.shift);
            newRows.add(newRow.updateWidth(currentEditingLayout.keysWidth));
        }
        currentEditingLayout = new KeyboardData(currentEditingLayout, newRows);
        liveKeyboardView.setKeyboard(currentEditingLayout);
    }

    private void ensureModmap() {
        if (currentEditingLayout.modmap == null) {
            currentEditingLayout = new KeyboardData(currentEditingLayout.rows, currentEditingLayout.keysWidth, new Modmap(), currentEditingLayout.script, currentEditingLayout.numpad_script, currentEditingLayout.name, currentEditingLayout.bottom_row, currentEditingLayout.embedded_number_row, currentEditingLayout.locale_extra_keys, currentEditingLayout.wordlist, currentEditingLayout.surroundings);
        }
    }

    private void pushToUndo() {
        // Deep copy
        KeyboardData clone = new KeyboardData(currentEditingLayout.rows, currentEditingLayout.keysWidth,
            currentEditingLayout.modmap != null ? currentEditingLayout.modmap.copy() : null,
            currentEditingLayout.script, currentEditingLayout.numpad_script, currentEditingLayout.name,
            currentEditingLayout.bottom_row, currentEditingLayout.embedded_number_row,
            currentEditingLayout.locale_extra_keys, currentEditingLayout.wordlist, currentEditingLayout.surroundings);

        undoStack.push(clone);
        redoStack.clear();

        // Autosave
        Config.globalPrefs().edit().putString("layout_autosave", KeyboardData.serialize_to_unified_xml(currentEditingLayout)).apply();
    }

    private void undo() {
        if (!undoStack.isEmpty()) {
            redoStack.push(currentEditingLayout);
            currentEditingLayout = undoStack.pop();
            liveKeyboardView.setKeyboard(currentEditingLayout);
        }
    }

    private void redo() {
        if (!redoStack.isEmpty()) {
            undoStack.push(currentEditingLayout);
            currentEditingLayout = redoStack.pop();
            liveKeyboardView.setKeyboard(currentEditingLayout);
        }
    }

    private void importLayout() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/xml");
        startActivityForResult(intent, 100);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            try (java.io.InputStream is = getContentResolver().openInputStream(uri)) {
                String xml = Utils.read_all_utf8(is);
                KeyboardData imported = KeyboardData.load_string_exn(xml);
                if (imported != null) {
                    pushToUndo();
                    currentEditingLayout = imported;
                    liveKeyboardView.setKeyboard(currentEditingLayout);
                    Toast.makeText(this, "Layout Imported", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Import Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveLayoutInternal() {
        try {
            String xml = KeyboardData.serialize_to_unified_xml(currentEditingLayout);
            Config config = Config.globalConfig();
            SharedPreferences prefs = Config.globalPrefs();

            // Clear input pref so we don't keep loading it
            prefs.edit().remove("layout_customizer_input").apply();

            // Get existing layouts
            List<LayoutsPreference.Layout> items = LayoutsPreference.load_layouts_from_preferences(prefs);
            // Replace or Add
            boolean found = false;
            for (int i=0; i<items.size(); i++) {
                LayoutsPreference.Layout l = items.get(i);
                if (l instanceof LayoutsPreference.CustomLayout) {
                    if (((LayoutsPreference.CustomLayout)l).parsed != null && ((LayoutsPreference.CustomLayout)l).parsed.name.equals(currentEditingLayout.name)) {
                        items.set(i, new LayoutsPreference.CustomLayout(xml, currentEditingLayout));
                        found = true;
                        break;
                    }
                }
            }
            if (!found) items.add(new LayoutsPreference.CustomLayout(xml, currentEditingLayout));

            LayoutsPreference.save_to_preferences(prefs.edit(), items);
            config.refresh(getResources(), config.foldable_unfolded);
            Toast.makeText(this, "Layout Saved Internally", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void exportLayout() {
        try {
            String xml = KeyboardData.serialize_to_unified_xml(currentEditingLayout);
            File dir = new File("/storage/emulated/0/Download/ziaistan_keyboard_backup/");
            if (!dir.exists()) dir.mkdirs();
            String fileName = "custom_layout_" + currentEditingLayout.name.replaceAll("\\s+", "_") + ".xml";
            File file = new File(dir, fileName);
            try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                writer.write(xml);
            }
            Toast.makeText(this, "Layout Exported: " + file.getName(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String currentEditingLayoutToXml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!--\n");
        sb.append("  ZIAISTAN KEYBOARD PREMIUM LAYOUT FILE\n");
        sb.append("  This file contains the complete layout, theme, and behavior definitions.\n");
        sb.append("  \n");
        sb.append("  HOW TO CREATE THEMES PROGRAMMATICALLY:\n");
        sb.append("  1. Define colors in ARGB hex format (e.g., #FF1B1B1B).\n");
        sb.append("  2. Supported items in <theme>:\n");
        sb.append("     - kb (Keyboard Background)\n");
        sb.append("     - key (Default Key Background)\n");
        sb.append("     - label (Main Text Color)\n");
        sb.append("     - active (Pressed Key Background)\n");
        sb.append("     - radius (Key Corner Radius in DP)\n");
        sb.append("  3. Key assignments use canonical names (e.g., 'backspace', 'enter', 'shift').\n");
        sb.append("-->\n\n");
        sb.append("<ziaistan_custom_layout version=\"1\">\n");

        // 1. Metadata
        sb.append("  <metadata>\n");
        sb.append("    <layout_name>").append(escapeXml(currentEditingLayout.name)).append("</layout_name>\n");
        sb.append("    <created_at>").append(new SimpleDateFormat("yyyy-MM-dd").format(new Date())).append("</created_at>\n");
        sb.append("  </metadata>\n");


        // 3. Keyboard Layout
        sb.append("  <keyboard script=\"").append(escapeXml(currentEditingLayout.script)).append("\">\n");
        for (KeyboardData.Row row : currentEditingLayout.rows) {
            sb.append("    <row height=\"").append(row.height).append("\" shift=\"").append(row.shift).append("\">\n");
            for (KeyboardData.Key key : row.keys) {
                sb.append("      <key width=\"").append(key.width).append("\" shift=\"").append(key.shift).append("\"");
                if (key.borderRadius >= 0) sb.append(" border_radius=\"").append(key.borderRadius).append("\"");

                String[] synonyms = {"c", "nw", "ne", "sw", "se", "w", "e", "n", "s"};
                for (int i = 0; i < 9; i++) {
                    if (key.keys[i] != null) {
                        sb.append(" ").append(synonyms[i]).append("=\"").append(escapeXml(KeyValue.getCanonicalName(key.keys[i]))).append("\"");
                    }
                    if (key.circle != null && key.circle[i] != null) {
                         sb.append(" circ_").append(synonyms[i]).append("=\"").append(escapeXml(KeyValue.getCanonicalName(key.circle[i]))).append("\"");
                    }
                    if (key.anticircle_v2 != null && key.anticircle_v2[i] != null) {
                         sb.append(" anti_").append(synonyms[i]).append("=\"").append(escapeXml(KeyValue.getCanonicalName(key.anticircle_v2[i]))).append("\"");
                    }
                    if (key.colorLight[i] != null) sb.append(" ").append(synonyms[i]).append("_color_light=\"").append(escapeXml(key.colorLight[i])).append("\"");
                    if (key.colorDark[i] != null) sb.append(" ").append(synonyms[i]).append("_color_dark=\"").append(escapeXml(key.colorDark[i])).append("\"");
                }
                if (key.anticircle != null) sb.append(" anticircle=\"").append(escapeXml(KeyValue.getCanonicalName(key.anticircle))).append("\"");
                if (key.indication != null) sb.append(" indication=\"").append(escapeXml(key.indication)).append("\"");
                sb.append(" />\n");
            }
            sb.append("    </row>\n");
        }
        sb.append("  </keyboard>\n");

        // 4. Surroundings (Calculated dynamically if not already present to ensure auto-correction works)
        Map<Character, List<Character>> map = currentEditingLayout.surroundings;
        if (map == null) map = KeyboardLayoutAnalyzer.getAdjacencyMap(currentEditingLayout);

        if (map != null && !map.isEmpty()) {
            sb.append("  <surroundings>\n");
            for (Map.Entry<Character, List<Character>> entry : map.entrySet()) {
                sb.append("    <char value=\"").append(escapeXml(String.valueOf(entry.getKey()))).append("\" neighbors=\"");
                for (char c : entry.getValue()) sb.append(escapeXml(String.valueOf(c)));
                sb.append("\" />\n");
            }
            sb.append("  </surroundings>\n");
        }

        sb.append("</ziaistan_custom_layout>");
        return sb.toString();
    }

    private int getSelectedPosition(android.widget.RadioGroup group) {
        int id = group.getCheckedRadioButtonId();
        if (id == R.id.pos_nw) return 1;
        if (id == R.id.pos_ne) return 2;
        if (id == R.id.pos_sw) return 3;
        if (id == R.id.pos_se) return 4;
        if (id == R.id.pos_w) return 5;
        if (id == R.id.pos_e) return 6;
        if (id == R.id.pos_n) return 7;
        if (id == R.id.pos_s) return 8;
        return 0;
    }

    private void updateKeyPositionEnhanced(int rowIdx, int colIdx, int posIndex, int gestureType, String newChar, String actionId, boolean append) {
        List<KeyboardData.Row> newRows = new ArrayList<>();
        KeyValue newKv = null;
        if (newChar != null) newKv = KeyValue.getKeyByName(newChar);
        else if (actionId != null) newKv = KeyValue.getKeyByName(actionId);

        for (int r = 0; r < currentEditingLayout.rows.size(); r++) {
            KeyboardData.Row row = currentEditingLayout.rows.get(r);
            if (r == rowIdx) {
                List<KeyboardData.Key> newKeys = new ArrayList<>(row.keys);
                KeyboardData.Key key = newKeys.get(colIdx);
                KeyValue[] ks = Arrays.copyOf(key.keys, 9);
                KeyValue[] circ = Arrays.copyOf(key.circle, 9);
                KeyValue[] anti_v2 = Arrays.copyOf(key.anticircle_v2, 9);

                KeyValue current;
                if (gestureType == 0) current = ks[posIndex];
                else if (gestureType == 1) current = circ[posIndex];
                else current = anti_v2[posIndex];

                KeyValue finalKv = newKv;
                if (append && current != null && newKv != null) {
                    finalKv = KeyValue.makeMacro(current.getString(), mergeMacros(current, newKv), 0);
                }

                if (gestureType == 0) ks[posIndex] = finalKv;
                else if (gestureType == 1) circ[posIndex] = finalKv;
                else if (gestureType == 2) anti_v2[posIndex] = finalKv;

                newKeys.set(colIdx, new KeyboardData.Key(ks, circ, anti_v2, key.anticircle, 0, key.width, key.shift, key.borderRadius, key.indication, key.labelScales, key.colorDark, key.colorLight));
                newRows.add(new KeyboardData.Row(newKeys, row.height, row.shift));
            } else {
                newRows.add(row);
            }
        }
        currentEditingLayout = new KeyboardData(currentEditingLayout, newRows);
    }

    private KeyValue[] mergeMacros(KeyValue a, KeyValue b) {
        List<KeyValue> list = new ArrayList<>();
        if (a.getKind() == KeyValue.Kind.Macro) list.addAll(Arrays.asList(a.getMacro()));
        else list.add(a);
        if (b.getKind() == KeyValue.Kind.Macro) list.addAll(Arrays.asList(b.getMacro()));
        else list.add(b);
        return list.toArray(new KeyValue[0]);
    }

    private void showAddKeyDirectionMenu(KeyboardData.Key selectedKey) {
        new AlertDialog.Builder(this)
            .setTitle("Add Key Near Selection")
            .setItems(new String[]{"Add Left", "Add Right", "Add Above (New Row)", "Add Below (New Row)"}, (dialog, which) -> {
                pushToUndo();
                switch(which) {
                    case 0: addKeySide(selectedKey, true); break;
                    case 1: addKeySide(selectedKey, false); break;
                    case 2: addKeyAboveBelow(selectedKey, true); break;
                    case 3: addKeyAboveBelow(selectedKey, false); break;
                }
                liveKeyboardView.setKeyboard(currentEditingLayout);
            })
            .show();
    }

    private void addKeySide(KeyboardData.Key target, boolean left) {
        List<KeyboardData.Row> newRows = new ArrayList<>();
        for (KeyboardData.Row row : currentEditingLayout.rows) {
            int idx = row.keys.indexOf(target);
            if (idx != -1) {
                List<KeyboardData.Key> newKeys = new ArrayList<>(row.keys);
                newKeys.add(left ? idx : idx + 1, new KeyboardData.Key(new KeyValue[9], new KeyValue[9], new KeyValue[9], null, 0, 1.0f, 0, -1f, null, new float[9], new String[9], new String[9]));
                KeyboardData.Row newRow = new KeyboardData.Row(newKeys, row.height, row.shift);
                newRows.add(newRow.updateWidth(currentEditingLayout.keysWidth));
            } else {
                newRows.add(row);
            }
        }
        currentEditingLayout = new KeyboardData(currentEditingLayout, newRows);
    }

    private void addKeyAboveBelow(KeyboardData.Key target, boolean above) {
        List<KeyboardData.Row> newRows = new ArrayList<>();
        int targetRowIdx = -1;
        int targetColIdx = -1;
        for (int r = 0; r < currentEditingLayout.rows.size(); r++) {
            int c = currentEditingLayout.rows.get(r).keys.indexOf(target);
            if (c != -1) { targetRowIdx = r; targetColIdx = c; break; }
        }

        if (targetRowIdx != -1) {
            KeyboardData.Row oldRow = currentEditingLayout.rows.get(targetRowIdx);
            List<KeyboardData.Key> keys = new ArrayList<>();
            // Match target's horizontal position by adding empty spacer or shift logic (simplified: new row with 1 key)
            keys.add(new KeyboardData.Key(new KeyValue[9], new KeyValue[9], new KeyValue[9], null, 0, 1.0f, 0, -1f, null, new float[9], new String[9], new String[9]));
            KeyboardData.Row newRow = new KeyboardData.Row(keys, oldRow.height, oldRow.shift);

            newRows.addAll(currentEditingLayout.rows);
            newRows.add(above ? targetRowIdx : targetRowIdx + 1, newRow.updateWidth(currentEditingLayout.keysWidth));
            currentEditingLayout = new KeyboardData(currentEditingLayout, newRows);
        }
    }

    private void toggleSurrounding(KeyboardData.Key target, KeyboardData.Key neighbor) {
        char targetChar = getPrimaryChar(target);
        char neighborChar = getPrimaryChar(neighbor);
        if (targetChar == 0 || neighborChar == 0 || targetChar == neighborChar) return;

        pushToUndo();
        Map<Character, List<Character>> surr = currentEditingLayout.surroundings;
        if (surr == null) surr = new HashMap<>(KeyboardLayoutAnalyzer.getAdjacencyMap(currentEditingLayout));
        else surr = new HashMap<>(surr);

        List<Character> neighbors = surr.get(targetChar);
        if (neighbors == null) neighbors = new ArrayList<>();
        else neighbors = new ArrayList<>(neighbors);

        if (neighbors.contains(neighborChar)) neighbors.remove((Character)neighborChar);
        else neighbors.add(neighborChar);

        surr.put(targetChar, neighbors);
        currentEditingLayout = new KeyboardData(currentEditingLayout.rows, currentEditingLayout.keysWidth, currentEditingLayout.modmap, currentEditingLayout.script, currentEditingLayout.numpad_script, currentEditingLayout.name, currentEditingLayout.bottom_row, currentEditingLayout.embedded_number_row, currentEditingLayout.locale_extra_keys, currentEditingLayout.wordlist, surr);

        Toast.makeText(this, "Surroundings updated for '" + targetChar + "'", Toast.LENGTH_SHORT).show();
    }

    private char getPrimaryChar(KeyboardData.Key k) {
        if (k.keys[0] != null && k.keys[0].getKind() == KeyValue.Kind.Char) return k.keys[0].getChar();
        return 0;
    }

    private void addRow() {
        pushToUndo();
        List<KeyboardData.Row> newRows = new ArrayList<>(currentEditingLayout.rows);
        List<KeyboardData.Key> keys = new ArrayList<>();
        keys.add(new KeyboardData.Key(new KeyValue[9], new KeyValue[9], new KeyValue[9], null, 0, 1.0f, 0, -1f, null, new float[9], new String[9], new String[9]));
        KeyboardData.Row newRow = new KeyboardData.Row(keys, 1.0f, 0);
        newRows.add(0, newRow.updateWidth(currentEditingLayout.keysWidth));
        currentEditingLayout = new KeyboardData(currentEditingLayout, newRows);
        liveKeyboardView.setKeyboard(currentEditingLayout);
    }

    private void removeRow() {
        if (currentEditingLayout.rows.size() <= 1) return;
        pushToUndo();
        List<KeyboardData.Row> newRows = new ArrayList<>(currentEditingLayout.rows);
        newRows.remove(0);
        currentEditingLayout = new KeyboardData(currentEditingLayout, newRows);
        liveKeyboardView.setKeyboard(currentEditingLayout);
    }

    private void updateKeyLabelScale(int rowIdx, int colIdx, int pos, float scale) {
        List<KeyboardData.Row> newRows = new ArrayList<>();
        for (int r = 0; r < currentEditingLayout.rows.size(); r++) {
            KeyboardData.Row row = currentEditingLayout.rows.get(r);
            if (r == rowIdx) {
                List<KeyboardData.Key> newKeys = new ArrayList<>(row.keys);
                KeyboardData.Key key = newKeys.get(colIdx);
                float[] newScales = Arrays.copyOf(key.labelScales, 9);
                newScales[pos] = scale;
                newKeys.set(colIdx, new KeyboardData.Key(key.keys, key.circle, key.anticircle_v2, key.anticircle, 0, key.width, key.shift, key.borderRadius, key.indication, newScales, key.colorDark, key.colorLight));
                newRows.add(new KeyboardData.Row(newKeys, row.height, row.shift));
            } else {
                newRows.add(row);
            }
        }
        currentEditingLayout = new KeyboardData(currentEditingLayout, newRows);
    }
}
