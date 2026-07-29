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
import java.util.UUID;

public class MainDashboardViewModel implements EventListener {
    private static final Logger log = LoggerFactory.getLogger(MainDashboardViewModel.class);

    private final StringProperty sourceFilePath = new SimpleStringProperty("");
    private final StringProperty targetDestinationPath = new SimpleStringProperty("");
    private final DoubleProperty progressPercentage = new SimpleDoubleProperty(0.0);
    private final StringProperty statusText = new SimpleStringProperty("Ready");
    private final BooleanProperty converting = new SimpleBooleanProperty(false);
    private final BooleanProperty preserveVendorMetadata = new SimpleBooleanProperty(true);
    private final ObjectProperty<org.ome.converter.core.model.OmeZarrVersion> targetVersion = new SimpleObjectProperty<>(org.ome.converter.core.model.OmeZarrVersion.OME_ZARR_0_5);

    private final ObservableList<String> logMessages = FXCollections.observableArrayList();
    private final ObservableList<JobEntity> jobHistory = FXCollections.observableArrayList();

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

            orchestrator.submitConversion(request);

        } catch (Exception e) {
            converting.set(false);
            statusText.set("Error: " + e.getMessage());
            onError.accept(e);
        }
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

    public ObservableList<String> getLogMessages() { return logMessages; }
    public ObservableList<JobEntity> getJobHistory() { return jobHistory; }
}
