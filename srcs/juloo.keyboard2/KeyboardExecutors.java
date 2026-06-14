package juloo.keyboard2;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KeyboardExecutors {

    public static final ExecutorService HIGH_PRIORITY_EXECUTOR = Executors.newCachedThreadPool();
    public static final ExecutorService SUGGESTION_EXECUTOR = Executors.newSingleThreadExecutor();
}