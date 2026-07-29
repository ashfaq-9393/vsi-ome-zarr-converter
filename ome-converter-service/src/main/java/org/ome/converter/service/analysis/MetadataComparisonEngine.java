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

        // Build lookup maps for converted items by location and key
        Map<String, ConvertedMetadataItem> convertedByKey = new LinkedHashMap<>();
        Map<String, ConvertedMetadataItem> convertedByLocation = new LinkedHashMap<>();
        for (ConvertedMetadataItem conv : convertedItems) {
            convertedByKey.put(conv.key().toLowerCase(), conv);
            convertedByLocation.put(conv.locationPath().toLowerCase(), conv);
        }

        int mapped = 0;
        int renamed = 0;
        int vendorPreserved = 0;
        int transitional = 0;
        int missing = 0;
        int possibleMatch = 0;
        int unknown = 0;

        for (OriginalMetadataItem orig : originalItems) {
            String rawKey = orig.key();
            String rawKeyLower = rawKey.toLowerCase();
            Optional<String> canonicalConcept = SemanticMetadataDictionary.findCanonicalConcept(rawKey);

            MetadataClassification classification;
            String convKey = "-";
            String convLoc = "-";
            String convVal = "-";
            String explanation;

            // 1. Check if preserved in vendor custom attributes
            if (convertedByKey.containsKey(rawKeyLower) && "VENDOR_CUSTOM".equalsIgnoreCase(convertedByKey.get(rawKeyLower).namespace())) {
                ConvertedMetadataItem match = convertedByKey.get(rawKeyLower);
                classification = MetadataClassification.VENDOR_METADATA;
                vendorPreserved++;
                convKey = match.key();
                convLoc = match.locationPath();
                convVal = match.value();
                explanation = "Preserved verbatim in vendor_custom_metadata attribute block.";

            } else if ("TRANSITIONAL_XML".equalsIgnoreCase(orig.category())) {
                classification = MetadataClassification.TRANSITIONAL_METADATA;
                transitional++;
                convKey = "METADATA.ome.xml";
                convLoc = "OME/METADATA.ome.xml";
                convVal = orig.value();
                explanation = "Preserved in bioformats2raw companion OME-XML node.";

            } else if (convertedByKey.containsKey(rawKeyLower)) {
                ConvertedMetadataItem match = convertedByKey.get(rawKeyLower);
                classification = MetadataClassification.MAPPED;
                mapped++;
                convKey = match.key();
                convLoc = match.locationPath();
                convVal = match.value();
                explanation = "Directly converted to standard OME-NGFF attribute.";

            } else if (canonicalConcept.isPresent()) {
                String concept = canonicalConcept.get();
                // Check if concept is present in converted dataset
                ConvertedMetadataItem conceptMatch = findMatchForConcept(concept, convertedItems);
                if (conceptMatch != null) {
                    classification = MetadataClassification.RENAMED;
                    renamed++;
                    convKey = conceptMatch.key();
                    convLoc = conceptMatch.locationPath();
                    convVal = conceptMatch.value();
                    explanation = "Renamed and mapped via scientific semantic thesaurus (" + rawKey + " ➔ " + concept + ").";
                } else {
                    classification = MetadataClassification.MISSING;
                    missing++;
                    explanation = "Mapped in scientific dictionary to '" + concept + "', but absent in output dataset.";
                }
            } else if (isPossibleMatchCandidate(rawKeyLower)) {
                classification = MetadataClassification.POSSIBLE_MATCH;
                possibleMatch++;
                explanation = "Potential hardware/acquisition attribute requiring expert ontology review.";
            } else {
                classification = MetadataClassification.UNKNOWN;
                unknown++;
                explanation = "Unrecognized raw vendor tag without standard OME translation.";
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
        int totalConverted = convertedItems.size();
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

    private ConvertedMetadataItem findMatchForConcept(String concept, List<ConvertedMetadataItem> convertedItems) {
        for (ConvertedMetadataItem conv : convertedItems) {
            if (conv.key().equalsIgnoreCase(concept) || conv.locationPath().toLowerCase().contains(concept.toLowerCase())) {
                return conv;
            }
            if ("pixel_size_x".equalsIgnoreCase(concept) && conv.key().equalsIgnoreCase("scale")) {
                return conv;
            }
        }
        return null;
    }

    private boolean isPossibleMatchCandidate(String rawKeyLower) {
        return rawKeyLower.contains("camera") || rawKeyLower.contains("lens") || rawKeyLower.contains("filter")
            || rawKeyLower.contains("laser") || rawKeyLower.contains("power") || rawKeyLower.contains("channel");
    }

    private double roundPct(double val) {
        return Math.round(val * 10.0) / 10.0;
    }
}
