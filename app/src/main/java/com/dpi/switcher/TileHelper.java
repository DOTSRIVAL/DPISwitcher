package com.dpi.switcher;

import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.util.concurrent.Executor;

public class TileHelper {
    public static void requestAddTile(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            StatusBarManager statusBarManager = context.getSystemService(StatusBarManager.class);
            if (statusBarManager != null) {
                ComponentName componentName = new ComponentName(context, DpiTileService.class);
                statusBarManager.requestAddTileService(
                        componentName,
                        context.getString(R.string.tile_label),
                        android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_dpi),
                        new Executor() {
                            @Override
                            public void execute(Runnable command) {
                                command.run();
                            }
                        },
                        result -> Log.d("TileHelper", "Add tile result: " + result)
                );
            }
        }
    }
}
