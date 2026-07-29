package org.ome.converter.core.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record GapAnalysisResult(
    String datasetName,
    OmeZarrVersion targetVersion,
    int totalOriginalCount,
    int totalConvertedCount,
    int mappedCount,
    int renamedCount,
    int vendorCount,
    int transitionalCount,
    int missingCount,
    int possibleMatchCount,
    double coveragePercentage,
    double preservationPercentage,
    double lossPercentage,
    Map<MetadataClassification, List<GapAnalysisItemDetail>> classificationDetails,
    Path htmlReportPath
) {
    public record GapAnalysisItemDetail(
        String originalKey,
        String originalHierarchyPath,
        String originalValue,
        String convertedKey,
        String convertedLocationPath,
        String convertedValue,
        MetadataClassification classification,
        String explanation
    ) {}
}
