package com.whitepoint.reducer

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile that smoothly toggles the built-in Reduce White Point feature.
 * Long-pressing the tile opens MainActivity (ACTION_QS_TILE_PREFERENCES).
 */
class WhitePointTileService : TileService() {

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (!DimmingEngine.isTransitioning) {
                refreshTile()
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        WhitePoint.registerObserver(contentResolver, observer)
        refreshTile()
    }

    override fun onStopListening() {
        WhitePoint.unregisterObserver(contentResolver, observer)
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (!WhitePoint.hasPermission(this)) {
            openApp()
            return
        }
        DimmingEngine.toggle(this, animate = true) {
            refreshTile()
        }
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val isOn = WhitePoint.isEnabled(this)
        val level = DimmingPreferences.getTargetLevel(this)

        tile.icon = Icon.createWithResource(this, R.drawable.ic_sun_ios_large)
        tile.label = getString(R.string.tile_label)
        tile.state = if (isOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = when {
            !WhitePoint.hasPermission(this) -> getString(R.string.tile_setup)
            isOn -> "$level%"
            else -> getString(R.string.tile_off)
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        fun requestTileUpdate(context: Context) {
            runCatching {
                requestListeningState(
                    context.applicationContext,
                    ComponentName(context.applicationContext, WhitePointTileService::class.java)
                )
            }
        }
    }
}
