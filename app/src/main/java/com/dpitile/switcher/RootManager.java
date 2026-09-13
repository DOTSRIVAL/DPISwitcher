package com.dpitile.switcher;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * RootManager — handles all su/root operations.
 * Compatible with Magisk, KernelSU, and any standard su implementation.
 *
 * When exec() is called for the first time and root hasn't been granted,
 * Magisk/KernelSU automatically shows their "Grant root?" dialog.
 * The app does NOT need to implement this dialog — the root manager handles it.
 */
public class RootManager {

    private static final String TAG = "DpiSwitcher/Root";
    private static final int TIMEOUT_SECONDS = 12;

    private static final ExecutorService executor =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "dpi-root-thread");
                t.setDaemon(true);
                return t;
            });

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Result of a root command */
    public static class Result {
        public final int    exitCode;
        public final String stdout;
        public final String stderr;

        public Result(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout   = stdout  != null ? stdout.trim()  : "";
            this.stderr   = stderr  != null ? stderr.trim()  : "";
        }

        public boolean isSuccess() { return exitCode == 0; }

        @Override
        public String toString() {
            return "Result{exit=" + exitCode + ", stdout='" + stdout + "'}";
        }
    }

    public interface Callback { void onResult(Result result); }
    public interface BoolCallback { void onResult(boolean granted); }

    /**
     * Check if root is currently available (non-blocking, no new grant dialog).
     * Runs 'id' and checks uid=0 in background, returns on main thread.
     */
    public static void isRootAvailable(BoolCallback callback) {
        executor.submit(() -> {
            Result result = execBlocking("id");
            boolean granted = result.isSuccess() && result.stdout.contains("uid=0");
            Log.d(TAG, "isRootAvailable: " + granted + " (stdout=" + result.stdout + ")");
            mainHandler.post(() -> callback.onResult(granted));
        });
    }

    /**
     * Request root access — triggers Magisk/KernelSU grant dialog if not yet granted.
     * Runs 'id' as the trigger command.
     */
    public static void requestRoot(BoolCallback callback) {
        executor.submit(() -> {
            Result result = execBlocking("id");
            boolean granted = result.isSuccess() && result.stdout.contains("uid=0");
            Log.d(TAG, "requestRoot: granted=" + granted);
            mainHandler.post(() -> callback.onResult(granted));
        });
    }

    /**
     * Execute a shell command as root (async, result on main thread).
     */
    public static void exec(String command, Callback callback) {
        executor.submit(() -> {
            Result result = execBlocking(command);
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    /**
     * Blocking su execution — call only from background thread.
     */
    public static Result execBlocking(String command) {
        Log.d(TAG, "exec: " + command);
        Process proc = null;
        try {
            boolean useShizuku = false;
            try {
                if (rikka.shizuku.Shizuku.pingBinder() && 
                    rikka.shizuku.Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    useShizuku = true;
                }
            } catch (Throwable ignored) {}

            if (useShizuku) {
                java.lang.reflect.Method m = rikka.shizuku.Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
                m.setAccessible(true);
                proc = (Process) m.invoke(null, new String[]{"sh", "-c", command}, null, null);
            } else {
                ProcessBuilder pb = new ProcessBuilder("su", "-c", command);
                pb.environment().put("PATH", "/sbin:/system/bin:/system/xbin:/data/adb/ksu/bin");
                proc = pb.start();
            }

            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();

            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) out.append(line).append('\n');
            }
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(proc.getErrorStream()))) {
                String line;
                while ((line = r.readLine()) != null) err.append(line).append('\n');
            }

            boolean done = proc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            int exitCode = done ? proc.exitValue() : -1;
            if (!done) {
                proc.destroyForcibly();
                err.append("Timed out after ").append(TIMEOUT_SECONDS).append("s");
            }

            return new Result(exitCode, out.toString(), err.toString());

        } catch (Exception e) {
            Log.e(TAG, "execBlocking error: " + e.getMessage());
            return new Result(-1, "", e.getMessage());
        } finally {
            if (proc != null) proc.destroy();
        }
    }
}
