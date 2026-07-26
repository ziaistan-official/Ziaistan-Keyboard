package juloo.keyboard2;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClipboardItem {
    private String id;
    private long createdAt;
    private long lastUsedAt;
    private String source;
    private String contentPreview;
    private long contentLength;
    private boolean isPinned;
    private boolean isArchived;
    private List<String> tags;
    private int color;
    private String filePath;
    private String extension;
    private boolean hasBody;
    private Body body;
    private String contentHash;

    private boolean expanded = false;
    private boolean selected = false;

    public static class Body {
        public String type;
        public String text;

        public Body(String type, String text) {
            this.type = type;
            this.text = text;
        }
    }

    public ClipboardItem(String text, long timestamp, String source) {
        this.id = UUID.randomUUID().toString();
        this.createdAt = timestamp;
        this.lastUsedAt = timestamp;
        this.source = source;
        this.contentPreview = text.substring(0, Math.min(text.length(), 256));
        this.contentLength = text.length();
        this.isPinned = false;
        this.isArchived = false;
        this.tags = new ArrayList<>();
        this.color = 0;
        this.extension = "txt";
        this.hasBody = true;
        this.body = new Body("text", text);
        this.contentHash = calculateHash(text);
    }

    private ClipboardItem() {}

    public static String calculateHash(String text) {
        if (text == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(text.hashCode());
        }
    }

    public String getId() { return id; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(long lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public String getSource() { return source; }
    public String getContentPreview() { return contentPreview; }
    public long getContentLength() { return contentLength; }
    public boolean isPinned() { return isPinned; }
    public void setPinned(boolean pinned) { isPinned = pinned; }
    public boolean isArchived() { return isArchived; }
    public void setArchived(boolean archived) { isArchived = archived; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension; }
    public boolean hasBody() { return hasBody; }
    public void setHasBody(boolean hasBody) { this.hasBody = hasBody; }
    public Body getBody() { return body; }
    public void setBody(Body body) { this.body = body; }
    public String getContentHash() { return contentHash; }

    public String getText() {
        return (body != null) ? body.text : "";
    }

    public boolean isExpanded() { return expanded; }
    public void setExpanded(boolean expanded) { this.expanded = expanded; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }

    // Compatibility methods for existing code
    public String getName() { return contentPreview; }
    public void setName(String name) { this.contentPreview = name; }
    public long getTimestamp() { return createdAt; }
    public void setTimestamp(long timestamp) { this.createdAt = timestamp; }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("createdAt", createdAt);
        json.put("lastUsedAt", lastUsedAt);
        json.put("source", source);
        json.put("contentPreview", contentPreview);
        json.put("contentLength", contentLength);
        json.put("isPinned", isPinned);
        json.put("isArchived", isArchived);

        JSONArray tagsArray = new JSONArray();
        if (tags != null) {
            for (String tag : tags) {
                tagsArray.put(tag);
            }
        }
        json.put("tags", tagsArray);
        json.put("color", color);
        json.put("filePath", filePath);
        json.put("extension", extension);
        json.put("hasBody", hasBody);
        json.put("contentHash", contentHash);

        if (body != null) {
            JSONObject bodyJson = new JSONObject();
            bodyJson.put("type", body.type);
            bodyJson.put("text", body.text);
            json.put("body", bodyJson);
        }

        return json;
    }

    public static ClipboardItem fromJSON(JSONObject json) throws JSONException {
        ClipboardItem item = new ClipboardItem();
        item.id = json.optString("id", UUID.randomUUID().toString());
        item.createdAt = json.optLong("createdAt", json.optLong("timestamp", System.currentTimeMillis()));
        item.lastUsedAt = json.optLong("lastUsedAt", item.createdAt);
        item.source = json.optString("source", "clipboard");

        String legacyText = json.optString("text", null);

        if (json.has("body")) {
            JSONObject bodyJson = json.getJSONObject("body");
            item.body = new Body(bodyJson.optString("type", "text"), bodyJson.optString("text", ""));
            item.hasBody = true;
        } else if (legacyText != null) {
            item.body = new Body("text", legacyText);
            item.hasBody = true;
        }

        if (item.body != null) {
            item.contentPreview = json.optString("contentPreview", item.body.text.substring(0, Math.min(item.body.text.length(), 256)));
            item.contentLength = json.optLong("contentLength", item.body.text.length());
            item.contentHash = json.optString("contentHash", calculateHash(item.body.text));
        } else {
            item.contentPreview = json.optString("contentPreview", "");
            item.contentLength = json.optLong("contentLength", 0);
            item.contentHash = json.optString("contentHash", "");
        }

        item.isPinned = json.optBoolean("isPinned", json.optBoolean("pinned", false));
        item.isArchived = json.optBoolean("isArchived", json.optBoolean("archived", false));

        item.tags = new ArrayList<>();
        JSONArray tagsArray = json.optJSONArray("tags");
        if (tagsArray != null) {
            for (int i = 0; i < tagsArray.length(); i++) {
                item.tags.add(tagsArray.getString(i));
            }
        }

        item.color = json.optInt("color", 0);
        item.filePath = json.optString("filePath", null);
        item.extension = json.optString("extension", "txt");

        return item;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClipboardItem that = (ClipboardItem) o;
        return contentHash != null && contentHash.equals(that.contentHash);
    }

    @Override
    public int hashCode() {
        return contentHash != null ? contentHash.hashCode() : 0;
    }

    public ClipboardItem clone() {
        ClipboardItem item = new ClipboardItem();
        item.id = this.id;
        item.createdAt = this.createdAt;
        item.lastUsedAt = this.lastUsedAt;
        item.source = this.source;
        item.contentPreview = this.contentPreview;
        item.contentLength = this.contentLength;
        item.isPinned = this.isPinned;
        item.isArchived = this.isArchived;
        item.tags = new ArrayList<>(this.tags);
        item.color = this.color;
        item.filePath = this.filePath;
        item.extension = this.extension;
        item.hasBody = this.hasBody;
        if (this.body != null) {
            item.body = new Body(this.body.type, this.body.text);
        }
        item.contentHash = this.contentHash;
        item.expanded = this.expanded;
        item.selected = this.selected;
        return item;
    }
}
