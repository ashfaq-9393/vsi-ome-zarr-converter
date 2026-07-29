package org.ome.converter.service.analysis;

import org.ome.converter.core.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

public class MetadataGapAnalyzerService {
    private static final Logger log = LoggerFactory.getLogger(MetadataGapAnalyzerService.class);

    private final OriginalMetadataCollector collector = new OriginalMetadataCollector();
    private final ConvertedMetadataInspector inspector = new ConvertedMetadataInspector();
    private final MetadataComparisonEngine comparisonEngine = new MetadataComparisonEngine();
    private final GapAnalysisReportGenerator reportGenerator = new GapAnalysisReportGenerator();

    public GapAnalysisResult analyzeAndReport(
        String datasetName,
        OmeZarrVersion version,
        ImageMetadata standardMeta,
        VendorMetadata vendorMeta,
        Path zarrRoot
    ) {
        log.info("Starting Metadata Gap Analysis for dataset: {} ({})", datasetName, version.getDisplayName());

        // 1. Collect Original Metadata
        List<OriginalMetadataItem> originalItems = collector.collectOriginalMetadata(standardMeta, vendorMeta);

        // 2. Inspect Converted Dataset (0.4 / 0.5)
        List<ConvertedMetadataItem> convertedItems = inspector.inspectConvertedDataset(zarrRoot, version);

        // 3. Temporary path for report file target
        Path reportPath = zarrRoot.getParent() != null ? zarrRoot.getParent() : zarrRoot;

        // 4. Run Semantic Comparison Engine
        GapAnalysisResult resultWithoutPath = comparisonEngine.compare(datasetName, version, originalItems, convertedItems, reportPath);

        // 5. Generate Standalone HTML Dashboard Report
        Path finalHtmlReportPath = reportGenerator.generateHtmlReport(resultWithoutPath, reportPath);

        // Re-construct result with exact final HTML report path
        GapAnalysisResult finalResult = new GapAnalysisResult(
            resultWithoutPath.datasetName(),
            resultWithoutPath.targetVersion(),
            resultWithoutPath.totalOriginalCount(),
            resultWithoutPath.totalConvertedCount(),
            resultWithoutPath.mappedCount(),
            resultWithoutPath.renamedCount(),
            resultWithoutPath.vendorCount(),
            resultWithoutPath.transitionalCount(),
            resultWithoutPath.missingCount(),
            resultWithoutPath.possibleMatchCount(),
            resultWithoutPath.coveragePercentage(),
            resultWithoutPath.preservationPercentage(),
            resultWithoutPath.lossPercentage(),
            resultWithoutPath.classificationDetails(),
            finalHtmlReportPath
        );

        log.info("Completed Metadata Gap Analysis for {}. Preservation: {}%, Loss: {}%. Report saved at {}",
            datasetName, finalResult.preservationPercentage(), finalResult.lossPercentage(), finalHtmlReportPath.toAbsolutePath());

        return finalResult;
    }
}
