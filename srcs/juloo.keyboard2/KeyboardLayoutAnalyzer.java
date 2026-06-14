package juloo.keyboard2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeyboardLayoutAnalyzer {


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

            KeyWithPos bestLeft = null, bestRight = null, bestTop = null, bestBottom = null;
            float distL = Float.MAX_VALUE, distR = Float.MAX_VALUE, distT = Float.MAX_VALUE, distB = Float.MAX_VALUE;

            float cx = keyWithPos.x + keyWithPos.width / 2;
            float cy = keyWithPos.y + keyWithPos.height / 2;

            for (KeyWithPos other : allKeysWithPos) {
                if (keyWithPos == other) continue;
                char otherChar = getKeyChar(other.key);
                // Only consider keys that have a valid character for surroundings
                if (otherChar == 0) continue;

                float ocx = other.x + other.width / 2;
                float ocy = other.y + other.height / 2;
                float dx = ocx - cx;
                float dy = ocy - cy;
                float distSq = dx * dx + dy * dy;

                // Max distance check: 5.0x average dimension to find neighbors across large action buttons
                float maxDist = (keyWithPos.width + keyWithPos.height) * 2.5f;
                if (distSq > maxDist * maxDist) continue;

                double angle = Math.atan2(dy, dx); // -PI to PI

                if (angle >= -Math.PI/4 && angle < Math.PI/4) { // Right
                    if (distSq < distR) { distR = distSq; bestRight = other; }
                } else if (angle >= Math.PI/4 && angle < 3*Math.PI/4) { // Bottom
                    if (distSq < distB) { distB = distSq; bestBottom = other; }
                } else if (angle >= -3*Math.PI/4 && angle < -Math.PI/4) { // Top
                    if (distSq < distT) { distT = distSq; bestTop = other; }
                } else { // Left
                    if (distSq < distL) { distL = distSq; bestLeft = other; }
                }
            }

            List<Character> neighbors = new ArrayList<>();
            if (bestLeft != null) neighbors.add(getKeyChar(bestLeft.key));
            if (bestRight != null) neighbors.add(getKeyChar(bestRight.key));
            if (bestTop != null) neighbors.add(getKeyChar(bestTop.key));
            if (bestBottom != null) neighbors.add(getKeyChar(bestBottom.key));

            adjacencyMap.put(keyChar, neighbors);
        }

        return adjacencyMap;
    }

    private static char getKeyChar(KeyboardData.Key key) {

        KeyValue kv = key.getKeyValue(0);
        if (kv != null && kv.getKind() == KeyValue.Kind.Char) {
            return Character.toLowerCase(kv.getChar());
        }
        return 0;
    }

    public static String detectScript(List<KeyboardData.Row> rows) {
        if (rows == null) return "latin";
        for (KeyboardData.Row row : rows) {
            for (KeyboardData.Key key : row.keys) {
                for (int i = 0; i < 9; i++) {
                    KeyValue kv = key.keys[i];
                    if (kv != null && kv.getKind() == KeyValue.Kind.Char) {
                        if (Utils.isUrdu(String.valueOf(kv.getChar()))) {
                            return "urdu";
                        }
                    }
                }
            }
        }
        return "latin";
    }

    private static boolean isAdjacent(KeyWithPos key1, KeyWithPos key2) {
        float key1CenterX = key1.x + key1.width / 2;
        float key1CenterY = key1.y + key1.height / 2;
        float key2CenterX = key2.x + key2.width / 2;
        float key2CenterY = key2.y + key2.height / 2;

        float dx = Math.abs(key1CenterX - key2CenterX);
        float dy = Math.abs(key1CenterY - key2CenterY);


        float maxDistanceX = (key1.width + key2.width) / 2 * 1.5f;
        float maxDistanceY = (key1.height + key2.height) / 2 * 1.5f;

        return dx < maxDistanceX && dy < maxDistanceY;
    }
}
