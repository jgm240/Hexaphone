package org.hexaphone.performance;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Quick Settings tile mirroring MainActivity's SELinux permissive
 * button -- same SelinuxState helper, same
 * hexaphone_selinux_permissive init service underneath. More
 * discoverable for debugging than digging into System Tweaks each time,
 * at the cost of putting a "make this device less secure" control one
 * swipe-down away; see the in-app warning text for why this still isn't
 * something to leave on.
 */
public class SelinuxPermissiveTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        refresh();
    }

    @Override
    public void onClick() {
        super.onClick();
        boolean goingPermissive = SelinuxState.isEnforcing();
        SelinuxState.setPermissive(goingPermissive);
        refresh();
    }

    private void refresh() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        boolean permissive = !SelinuxState.isEnforcing();
        tile.setState(permissive ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.qs_tile_label));
        tile.updateTile();
    }
}
