package juloo.keyboard2;

import org.json.JSONException;
import org.json.JSONObject;

public class ClipboardItem {
    private static final String JSON_TEXT = "text";
    private static final String JSON_PINNED = "pinned";
    private static final String JSON_TIMESTAMP = "timestamp";
    private static final String JSON_ARCHIVED = "archived";
    private static final String JSON_NAME = "name";

    private final String text;
    private long timestamp;
    private boolean pinned;
    private boolean archived;
    private String name;


    private boolean expanded = false;

    public ClipboardItem(String text, long timestamp, boolean pinned) {
        this(text, timestamp, pinned, false, null);
    }

    public ClipboardItem(String text, long timestamp, boolean pinned, boolean archived, String name) {
        this.text = text;
        this.timestamp = timestamp;
        this.pinned = pinned;
        this.archived = archived;
        this.name = name;
    }

    public String getText() {
        return text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClipboardItem that = (ClipboardItem) o;
        return text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(JSON_TEXT, text);
        json.put(JSON_TIMESTAMP, timestamp);
        json.put(JSON_PINNED, pinned);
        json.put(JSON_ARCHIVED, archived);
        if (name != null) {
            json.put(JSON_NAME, name);
        }
        return json;
    }

    public static ClipboardItem fromJSON(JSONObject json) throws JSONException {
        String text = json.optString(JSON_TEXT, null);
        if (text == null || text.isEmpty()) {
            throw new JSONException("ClipboardItem text is missing or empty");
        }
        long timestamp = json.optLong(JSON_TIMESTAMP, System.currentTimeMillis());
        boolean pinned = json.optBoolean(JSON_PINNED, false);
        boolean archived = json.optBoolean(JSON_ARCHIVED, false);
        String name = json.optString(JSON_NAME, null);
        return new ClipboardItem(text, timestamp, pinned, archived, name);
    }
}
