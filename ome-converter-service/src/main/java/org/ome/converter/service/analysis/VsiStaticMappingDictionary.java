package org.ome.converter.service.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ome.converter.core.model.MetadataClassification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;

public class VsiStaticMappingDictionary {
    private static final Logger log = LoggerFactory.getLogger(VsiStaticMappingDictionary.class);
    private static final Map<String, StaticMappingRule> STATIC_MAPPINGS = new LinkedHashMap<>();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record StaticMappingRule(
        String rawKey,
        String omeTarget,
        String category,
        MetadataClassification classification,
        String explanation
    ) {}

    static {
        loadStaticDictionary();
    }

    private static void loadStaticDictionary() {
        try (InputStream is = VsiStaticMappingDictionary.class.getClassLoader().getResourceAsStream("vsi_to_ome_mapping.json")) {
            if (is == null) {
                log.warn("vsi_to_ome_mapping.json not found on classpath, initializing default static rules.");
                initializeFallbackDefaults();
                return;
            }
            JsonNode rootNode = MAPPER.readTree(is);
            JsonNode mappingsNode = rootNode.path("mappings");
            if (mappingsNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = mappingsNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    JsonNode node = entry.getValue();
                    String rawKey = node.path("raw_key").asText(entry.getKey());
                    String omeTarget = node.path("ome_target").asText("-");
                    String category = node.path("category").asText("GENERAL");
                    String statusStr = node.path("status").asText("UNKNOWN");
                    String explanation = node.path("explanation").asText("Static rule mapping");

                    MetadataClassification classification;
                    try {
                        classification = MetadataClassification.valueOf(statusStr);
                    } catch (IllegalArgumentException e) {
                        classification = MetadataClassification.UNKNOWN;
                    }

                    STATIC_MAPPINGS.put(rawKey.toLowerCase(), new StaticMappingRule(rawKey, omeTarget, category, classification, explanation));
                }
            }
            log.info("Loaded {} static VSI-to-OME metadata mapping rules.", STATIC_MAPPINGS.size());
        } catch (Exception e) {
            log.error("Failed to load vsi_to_ome_mapping.json static dictionary: {}", e.getMessage(), e);
            initializeFallbackDefaults();
        }
    }

    private static void initializeFallbackDefaults() {
        STATIC_MAPPINGS.put("ome.image.sizex", new StaticMappingRule("OME.Image.SizeX", "multiscales[0].datasets[*].shape", "SPATIAL", MetadataClassification.MAPPED, "Directly mapped to 5D Zarr array shape (dim X)."));
        STATIC_MAPPINGS.put("ome.image.sizey", new StaticMappingRule("OME.Image.SizeY", "multiscales[0].datasets[*].shape", "SPATIAL", MetadataClassification.MAPPED, "Directly mapped to 5D Zarr array shape (dim Y)."));
        STATIC_MAPPINGS.put("ome.image.sizez", new StaticMappingRule("OME.Image.SizeZ", "multiscales[0].datasets[*].shape", "SPATIAL", MetadataClassification.MAPPED, "Directly mapped to 5D Zarr array shape (dim Z)."));
        STATIC_MAPPINGS.put("ome.image.sizec", new StaticMappingRule("OME.Image.SizeC", "omero.channels length / shape", "CHANNEL", MetadataClassification.MAPPED, "Mapped to OMERO channels array length."));
        STATIC_MAPPINGS.put("ome.image.sizet", new StaticMappingRule("OME.Image.SizeT", "multiscales[0].datasets[*].shape", "TIME", MetadataClassification.MAPPED, "Directly mapped to 5D Zarr array shape (dim T)."));
        STATIC_MAPPINGS.put("ome.image.physicalsizex", new StaticMappingRule("OME.Image.PhysicalSizeX", "multiscales[0].datasets[0].scale[X]", "SPATIAL", MetadataClassification.RENAMED, "Mapped via scientific dictionary: PhysicalSizeX ➔ scale factor."));
        STATIC_MAPPINGS.put("ome.image.physicalsizey", new StaticMappingRule("OME.Image.PhysicalSizeY", "multiscales[0].datasets[0].scale[Y]", "SPATIAL", MetadataClassification.RENAMED, "Mapped via scientific dictionary: PhysicalSizeY ➔ scale factor."));
        STATIC_MAPPINGS.put("ome.image.physicalsizez", new StaticMappingRule("OME.Image.PhysicalSizeZ", "multiscales[0].datasets[0].scale[Z]", "SPATIAL", MetadataClassification.RENAMED, "Mapped via scientific dictionary: PhysicalSizeZ ➔ scale factor."));
    }

    public static StaticMappingRule lookup(String key, String hierarchyPath, String category) {
        if (key == null) return createUnknownRule("-");
        String keyLower = key.toLowerCase();

        // 1. Direct exact key match
        if (STATIC_MAPPINGS.containsKey(keyLower)) {
            return STATIC_MAPPINGS.get(keyLower);
        }

        // 2. Category / Pattern matches
        if ("VENDOR_GLOBAL".equalsIgnoreCase(category) || keyLower.startsWith("global.")) {
            return new StaticMappingRule(key, "attributes.vendor_custom_metadata.vsi_global_tags." + key, "VENDOR_GLOBAL", MetadataClassification.VENDOR_METADATA, "Statically mapped and preserved verbatim in vendor_custom_metadata global tags.");
        }
        if ("VENDOR_SERIES".equalsIgnoreCase(category) || hierarchyPath != null && hierarchyPath.toLowerCase().contains("series")) {
            return new StaticMappingRule(key, "attributes.vendor_custom_metadata.vsi_series_tags." + key, "VENDOR_SERIES", MetadataClassification.VENDOR_METADATA, "Statically mapped and preserved verbatim in vendor_custom_metadata series tags.");
        }
        if ("TRANSITIONAL_XML".equalsIgnoreCase(category) || "ome.header.xml".equalsIgnoreCase(keyLower)) {
            return new StaticMappingRule(key, "OME/METADATA.ome.xml", "TRANSITIONAL_XML", MetadataClassification.TRANSITIONAL_METADATA, "Statically mapped to companion OME-XML metadata node.");
        }
        if (keyLower.contains("channel") && keyLower.contains("name")) {
            return new StaticMappingRule(key, "omero.channels[*].label", "CHANNEL", MetadataClassification.MAPPED, "Directly mapped to OMERO channel label attribute.");
        }
        if (keyLower.contains("channel") && (keyLower.contains("color") || keyLower.contains("hex"))) {
            return new StaticMappingRule(key, "omero.channels[*].color", "CHANNEL", MetadataClassification.MAPPED, "Directly mapped to OMERO channel color attribute.");
        }

        // 3. Semantic concept lookup
        Optional<String> canonicalConcept = SemanticMetadataDictionary.findCanonicalConcept(key);
        if (canonicalConcept.isPresent()) {
            String concept = canonicalConcept.get();
            return new StaticMappingRule(key, "multiscales / omero." + concept, "SEMANTIC", MetadataClassification.RENAMED, "Renamed and mapped statically via scientific semantic dictionary (" + key + " ➔ " + concept + ").");
        }

        // 4. Hardware candidates
        if (isPossibleMatchCandidate(keyLower)) {
            return new StaticMappingRule(key, "N/A (hardware attribute)", "HARDWARE", MetadataClassification.POSSIBLE_MATCH, "Potential acquisition attribute requiring expert ontology review.");
        }

        return createUnknownRule(key);
    }

    private static boolean isPossibleMatchCandidate(String rawKeyLower) {
        return rawKeyLower.contains("camera") || rawKeyLower.contains("lens") || rawKeyLower.contains("filter")
            || rawKeyLower.contains("laser") || rawKeyLower.contains("power") || rawKeyLower.contains("channel");
    }

    private static StaticMappingRule createUnknownRule(String key) {
        return new StaticMappingRule(key, "-", "UNKNOWN", MetadataClassification.UNKNOWN, "Unrecognized raw vendor tag without standard OME translation in static dictionary.");
    }

    public static Map<String, StaticMappingRule> getStaticMappings() {
        return Collections.unmodifiableMap(STATIC_MAPPINGS);
    }
}
