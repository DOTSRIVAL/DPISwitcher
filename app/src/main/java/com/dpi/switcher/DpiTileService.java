package com.dpi.switcher;

import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.N)
public class DpiTileService extends TileService {
    private static final String TAG = "DpiSwitcher/TileSvc";
    private StateManager state;
    private volatile boolean isCycling = false;

    @Override
    public void onCreate() {
        super.onCreate();
        state = new StateManager(this);
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        refreshTile();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        refreshTile();
    }

    @Override
    public void onClick() {
        if (isCycling) return;
        isCycling = true;
        setTileLabel("...");

        DpiController.getCurrentDpi(currentDpi -> {
            final int activeDpi = (currentDpi == 0) ? state.getCurrentDpi() : currentDpi;
            int[] presets = state.getCustomPresets();
            if (presets.length == 0) {
                isCycling = false;
                setTileLabel("Empty");
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::refreshTile, 2000);
                return;
            }
            
            int nextIndex = state.getNextPresetIndex(activeDpi);
            int nextDpi = presets[nextIndex];

            DpiController.setDpi(nextDpi, result -> {
                isCycling = false;
                if (result.success) {
                    state.setCurrentDpi(result.dpi);
                    if (state.getOriginalDpi() == 0) {
                        state.setOriginalDpi(activeDpi > 0 ? activeDpi : 392);
                    }
                    setTileLabel(String.valueOf(result.dpi));
                } else {
                    setTileLabel("Error");
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed(this::refreshTile, 2000);
                }
            });
        });
    }

    private void refreshTile() {
        DpiController.getCurrentDpi(dpi -> {
            if (dpi > 0) {
                state.setCurrentDpi(dpi);
                setTileLabel(String.valueOf(dpi));
            } else {
                int cached = state.getCurrentDpi();
                if (cached > 0) {
                    setTileLabel(String.valueOf(cached));
                } else {
                    setTileLabel("--");
                }
            }
        });
    }

    private void setTileLabel(String subtitle) {
        Tile tile = getQsTile();
        if (tile == null) return;
        
        int iconId = getSharedPreferences("DpiSwitcherPrefs", Context.MODE_PRIVATE).getInt("tile_icon", android.R.drawable.ic_menu_preferences);
        try {
            tile.setIcon(android.graphics.drawable.Icon.createWithResource(this, iconId));
        } catch (Exception ignored) {}
        
        if (subtitle.equals("--") || subtitle.equals("Error") || subtitle.equals("...")) {
            tile.setLabel(getString(R.string.tile_label));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.setSubtitle(subtitle);
        } else {
            tile.setLabel(subtitle); // e.g. "392"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.setSubtitle("DPI");
        }
        
        boolean isActive = !subtitle.equals("--") && !subtitle.equals("Error") && !subtitle.equals("...");
        tile.setState(isActive ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    public static void requestUpdate(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            TileService.requestListeningState(ctx, new ComponentName(ctx, DpiTileService.class));
        }
    }
}
