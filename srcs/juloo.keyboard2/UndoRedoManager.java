package juloo.keyboard2;

import android.view.inputmethod.InputConnection;
import java.util.Stack;

public class UndoRedoManager {
    private static final int MAX_STACK_SIZE = 50;

    private interface Action {
        void undo(InputConnection ic);
        void redo(InputConnection ic);
    }

    private static class InsertAction implements Action {
        String text;
        int index;








        InsertAction(String text) {
            this.text = text;
        }

        @Override
        public void undo(InputConnection ic) {






            ic.deleteSurroundingText(text.length(), 0);
        }

        @Override
        public void redo(InputConnection ic) {
            ic.commitText(text, 1);
        }
    }

    private static class DeleteAction implements Action {
        String textBefore;
        String textAfter;

        DeleteAction(String textBefore, String textAfter) {
            this.textBefore = textBefore;
            this.textAfter = textAfter;
        }

        @Override
        public void undo(InputConnection ic) {












            if (textBefore != null && !textBefore.isEmpty()) {
                ic.commitText(textBefore, 1);
            }
            if (textAfter != null && !textAfter.isEmpty()) {


                ic.commitText(textAfter, 0);
            }
        }

        @Override
        public void redo(InputConnection ic) {
            int beforeLen = textBefore != null ? textBefore.length() : 0;
            int afterLen = textAfter != null ? textAfter.length() : 0;
            ic.deleteSurroundingText(beforeLen, afterLen);
        }
    }

    private static class ReplaceAction implements Action {
        String originalText;
        String newText;

        ReplaceAction(String original, String replacement) {
            this.originalText = original;
            this.newText = replacement;
        }

        @Override
        public void undo(InputConnection ic) {


            ic.deleteSurroundingText(newText.length(), 0);
            ic.commitText(originalText, 1);
        }

        @Override
        public void redo(InputConnection ic) {

             ic.deleteSurroundingText(originalText.length(), 0);
             ic.commitText(newText, 1);
        }
    }


    private static class BatchAction implements Action {
        Stack<Action> actions = new Stack<>();

        void add(Action a) {
            actions.push(a);
        }

        boolean isEmpty() { return actions.isEmpty(); }

        @Override
        public void undo(InputConnection ic) {

            for (int i = actions.size() - 1; i >= 0; i--) {
                actions.get(i).undo(ic);
            }
        }

        @Override
        public void redo(InputConnection ic) {
            for (Action a : actions) {
                a.redo(ic);
            }
        }
    }

    private Stack<Action> undoStack = new Stack<>();
    private Stack<Action> redoStack = new Stack<>();
    private BatchAction currentBatch = null;

    public void beginBatch() {
        if (currentBatch == null) {
            currentBatch = new BatchAction();
        }
    }

    public void endBatch() {
        if (currentBatch != null && !currentBatch.isEmpty()) {
            addToActionStack(currentBatch);
        }
        currentBatch = null;
    }

    private void addToActionStack(Action action) {
        if (currentBatch != null) {
            currentBatch.add(action);
        } else {
            undoStack.push(action);
            if (undoStack.size() > MAX_STACK_SIZE) {
                undoStack.remove(0);
            }
            redoStack.clear();
        }
    }

    public void recordInsert(String text) {
        if (text == null || text.isEmpty()) return;
        addToActionStack(new InsertAction(text));
    }

    public void recordDelete(String before, String after) {
        if ((before == null || before.isEmpty()) && (after == null || after.isEmpty())) return;
        addToActionStack(new DeleteAction(before, after));
    }

    public void recordReplace(String original, String replacement) {
         addToActionStack(new ReplaceAction(original, replacement));
    }

    public void undo(InputConnection ic) {
        if (ic == null || undoStack.isEmpty()) return;
        Action action = undoStack.pop();
        action.undo(ic);
        redoStack.push(action);
    }

    public void redo(InputConnection ic) {
        if (ic == null || redoStack.isEmpty()) return;
        Action action = redoStack.pop();
        action.redo(ic);
        undoStack.push(action);
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
        currentBatch = null;
    }
}
