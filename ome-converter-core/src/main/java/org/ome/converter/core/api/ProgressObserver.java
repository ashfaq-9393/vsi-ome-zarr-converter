package org.ome.converter.core.api;

public interface ProgressObserver {
    void onProgress(double percentage, long tilesProcessed, long totalTiles, String currentTask);
    void onLog(String level, String message);
    boolean isCancelled();
}
