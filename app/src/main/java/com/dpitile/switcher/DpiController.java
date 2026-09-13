package com.dpitile.switcher;

import android.os.Build;
import android.util.Log;

public class DpiController {

    private static final String TAG = "DpiSwitcher/DpiCtrl";
    public static final int MIN_SW = 320;
    
    public static class DeviceInfo {
        public int width;
        public int height;
        public int physicalDensity;
        public int defaultSw;
        public int maxSw;
        public String model;
        public int[] dynamicPresets;
        
        public void shuffleDynamicPresets() {
            if (maxSw <= defaultSw) return;
            dynamicPresets = new int[9];
            dynamicPresets[0] = defaultSw;
            java.util.List<Integer> list = new java.util.ArrayList<>();
            java.util.Random r = new java.util.Random();
            // generate 7 random integers between defaultSw + 10 and maxSw - 10
            int range = (maxSw - 10) - (defaultSw + 10);
            if (range <= 0) range = 1;
            for(int i=0; i<7; i++) {
                int rVal = (defaultSw + 10) + r.nextInt(range);
                int rem = rVal % 5;
                rVal = rVal - rem + (rem >= 3 ? 5 : 0);
                list.add(rVal);
            }
            java.util.Collections.sort(list);
            for(int i=0; i<7; i++) dynamicPresets[i+1] = list.get(i);
            dynamicPresets[8] = maxSw;
        }
    }
    
    public static DeviceInfo deviceInfo = null;

    public static class DpiResult {
        public final boolean success;
        public final int     dpi;
        public final String  method;
        public final String  error;

        public DpiResult(boolean success, int dpi, String method, String error) {
            this.success = success;
            this.dpi     = dpi;
            this.method  = method;
            this.error   = error;
        }
    }

    public interface DpiCallback { void onResult(DpiResult result); }
    public interface IntCallback  { void onResult(int dpi); }
    public interface DeviceCallback { void onResult(DeviceInfo info); }

    private static boolean isFetchingDevice = false;
    private static final java.util.List<DeviceCallback> pendingDeviceCallbacks = new java.util.ArrayList<>();

    public static void getDeviceInfo(DeviceCallback callback) {
        if (deviceInfo != null) { 
            callback.onResult(deviceInfo); 
            return; 
        }
        
        pendingDeviceCallbacks.add(callback);
        if (isFetchingDevice) return;
        isFetchingDevice = true;
        
        RootManager.exec("wm size && wm density", result -> {
            DeviceInfo info = new DeviceInfo();
            info.model = Build.MODEL;
            
            int w = 1080;
            int h = 2400;
            int pd = 440;
            try {
                String[] lines = result.stdout.split("\n");
                for (String l : lines) {
                    if (l.contains("Physical size")) {
                        String[] parts = l.split(":")[1].trim().split("x");
                        w = Math.min(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
                        h = Math.max(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
                    }
                    if (l.contains("Physical density")) {
                        pd = Integer.parseInt(l.split(":")[1].trim());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing wm size/density", e);
            }
            
            info.width = w;
            info.height = h;
            info.physicalDensity = pd;
            info.defaultSw = (w * 160) / pd;
            info.maxSw = (w * 160) / 72; // Android absolute minimum density is 72 dpi
            
            info.dynamicPresets = new int[9];
            int step = (info.maxSw - info.defaultSw) / 8;
            for (int i = 0; i < 8; i++) {
                int val = info.defaultSw + (i * step);
                int rem = val % 5;
                val = val - rem + (rem >= 3 ? 5 : 0);
                info.dynamicPresets[i] = val;
            }
            info.dynamicPresets[8] = info.maxSw;
            
            deviceInfo = info;
            isFetchingDevice = false;
            
            for (DeviceCallback cb : new java.util.ArrayList<>(pendingDeviceCallbacks)) {
                cb.onResult(deviceInfo);
            }
            pendingDeviceCallbacks.clear();
        });
    }

    public static String validate(int sw) {
        if (sw < MIN_SW) return "Value too low (minimum " + MIN_SW + ")";
        if (deviceInfo != null && sw > deviceInfo.maxSw) return "Exceeds max safe value (" + deviceInfo.maxSw + ")";
        return null;
    }

    private static int densityToSw(int density) {
        if (deviceInfo == null || density <= 0) return density;
        return (deviceInfo.width * 160) / density;
    }

    private static int swToDensity(int sw) {
        if (deviceInfo == null || sw <= 0) return sw;
        return (deviceInfo.width * 160) / sw;
    }

    public static void getCurrentDpi(IntCallback callback) {
        getDeviceInfo(info -> {
            RootManager.exec("wm density", result -> {
                int density = parseDpiFromWm(result.stdout);
                if (density == 0) {
                    RootManager.exec("getprop ro.sf.lcd_density", r2 -> {
                        int d2 = parseIntSafe(r2.stdout.trim());
                        callback.onResult(densityToSw(d2));
                    });
                } else {
                    callback.onResult(densityToSw(density));
                }
            });
        });
    }

    // Standard setDpi without safety dialog (used for quick tile)
    public static void setDpi(int targetSw, DpiCallback callback) {
        String err = validate(targetSw);
        if (err != null) {
            callback.onResult(new DpiResult(false, 0, "none", err));
            return;
        }

        getDeviceInfo(info -> {
            int targetDensity = swToDensity(targetSw);
            RootManager.exec("wm density " + targetDensity, result -> {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(() -> verifyAndFinish(targetSw, targetDensity, "wm_density", callback), 500);
            });
        });
    }

    // Safe mode setDpi with 10s auto-revert (used in Main App)
    public static void applyWithSafety(int targetSw, IntCallback onStart, DpiCallback callback) {
        String err = validate(targetSw);
        if (err != null) {
            callback.onResult(new DpiResult(false, 0, "none", err));
            return;
        }

        getDeviceInfo(info -> {
            int targetDensity = swToDensity(targetSw);
            
            RootManager.exec("wm density " + targetDensity, r1 -> {
                // Spawn the safety daemon. It blocks its own cached thread for 10s, which is perfectly safe.
                String revertCmd = "sleep 10 && wm density reset";
                RootManager.exec(revertCmd, r2 -> {});
                
                onStart.onResult(targetSw); // Trigger UI countdown immediately
                // Verify actual apply
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(() -> verifyAndFinish(targetSw, targetDensity, "safe_mode", callback), 1000);
            });
        });
    }

    public static void confirmKeepDpi() {
        RootManager.exec("pkill -f 'sleep 10 && wm density reset'", r -> {});
    }

    public static void cancelAndRevert(DpiCallback callback) {
        RootManager.exec("pkill -f 'sleep 10 && wm density reset'", r1 -> {
            resetDpi(callback);
        });
    }

    public static void resetDpi(DpiCallback callback) {
        RootManager.exec("wm density reset", result -> {
            if (result.isSuccess()) {
                getCurrentDpi(dpi -> callback.onResult(new DpiResult(true, dpi, "wm_reset", null)));
            } else {
                callback.onResult(new DpiResult(false, 0, "none", "Reset failed: " + result.stderr));
            }
        });
    }

    private static void verifyAndFinish(int targetSw, int targetDensity, String method, DpiCallback callback) {
        getCurrentDpi(actualSw -> {
            if (actualSw > 0 && Math.abs(actualSw - targetSw) <= 5) {
                callback.onResult(new DpiResult(true, actualSw, method, null));
            } else {
                String msg = actualSw == 0 ? "Could not read density after apply" : "Applied SW " + actualSw + " but requested " + targetSw;
                callback.onResult(new DpiResult(false, actualSw, method, msg));
            }
        });
    }

    public static int parseDpiFromWm(String output) {
        if (output == null || output.isEmpty()) return 0;
        for (String line : output.split("\n")) {
            if (line.toLowerCase().contains("override")) {
                int v = parseIntSafe(line.replaceAll("[^0-9]", ""));
                if (v >= 72) return v;
            }
        }
        for (String line : output.split("\n")) {
            if (line.toLowerCase().contains("physical density")) {
                int v = parseIntSafe(line.replaceAll("[^0-9]", ""));
                if (v >= 72) return v;
            }
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }
}
