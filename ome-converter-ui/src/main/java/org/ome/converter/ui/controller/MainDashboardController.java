package org.ome.converter.ui.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import org.ome.converter.core.model.GapAnalysisResult;
import org.ome.converter.core.model.GapAnalysisResult.GapAnalysisItemDetail;
import org.ome.converter.core.model.MetadataClassification;
import org.ome.converter.ui.util.AlertHelper;
import org.ome.converter.ui.viewmodel.MainDashboardViewModel;

import java.awt.Desktop;
import java.io.File;

public class MainDashboardController {

    @FXML private TextField txtSourceFile;
    @FXML private TextField txtTargetDestination;
    @FXML private CheckBox chkVendorMetadata;
    @FXML private ComboBox<org.ome.converter.core.model.OmeZarrVersion> cmbTargetVersion;

    @FXML private ProgressBar progressBar;
    @FXML private Label lblStatus;
    @FXML private Button btnConvert;
    @FXML private Button btnCancel;

    @FXML private ListView<String> lstLogs;

    // In-App Dashboard 4 KPI Count Cards (MAPPED, VENDOR DUMP, LOSSED, FIELD COUNTS)
    @FXML private Label lblMappedCount;
    @FXML private Label lblVendorDumpCount;
    @FXML private Label lblLossedCount;
    @FXML private Label lblTotalFieldCount;
    @FXML private Label lblFieldCountsSub;

    @FXML private ProgressBar progressMapped;
    @FXML private ProgressBar progressVendorDump;
    @FXML private ProgressBar progressLossed;

    // Lossed Fields TableView & Search
    @FXML private TextField txtSearchLostFields;
    @FXML private TableView<GapAnalysisItemDetail> tblLostFields;
    @FXML private TableColumn<GapAnalysisItemDetail, String> colLostStatus;
    @FXML private TableColumn<GapAnalysisItemDetail, String> colLostKey;
    @FXML private TableColumn<GapAnalysisItemDetail, String> colLostPath;
    @FXML private TableColumn<GapAnalysisItemDetail, String> colLostValue;
    @FXML private TableColumn<GapAnalysisItemDetail, String> colLostExplanation;

    private MainDashboardViewModel viewModel;
    private FilteredList<GapAnalysisItemDetail> filteredFieldList;

    @FXML
    public void initialize() {
        viewModel = new MainDashboardViewModel();

        txtSourceFile.textProperty().bindBidirectional(viewModel.sourceFilePathProperty());
        txtTargetDestination.textProperty().bindBidirectional(viewModel.targetDestinationPathProperty());
        chkVendorMetadata.selectedProperty().bindBidirectional(viewModel.preserveVendorMetadataProperty());

        cmbTargetVersion.getItems().addAll(org.ome.converter.core.model.OmeZarrVersion.values());
        cmbTargetVersion.valueProperty().bindBidirectional(viewModel.targetVersionProperty());

        progressBar.progressProperty().bind(viewModel.progressPercentageProperty());
        lblStatus.textProperty().bind(viewModel.statusTextProperty());

        btnConvert.disableProperty().bind(viewModel.convertingProperty());
        btnCancel.disableProperty().bind(viewModel.convertingProperty().not());

        lstLogs.setItems(viewModel.getLogMessages());

        // In-App Dashboard KPI Count Bindings
        lblMappedCount.textProperty().bind(viewModel.mappedCountTextProperty());
        lblVendorDumpCount.textProperty().bind(viewModel.vendorDumpCountTextProperty());
        lblLossedCount.textProperty().bind(viewModel.lossedCountTextProperty());
        lblTotalFieldCount.textProperty().bind(viewModel.totalCountTextProperty());

        progressMapped.progressProperty().bind(viewModel.mappedFractionProperty());
        progressVendorDump.progressProperty().bind(viewModel.vendorDumpFractionProperty());
        progressLossed.progressProperty().bind(viewModel.lossedFractionProperty());

        // TableView Columns bound to LOSSED fields
        colLostStatus.setCellValueFactory(cellData -> new SimpleStringProperty("LOSSED"));
        colLostKey.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().originalKey()));
        colLostPath.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().originalHierarchyPath()));
        colLostValue.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().originalValue()));
        colLostExplanation.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().explanation()));

        filteredFieldList = new FilteredList<>(viewModel.getLossedFields(), p -> true);
        tblLostFields.setItems(filteredFieldList);

        if (txtSearchLostFields != null) {
            txtSearchLostFields.textProperty().addListener((obs, oldVal, newVal) -> updateTablePredicate());
        }
        updateTablePredicate();
    }

    private void updateTablePredicate() {
        if (filteredFieldList == null) return;

        String searchText = txtSearchLostFields != null && txtSearchLostFields.getText() != null
            ? txtSearchLostFields.getText().toLowerCase().trim()
            : "";

        filteredFieldList.setPredicate(item -> {
            if (searchText.isEmpty()) {
                return true;
            }
            return (item.originalKey() != null && item.originalKey().toLowerCase().contains(searchText))
                || (item.originalHierarchyPath() != null && item.originalHierarchyPath().toLowerCase().contains(searchText))
                || (item.originalValue() != null && item.originalValue().toLowerCase().contains(searchText))
                || (item.explanation() != null && item.explanation().toLowerCase().contains(searchText));
        });
    }

    @FXML
    private void handleBrowseSourceFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Microscopic Image Slide File");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Olympus CellSens VSI (*.vsi)", "*.vsi"),
            new FileChooser.ExtensionFilter("All Microscopic Slides", "*.vsi", "*.oir", "*.czi", "*.nd2")
        );

        if (!txtSourceFile.getText().isBlank()) {
            File existing = new File(txtSourceFile.getText());
            if (existing.getParentFile() != null && existing.getParentFile().exists()) {
                fileChooser.setInitialDirectory(existing.getParentFile());
            }
        }

        Stage stage = (Stage) txtSourceFile.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile != null) {
            viewModel.sourceFilePathProperty().set(selectedFile.getAbsolutePath());
        }
    }

    @FXML
    private void handleBrowseTargetDestination() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select Destination Directory for OME-Zarr Output");

        if (!txtTargetDestination.getText().isBlank()) {
            File existing = new File(txtTargetDestination.getText());
            if (existing.exists() && existing.isDirectory()) {
                dirChooser.setInitialDirectory(existing);
            }
        }

        Stage stage = (Stage) txtTargetDestination.getScene().getWindow();
        File selectedDir = dirChooser.showDialog(stage);
        if (selectedDir != null) {
            viewModel.targetDestinationPathProperty().set(selectedDir.getAbsolutePath());
        }
    }

    @FXML
    private void handleStartConversion() {
        viewModel.startConversion(
            () -> {
                String targetPath = txtTargetDestination.getText();
                AlertHelper.showCompletionSuccess("CONVERTED", targetPath);
                updateTablePredicate();
            },
            (ex) -> {
                if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("disk space")) {
                    AlertHelper.showStorageError("Disk Space Warning", ex.getMessage());
                } else if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("source file")) {
                    AlertHelper.showInputValidationError("Source File Error", ex.getMessage());
                } else {
                    AlertHelper.showConversionError("Conversion Process Error", ex.getMessage(), ex.getCause());
                }
            }
        );
    }

    @FXML
    private void handleCancelConversion() {
        viewModel.cancelCurrentJob();
    }

    @FXML
    private void handleOpenOutputFolder() {
        String targetDirStr = txtTargetDestination.getText();
        if (targetDirStr != null && !targetDirStr.isBlank()) {
            try {
                File dir = new File(targetDirStr);
                if (dir.exists()) {
                    Desktop.getDesktop().open(dir.isDirectory() ? dir : dir.getParentFile());
                } else {
                    AlertHelper.showStorageError("Directory Not Found", "The specified target directory does not exist yet.");
                }
            } catch (Exception e) {
                AlertHelper.showStorageError("Error Opening Directory", e.getMessage());
            }
        } else {
            AlertHelper.showStorageError("No Directory Selected", "Please select a target directory first.");
        }
    }

    @FXML
    private void handleClearLogs() {
        viewModel.getLogMessages().clear();
    }
}
