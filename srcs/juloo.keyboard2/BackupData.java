package juloo.keyboard2;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

public class BackupData {
    private static final int CURRENT_VERSION = 1;

    private int version;
    private long timestamp;
    private Map<String, Object> settings;
    private Map<String, Map<String, Object>> featureStates;
    public BackupData() {
        this.version = CURRENT_VERSION;
        this.timestamp = System.currentTimeMillis();
        this.settings = new HashMap<>();
        this.featureStates = new HashMap<>();
    }

    public void putSetting(String key, Object value) {
        settings.put(key, value);
    }


    public void putFeatureState(String featureName, Map<String, Object> state) {
        featureStates.put(featureName, state);
    }

    private String appVersion = "unknown";

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }


    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("version", version);
        json.put("timestamp", timestamp);
        json.put("app_version", appVersion);


        JSONObject settingsJson = new JSONObject();
        JSONObject typesJson = new JSONObject();
        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            settingsJson.put(entry.getKey(), entry.getValue());
            if (entry.getValue() instanceof Float) {
                typesJson.put(entry.getKey(), "float");
            } else if (entry.getValue() instanceof Long) {
                typesJson.put(entry.getKey(), "long");
            }
        }
        json.put("settings", settingsJson);
        json.put("types", typesJson);


        JSONObject featuresJson = new JSONObject();
        for (Map.Entry<String, Map<String, Object>> entry : featureStates.entrySet()) {
            JSONObject featureJson = new JSONObject();
            for (Map.Entry<String, Object> stateEntry : entry.getValue().entrySet()) {
                featureJson.put(stateEntry.getKey(), stateEntry.getValue());
            }
            featuresJson.put(entry.getKey(), featureJson);
        }
        json.put("features", featuresJson);

        return json;
    }


    public static BackupData fromJSON(JSONObject json) throws JSONException {
        BackupData backup = new BackupData();
        backup.version = json.optInt("version", 1);
        backup.timestamp = json.optLong("timestamp", 0);

        JSONObject settingsJson = json.optJSONObject("settings");
        JSONObject typesJson = json.optJSONObject("types");
        if (settingsJson != null) {
            Iterator<String> keys = settingsJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = settingsJson.get(key);


                if (typesJson != null && typesJson.has(key)) {
                    String type = typesJson.optString(key);
                    if ("float".equals(type) && value instanceof Number) {
                        value = ((Number) value).floatValue();
                    } else if ("long".equals(type) && value instanceof Number) {
                        value = ((Number) value).longValue();
                    }
                }

                backup.settings.put(key, value);
            }
        }


        JSONObject featuresJson = json.optJSONObject("features");
        if (featuresJson != null) {
            Iterator<String> featureKeys = featuresJson.keys();
            while (featureKeys.hasNext()) {
                String featureName = featureKeys.next();
                JSONObject featureJson = featuresJson.getJSONObject(featureName);

                Map<String, Object> featureState = new HashMap<>();
                Iterator<String> stateKeys = featureJson.keys();
                while (stateKeys.hasNext()) {
                    String stateKey = stateKeys.next();
                    featureState.put(stateKey, featureJson.get(stateKey));
                }
                backup.featureStates.put(featureName, featureState);
            }
        }

        return backup;
    }

    public Map<String, Object> getSettings() {
        return settings;
    }

    public Map<String, Map<String, Object>> getFeatureStates() {
        return featureStates;
    }

    public int getVersion() {
        return version;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
