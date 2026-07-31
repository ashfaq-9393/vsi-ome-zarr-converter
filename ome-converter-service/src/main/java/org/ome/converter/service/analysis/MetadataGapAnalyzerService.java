package org.ome.converter.service.analysis;

import org.ome.converter.core.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class MetadataGapAnalyzerService {
    private static final Logger log = LoggerFactory.getLogger(MetadataGapAnalyzerService.class);

    private final OriginalMetadataCollector collector = new OriginalMetadataCollector();
    private final MetadataComparisonEngine comparisonEngine = new MetadataComparisonEngine();

    public GapAnalysisResult analyzeAndReport(
        String datasetName,
        OmeZarrVersion version,
        ImageMetadata standardMeta,
        VendorMetadata vendorMeta,
        Path zarrRoot
    ) {
        log.info("Starting Static Metadata Gap Analysis for dataset: {} ({})", datasetName, version.getDisplayName());

        // 1. Collect Original Metadata
        List<OriginalMetadataItem> originalItems = collector.collectOriginalMetadata(standardMeta, vendorMeta);

        // 2. Report target path
        Path reportPath = (zarrRoot != null && zarrRoot.getParent() != null) ? zarrRoot.getParent() : (zarrRoot != null ? zarrRoot : Path.of("."));

        // 3. Run Static Comparison Engine using pre-defined VSI-to-OME dictionary schema (No HTML file written to disk)
        GapAnalysisResult finalResult = comparisonEngine.compare(datasetName, version, originalItems, Collections.emptyList(), reportPath);

        log.info("Completed Static Metadata Gap Analysis for {}. Mapped: {}, Vendor Dump: {}, Lost: {}",
            datasetName, finalResult.mappedCount() + finalResult.renamedCount(), finalResult.vendorCount() + finalResult.transitionalCount(), finalResult.missingCount());

        return finalResult;
    }
}
