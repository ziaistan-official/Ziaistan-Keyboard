package juloo.keyboard2;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KeyboardExecutors {
    /**
     * A high-priority executor for running background tasks that need to complete quickly,
     * such as on-the-fly auto-correction.
     */
    public static final ExecutorService HIGH_PRIORITY_EXECUTOR = Executors.newCachedThreadPool();
}