package ua.stetsenkoinna.graphpresentation.training;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;

public final class SwingCapture {
    private SwingCapture() { }

    public static <T> T call(Callable<T> operation) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) return operation.call();
        FutureTask<T> task = new FutureTask<>(operation);
        SwingUtilities.invokeLater(task);
        try {
            return task.get(30, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException failure) {
            task.cancel(false);
            throw new IllegalStateException("The editor did not respond to the capture request", failure);
        }
    }
}
