package com.seogaemo.hmg.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Pure ordering and visibility rules for the Home pager. */
public final class HomeTileCatalog {
    public static final int TILES_PER_PAGE = 6;

    public static final class Entry {
        public final String moduleId;
        public final boolean enabled;
        public final HomeTile[] tiles;

        public Entry(String moduleId, boolean enabled, HomeTile[] tiles) {
            this.moduleId = moduleId;
            this.enabled = enabled;
            this.tiles = tiles;
        }
    }

    private static final class OwnedTile {
        final String moduleId;
        final HomeTile tile;
        OwnedTile(String moduleId, HomeTile tile) {
            this.moduleId = moduleId;
            this.tile = tile;
        }
    }

    private HomeTileCatalog() { }

    public static List<HomeTile> collect(HomeTile settings, Entry[] entries) {
        List<OwnedTile> moduleTiles = new ArrayList<OwnedTile>();
        for (Entry entry : entries) {
            if (entry.tiles == null) continue;
            for (HomeTile tile : entry.tiles) {
                if (tile != null && (entry.enabled || tile.alwaysVisible))
                    moduleTiles.add(new OwnedTile(entry.moduleId, tile));
            }
        }
        Collections.sort(moduleTiles, new Comparator<OwnedTile>() {
            public int compare(OwnedTile left, OwnedTile right) {
                if (left.tile.order != right.tile.order)
                    return left.tile.order < right.tile.order ? -1 : 1;
                int moduleOrder = left.moduleId.compareTo(right.moduleId);
                return moduleOrder != 0 ? moduleOrder : left.tile.id.compareTo(right.tile.id);
            }
        });
        List<HomeTile> result = new ArrayList<HomeTile>();
        result.add(settings);
        for (OwnedTile owned : moduleTiles) result.add(owned.tile);
        return result;
    }

    public static int pageCount(int tileCount) {
        return Math.max(1, (tileCount + TILES_PER_PAGE - 1) / TILES_PER_PAGE);
    }
}
