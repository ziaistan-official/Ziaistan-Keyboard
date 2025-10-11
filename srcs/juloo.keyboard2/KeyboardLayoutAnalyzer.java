package juloo.keyboard2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeyboardLayoutAnalyzer {

    // Helper class to store key and its calculated position and dimensions.
    private static class KeyWithPos {
        final KeyboardData.Key key;
        final float x;
        final float y;
        final float width;
        final float height;

        KeyWithPos(KeyboardData.Key key, float x, float y, float width, float height) {
            this.key = key;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public static Map<Character, List<Character>> getAdjacencyMap(KeyboardData keyboardData) {
        Map<Character, List<Character>> adjacencyMap = new HashMap<>();
        if (keyboardData == null || keyboardData.rows == null) {
            return adjacencyMap;
        }

        List<KeyWithPos> allKeysWithPos = new ArrayList<>();
        float currentY = 0;
        for (KeyboardData.Row row : keyboardData.rows) {
            currentY += row.shift;
            float currentX = 0;
            for (KeyboardData.Key key : row.keys) {
                currentX += key.shift;
                allKeysWithPos.add(new KeyWithPos(key, currentX, currentY, key.width, row.height));
                currentX += key.width;
            }
            currentY += row.height;
        }

        for (KeyWithPos keyWithPos : allKeysWithPos) {
            char keyChar = getKeyChar(keyWithPos.key);
            if (keyChar == 0) continue;

            List<Character> neighbors = new ArrayList<>();
            for (KeyWithPos otherKeyWithPos : allKeysWithPos) {
                if (keyWithPos == otherKeyWithPos) continue;

                if (isAdjacent(keyWithPos, otherKeyWithPos)) {
                    char otherKeyChar = getKeyChar(otherKeyWithPos.key);
                    if (otherKeyChar != 0) {
                        neighbors.add(otherKeyChar);
                    }
                }
            }
            adjacencyMap.put(keyChar, neighbors);
        }

        return adjacencyMap;
    }

    private static char getKeyChar(KeyboardData.Key key) {
        // The main character is at index 0.
        KeyValue kv = key.getKeyValue(0);
        if (kv != null && kv.getKind() == KeyValue.Kind.Char) {
            return Character.toLowerCase(kv.getChar());
        }
        return 0; // Not a character key
    }

    private static boolean isAdjacent(KeyWithPos key1, KeyWithPos key2) {
        float key1CenterX = key1.x + key1.width / 2;
        float key1CenterY = key1.y + key1.height / 2;
        float key2CenterX = key2.x + key2.width / 2;
        float key2CenterY = key2.y + key2.height / 2;

        float dx = Math.abs(key1CenterX - key2CenterX);
        float dy = Math.abs(key1CenterY - key2CenterY);

        // A simple adjacency check: if the distance between centers is less than 1.5 times the key width/height
        float maxDistanceX = (key1.width + key2.width) / 2 * 1.5f;
        float maxDistanceY = (key1.height + key2.height) / 2 * 1.5f;

        return dx < maxDistanceX && dy < maxDistanceY;
    }
}
