package org.ome.converter.service.analysis;

import org.ome.converter.core.model.*;

import java.nio.file.Path;
import java.util.*;

public class MetadataComparisonEngine {

    public GapAnalysisResult compare(
        String datasetName,
        OmeZarrVersion version,
        List<OriginalMetadataItem> originalItems,
        List<ConvertedMetadataItem> convertedItems,
        Path htmlReportPath
    ) {
        Map<MetadataClassification, List<GapAnalysisResult.GapAnalysisItemDetail>> classificationMap = new LinkedHashMap<>();
        for (MetadataClassification classification : MetadataClassification.values()) {
            classificationMap.put(classification, new ArrayList<>());
        }

        int mapped = 0;
        int renamed = 0;
        int vendorPreserved = 0;
        int transitional = 0;
        int missing = 0;
        int possibleMatch = 0;
        int unknown = 0;

        for (OriginalMetadataItem orig : originalItems) {
            // Static dictionary lookup based on VsiStaticMappingDictionary schema
            VsiStaticMappingDictionary.StaticMappingRule staticRule = VsiStaticMappingDictionary.lookup(
                orig.key(),
                orig.hierarchyPath(),
                orig.category()
            );

            MetadataClassification classification = staticRule.classification();
            String convKey = orig.key();
            String convLoc = staticRule.omeTarget();
            String convVal = (classification != MetadataClassification.UNKNOWN && classification != MetadataClassification.MISSING)
                ? orig.value()
                : "-";
            String explanation = staticRule.explanation();

            switch (classification) {
                case MAPPED -> mapped++;
                case RENAMED -> renamed++;
                case VENDOR_METADATA -> vendorPreserved++;
                case TRANSITIONAL_METADATA -> transitional++;
                case MISSING -> missing++;
                case POSSIBLE_MATCH -> possibleMatch++;
                case UNKNOWN -> unknown++;
            }

            GapAnalysisResult.GapAnalysisItemDetail detail = new GapAnalysisResult.GapAnalysisItemDetail(
                orig.key(),
                orig.hierarchyPath(),
                orig.value(),
                convKey,
                convLoc,
                convVal,
                classification,
                explanation
            );

            classificationMap.get(classification).add(detail);
        }

        int totalOriginal = Math.max(1, originalItems.size());
        int totalConverted = mapped + renamed + vendorPreserved + transitional;
        int preservedTotal = mapped + renamed + vendorPreserved + transitional;

        double coveragePct = roundPct((double) (mapped + renamed) / totalOriginal * 100.0);
        double preservationPct = roundPct((double) preservedTotal / totalOriginal * 100.0);
        double lossPct = roundPct((double) (missing + unknown) / totalOriginal * 100.0);

        return new GapAnalysisResult(
            datasetName,
            version != null ? version : OmeZarrVersion.OME_ZARR_0_5,
            originalItems.size(),
            totalConverted,
            mapped,
            renamed,
            vendorPreserved,
            transitional,
            missing,
            possibleMatch,
            coveragePct,
            preservationPct,
            lossPct,
            classificationMap,
            htmlReportPath
        );
    }

    private double roundPct(double val) {
        return Math.round(val * 10.0) / 10.0;
    }
}
