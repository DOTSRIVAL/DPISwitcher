package com.dpitile.switcher;

import android.content.Context;
import android.content.SharedPreferences;

public class StateManager {

    private static final String PREFS = "dpi_switcher_prefs";
    private static final String KEY_ORIGINAL_DPI    = "original_dpi";
    private static final String KEY_CURRENT_DPI     = "current_dpi";
    private static final String KEY_REMEMBER_LAST    = "remember_last";
    private static final String KEY_PRESET_INDEX     = "preset_index";
    private static final String KEY_ROOT_GRANTED     = "root_granted";

    private static final String KEY_CUSTOM_PRESETS   = "custom_presets";
    public static final String DEFAULT_PRESETS = "";

    private final SharedPreferences prefs;

    public StateManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public int getOriginalDpi()          { return prefs.getInt(KEY_ORIGINAL_DPI, 0); }
    public void setOriginalDpi(int dpi)  { prefs.edit().putInt(KEY_ORIGINAL_DPI, dpi).apply(); }

    public int getCurrentDpi()           { return prefs.getInt(KEY_CURRENT_DPI, 0); }
    public void setCurrentDpi(int dpi)   { prefs.edit().putInt(KEY_CURRENT_DPI, dpi).apply(); }

    public boolean isRememberLast()      { return prefs.getBoolean(KEY_REMEMBER_LAST, true); }
    public void setRememberLast(boolean v){ prefs.edit().putBoolean(KEY_REMEMBER_LAST, v).apply(); }

    public int getPresetIndex()          { return prefs.getInt(KEY_PRESET_INDEX, 0); }
    public void setPresetIndex(int i)    { prefs.edit().putInt(KEY_PRESET_INDEX, i).apply(); }

    public boolean isRootGranted()       { return prefs.getBoolean(KEY_ROOT_GRANTED, false); }
    public void setRootGranted(boolean v){ prefs.edit().putBoolean(KEY_ROOT_GRANTED, v).apply(); }

    public boolean isShizukuGranted()    { return prefs.getBoolean("shizuku_granted", false); }
    public void setShizukuGranted(boolean v){ prefs.edit().putBoolean("shizuku_granted", v).apply(); }

    public String getCustomPresetsString() {
        return prefs.getString(KEY_CUSTOM_PRESETS, DEFAULT_PRESETS);
    }
    public void setCustomPresetsString(String csv) {
        prefs.edit().putString(KEY_CUSTOM_PRESETS, csv).apply();
    }
    
    public int getPhysicalDensity() {
        return prefs.getInt("physical_density", 0);
    }
    public void setPhysicalDensity(int pd) {
        prefs.edit().putInt("physical_density", pd).apply();
    }

    public int[] getCustomPresets() {
        String csv = getCustomPresetsString();
        if (csv == null || csv.trim().isEmpty()) return new int[0];
        
        String[] parts = csv.split(",");
        java.util.ArrayList<Integer> valid = new java.util.ArrayList<>();
        for (String p : parts) {
            try {
                int val = Integer.parseInt(p.trim());
                if (val > 0) valid.add(val);
            } catch (Exception ignored) {}
        }
        
        int[] arr = new int[valid.size()];
        for(int i=0; i<valid.size(); i++) arr[i] = valid.get(i);
        return arr;
    }

    public int getNextPresetIndex(int currentDpi) {
        int[] presets = getCustomPresets();
        if (presets.length == 0) return 0;
        
        for (int i = 0; i < presets.length; i++) {
            if (presets[i] == currentDpi) {
                return (i + 1) % presets.length;
            }
        }
        return 0;
    }
}
