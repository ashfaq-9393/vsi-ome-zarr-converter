package org.ome.converter.ui.viewmodel;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.ome.converter.core.event.AsyncEventBus;
import org.ome.converter.core.event.EventListener;
import org.ome.converter.core.event.LogEvent;
import org.ome.converter.core.event.ProgressEvent;
import org.ome.converter.core.model.ChunkSpec;
import org.ome.converter.core.model.ConversionRequest;
import org.ome.converter.core.model.ConversionResult;
import org.ome.converter.core.model.GapAnalysisResult;
import org.ome.converter.core.model.MetadataClassification;
import org.ome.converter.dao.api.SettingsRepository;
import org.ome.converter.dao.entity.JobEntity;
import org.ome.converter.dao.entity.UserSettingsEntity;
import org.ome.converter.dao.impl.JsonFileSettingsRepository;
import org.ome.converter.service.impl.ConversionOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class MainDashboardViewModel implements EventListener {
    private static final Logger log = LoggerFactory.getLogger(MainDashboardViewModel.class);

    private final StringProperty sourceFilePath = new SimpleStringProperty("");
    private final StringProperty targetDestinationPath = new SimpleStringProperty("");
    private final DoubleProperty progressPercentage = new SimpleDoubleProperty(0.0);
    private final StringProperty statusText = new SimpleStringProperty("Ready");
    private final BooleanProperty converting = new SimpleBooleanProperty(false);
    private final BooleanProperty preserveVendorMetadata = new SimpleBooleanProperty(true);
    private final ObjectProperty<org.ome.converter.core.model.OmeZarrVersion> targetVersion = new SimpleObjectProperty<>(org.ome.converter.core.model.OmeZarrVersion.OME_ZARR_0_5);

    // In-App Gap Analysis Properties (3 Simplified Classes with Direct Counts)
    private final ObjectProperty<GapAnalysisResult> gapAnalysisResult = new SimpleObjectProperty<>(null);
    private final BooleanProperty hasGapAnalysis = new SimpleBooleanProperty(false);

    // KPI Counts (Exact numbers)
    private final StringProperty mappedCountText = new SimpleStringProperty("0");
    private final StringProperty vendorDumpCountText = new SimpleStringProperty("0");
    private final StringProperty lossedCountText = new SimpleStringProperty("0");
    private final StringProperty totalCountText = new SimpleStringProperty("0");

    // Bar Fractions
    private final DoubleProperty mappedFraction = new SimpleDoubleProperty(0.0);
    private final DoubleProperty vendorDumpFraction = new SimpleDoubleProperty(0.0);
    private final DoubleProperty lossedFraction = new SimpleDoubleProperty(0.0);

    private final ObservableList<String> logMessages = FXCollections.observableArrayList();
    private final ObservableList<JobEntity> jobHistory = FXCollections.observableArrayList();
    private final ObservableList<GapAnalysisResult.GapAnalysisItemDetail> allFields = FXCollections.observableArrayList();
    private final ObservableList<GapAnalysisResult.GapAnalysisItemDetail> lossedFields = FXCollections.observableArrayList();

    private final ConversionOrchestrator orchestrator;
    private final SettingsRepository settingsRepository;
    private String currentJobId;

    public MainDashboardViewModel() {
        this(new ConversionOrchestrator(), new JsonFileSettingsRepository());
    }

    public MainDashboardViewModel(ConversionOrchestrator orchestrator, SettingsRepository settingsRepository) {
        this.orchestrator = orchestrator;
        this.settingsRepository = settingsRepository;

        UserSettingsEntity settings = settingsRepository.loadSettings();
        if (settings.lastDestinationDirectory() != null) {
            this.targetDestinationPath.set(settings.lastDestinationDirectory());
        } else {
            this.targetDestinationPath.set(System.getProperty("user.home"));
        }
        this.preserveVendorMetadata.set(settings.preserveVendorMetadata());

        AsyncEventBus.getInstance().register(this);
    }

    public void startConversion(Runnable onSuccess, java.util.function.Consumer<Exception> onError) {
        if (sourceFilePath.get().isBlank()) {
            onError.accept(new IllegalArgumentException("Please select a valid source VSI file."));
            return;
        }
        if (targetDestinationPath.get().isBlank()) {
            onError.accept(new IllegalArgumentException("Please select a target destination directory on disk."));
            return;
        }

        File srcFile = new File(sourceFilePath.get());
        if (!srcFile.exists() || !srcFile.isFile()) {
            onError.accept(new IllegalArgumentException("Source file does not exist: " + sourceFilePath.get()));
            return;
        }

        File destDir = new File(targetDestinationPath.get());
        if (!destDir.exists() || !destDir.isDirectory()) {
            onError.accept(new IllegalArgumentException("Target destination directory does not exist: " + targetDestinationPath.get()));
            return;
        }

        long freeSpaceBytes = destDir.getUsableSpace();
        if (freeSpaceBytes < 200 * 1024 * 1024L) { // Less than 200 MB free
            onError.accept(new IllegalStateException(String.format(
                "Disk Space Warning: Target drive has only %.2f MB free space. Conversion may fail due to limited storage.",
                freeSpaceBytes / (1024.0 * 1024.0)
            )));
            return;
        }

        try {
            currentJobId = "JOB-" + UUID.randomUUID().toString().substring(0, 8);
            Path src = Paths.get(sourceFilePath.get());
            Path dest = Paths.get(targetDestinationPath.get());

            // Save last used destination path
            UserSettingsEntity currentSettings = settingsRepository.loadSettings();
            settingsRepository.saveSettings(new UserSettingsEntity(
                src.getParent() != null ? src.getParent().toString() : currentSettings.lastSourceDirectory(),
                dest.toString(),
                currentSettings.defaultTileWidth(),
                currentSettings.defaultTileHeight(),
                currentSettings.defaultCodec(),
                currentSettings.compressionLevel(),
                currentSettings.threadCount(),
                preserveVendorMetadata.get()
            ));

            ConversionRequest request = new ConversionRequest(
                currentJobId,
                src,
                dest,
                ChunkSpec.defaultSpec(),
                preserveVendorMetadata.get(),
                4,
                targetVersion.get()
            );

            converting.set(true);
            progressPercentage.set(0.0);
            statusText.set("Initializing Conversion Engine (" + targetVersion.get().getDisplayName() + ")...");
            logMessages.add("[SYSTEM] Starting conversion job: " + currentJobId + " using " + targetVersion.get().getDisplayName());

            Future<ConversionResult> future = orchestrator.submitConversion(request);
            CompletableFuture.runAsync(() -> {
                try {
                    ConversionResult result = future.get();
                    Platform.runLater(() -> {
                        if (result.status() == ConversionResult.Status.SUCCESS) {
                            if (result.gapAnalysisResult() != null) {
                                setGapAnalysisData(result.gapAnalysisResult());
                            }
                            onSuccess.run();
                        } else {
                            onError.accept(new RuntimeException(result.errorMessage() != null ? result.errorMessage() : "Conversion failed"));
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        converting.set(false);
                        statusText.set("Conversion Failed");
                        onError.accept(new RuntimeException(ex.getMessage(), ex));
                    });
                }
            });

        } catch (Exception e) {
            converting.set(false);
            statusText.set("Error: " + e.getMessage());
            onError.accept(e);
        }
    }

    public void setGapAnalysisData(GapAnalysisResult result) {
        if (result == null) return;
        this.gapAnalysisResult.set(result);
        this.hasGapAnalysis.set(true);

        int totalOriginal = result.totalOriginalCount();
        int mapped = result.mappedCount() + result.renamedCount();
        int vendorDump = result.vendorCount() + result.transitionalCount();
        int lossed = result.missingCount() + result.possibleMatchCount();

        List<GapAnalysisResult.GapAnalysisItemDetail> unknownList = (result.classificationDetails() != null) ? result.classificationDetails().get(MetadataClassification.UNKNOWN) : null;
        if (unknownList != null) {
            lossed += unknownList.size();
        }

        // 1. Direct Counts
        this.mappedCountText.set(String.valueOf(mapped));
        this.vendorDumpCountText.set(String.valueOf(vendorDump));
        this.lossedCountText.set(String.valueOf(lossed));
        this.totalCountText.set(String.valueOf(totalOriginal));

        // 2. Bar Fractions
        double div = Math.max(1.0, (double) totalOriginal);
        this.mappedFraction.set(mapped / div);
        this.vendorDumpFraction.set(vendorDump / div);
        this.lossedFraction.set(lossed / div);

        // 3. Classify details into list
        List<GapAnalysisResult.GapAnalysisItemDetail> allList = new ArrayList<>();
        List<GapAnalysisResult.GapAnalysisItemDetail> lossedList = new ArrayList<>();

        if (result.classificationDetails() != null) {
            for (var entry : result.classificationDetails().entrySet()) {
                allList.addAll(entry.getValue());
                MetadataClassification status = entry.getKey();
                if (status == MetadataClassification.MISSING || status == MetadataClassification.UNKNOWN || status == MetadataClassification.POSSIBLE_MATCH) {
                    lossedList.addAll(entry.getValue());
                }
            }
        }

        this.allFields.setAll(allList);
        this.lossedFields.setAll(lossedList);
        log.info("In-App Gap Analysis Summary: MAPPED={}, VENDOR_DUMP={}, LOSSED={}, TOTAL={}", mapped, vendorDump, lossed, totalOriginal);
    }

    public void cancelCurrentJob() {
        if (currentJobId != null) {
            orchestrator.cancelJob(currentJobId);
            converting.set(false);
            statusText.set("Conversion Cancelled");
            logMessages.add("[SYSTEM] Cancelled job: " + currentJobId);
        }
    }

    @Override
    public void onProgress(ProgressEvent event) {
        if (event.jobId().equals(currentJobId)) {
            Platform.runLater(() -> {
                progressPercentage.set(event.percentage() / 100.0);
                statusText.set(event.currentTask());

                if (event.completed()) {
                    converting.set(false);
                    statusText.set("Conversion Finished Successfully!");
                } else if (event.failed()) {
                    converting.set(false);
                    statusText.set("Conversion Failed");
                }
            });
        }
    }

    @Override
    public void onLog(LogEvent event) {
        Platform.runLater(() -> {
            String msg = String.format("[%s] [%s] %s", event.timestamp().toString().substring(11, 19), event.level(), event.message());
            logMessages.add(msg);
        });
    }

    // Properties Getters
    public StringProperty sourceFilePathProperty() { return sourceFilePath; }
    public StringProperty targetDestinationPathProperty() { return targetDestinationPath; }
    public DoubleProperty progressPercentageProperty() { return progressPercentage; }
    public StringProperty statusTextProperty() { return statusText; }
    public BooleanProperty convertingProperty() { return converting; }
    public BooleanProperty preserveVendorMetadataProperty() { return preserveVendorMetadata; }
    public ObjectProperty<org.ome.converter.core.model.OmeZarrVersion> targetVersionProperty() { return targetVersion; }

    public ObjectProperty<GapAnalysisResult> gapAnalysisResultProperty() { return gapAnalysisResult; }
    public BooleanProperty hasGapAnalysisProperty() { return hasGapAnalysis; }

    public StringProperty mappedCountTextProperty() { return mappedCountText; }
    public StringProperty vendorDumpCountTextProperty() { return vendorDumpCountText; }
    public StringProperty lossedCountTextProperty() { return lossedCountText; }
    public StringProperty totalCountTextProperty() { return totalCountText; }

    public DoubleProperty mappedFractionProperty() { return mappedFraction; }
    public DoubleProperty vendorDumpFractionProperty() { return vendorDumpFraction; }
    public DoubleProperty lossedFractionProperty() { return lossedFraction; }

    public ObservableList<String> getLogMessages() { return logMessages; }
    public ObservableList<JobEntity> getJobHistory() { return jobHistory; }
    public ObservableList<GapAnalysisResult.GapAnalysisItemDetail> getAllFields() { return allFields; }
    public ObservableList<GapAnalysisResult.GapAnalysisItemDetail> getLossedFields() { return lossedFields; }
}
