package org.ome.converter.service.analysis;

import org.ome.converter.core.model.GapAnalysisResult;
import org.ome.converter.core.model.MetadataClassification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class GapAnalysisReportGenerator {
    private static final Logger log = LoggerFactory.getLogger(GapAnalysisReportGenerator.class);

    public Path generateHtmlReport(GapAnalysisResult result, Path outputDirectory) {
        String safeName = result.datasetName().replaceAll("[^a-zA-Z0-9._-]", "_");
        Path reportPath = outputDirectory.resolve(safeName + "_gap_report.html");
        File reportFile = reportPath.toFile();

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(reportFile))) {
            bw.write(buildHtmlContent(result));
            log.info("Successfully generated standalone HTML Metadata Gap Analysis Report: {}", reportPath.toAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to generate HTML Gap Analysis Report at {}: {}", reportPath, e.getMessage(), e);
        }

        return reportPath;
    }

    private String buildHtmlContent(GapAnalysisResult r) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n")
          .append("<html lang=\"en\">\n")
          .append("<head>\n")
          .append("  <meta charset=\"UTF-8\">\n")
          .append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
          .append("  <title>Metadata Gap Analysis Report - ").append(escape(r.datasetName())).append("</title>\n")
          .append("  <style>\n")
          .append("    :root { --bg: #0f172a; --card-bg: #1e293b; --text: #f8fafc; --text-sub: #94a3b8; --border: #334155; --primary: #38bdf8; --success: #22c55e; --warning: #eab308; --danger: #ef4444; --accent: #a855f7; }\n")
          .append("    * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Segoe UI', system-ui, -apple-system, sans-serif; }\n")
          .append("    body { background-color: var(--bg); color: var(--text); padding: 2rem; line-height: 1.5; }\n")
          .append("    .container { max-width: 1300px; margin: 0 auto; }\n")
          .append("    .header { background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px; padding: 1.5rem 2rem; margin-bottom: 1.5rem; display: flex; justify-content: space-between; align-items: center; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.1); }\n")
          .append("    .header h1 { font-size: 1.5rem; color: var(--primary); font-weight: 700; }\n")
          .append("    .header p { color: var(--text-sub); font-size: 0.875rem; margin-top: 0.25rem; }\n")
          .append("    .badge { background: #0284c7; color: white; padding: 0.35rem 0.75rem; border-radius: 9999px; font-size: 0.8rem; font-weight: 600; text-transform: uppercase; }\n")
          .append("    .grid-kpi { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; }\n")
          .append("    .kpi-card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 10px; padding: 1.25rem; text-align: center; }\n")
          .append("    .kpi-title { font-size: 0.8rem; text-transform: uppercase; color: var(--text-sub); font-weight: 600; letter-spacing: 0.05em; }\n")
          .append("    .kpi-value { font-size: 2rem; font-weight: 800; margin: 0.4rem 0; color: var(--text); }\n")
          .append("    .kpi-sub { font-size: 0.75rem; color: var(--text-sub); }\n")
          .append("    .progress-bar-bg { background: #334155; height: 8px; border-radius: 4px; overflow: hidden; margin-top: 0.5rem; }\n")
          .append("    .progress-bar-fill { height: 100%; border-radius: 4px; transition: width 0.3s ease; }\n")
          .append("    .section { background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px; padding: 1.5rem; margin-bottom: 1.5rem; }\n")
          .append("    .section-title { font-size: 1.15rem; font-weight: 600; margin-bottom: 1rem; color: var(--text); border-bottom: 1px solid var(--border); padding-bottom: 0.5rem; }\n")
          .append("    .controls { display: flex; gap: 0.5rem; margin-bottom: 1rem; flex-wrap: wrap; }\n")
          .append("    .search-input { background: #0f172a; border: 1px solid var(--border); color: white; padding: 0.5rem 1rem; border-radius: 6px; width: 300px; font-size: 0.875rem; }\n")
          .append("    .tab-btn { background: #0f172a; border: 1px solid var(--border); color: var(--text-sub); padding: 0.5rem 1rem; border-radius: 6px; cursor: pointer; font-size: 0.85rem; font-weight: 600; }\n")
          .append("    .tab-btn.active { background: var(--primary); color: #0f172a; border-color: var(--primary); }\n")
          .append("    table { width: 100%; border-collapse: collapse; text-align: left; font-size: 0.85rem; }\n")
          .append("    th { background: #0f172a; color: var(--text-sub); padding: 0.75rem 1rem; border-bottom: 1px solid var(--border); font-weight: 600; text-transform: uppercase; font-size: 0.75rem; }\n")
          .append("    td { padding: 0.75rem 1rem; border-bottom: 1px solid var(--border); vertical-align: top; word-break: break-word; }\n")
          .append("    tr:hover { background: rgba(255,255,255,0.02); }\n")
          .append("    .tag { display: inline-block; padding: 0.2rem 0.5rem; border-radius: 4px; font-size: 0.75rem; font-weight: 600; }\n")
          .append("    .tag-mapped { background: rgba(34, 197, 94, 0.2); color: #4ade80; border: 1px solid #22c55e; }\n")
          .append("    .tag-renamed { background: rgba(56, 189, 248, 0.2); color: #38bdf8; border: 1px solid #0284c7; }\n")
          .append("    .tag-vendor { background: rgba(168, 85, 247, 0.2); color: #c084fc; border: 1px solid #a855f7; }\n")
          .append("    .tag-transitional { background: rgba(234, 179, 8, 0.2); color: #fde047; border: 1px solid #eab308; }\n")
          .append("    .tag-missing { background: rgba(239, 68, 68, 0.2); color: #fca5a5; border: 1px solid #ef4444; }\n")
          .append("    .footer { text-align: center; color: var(--text-sub); font-size: 0.8rem; margin-top: 2rem; }\n")
          .append("  </style>\n")
          .append("</head>\n")
          .append("<body>\n")
          .append("  <div class=\"container\">\n")
          .append("    <div class=\"header\">\n")
          .append("      <div>\n")
          .append("        <h1>Metadata Gap Analysis Dashboard</h1>\n")
          .append("        <p>Dataset: <strong>").append(escape(r.datasetName())).append("</strong> | Spec: <strong>").append(r.targetVersion().getDisplayName()).append("</strong> | Generated: ").append(timestamp).append("</p>\n")
          .append("      </div>\n")
          .append("      <span class=\"badge\">").append(r.targetVersion().getDisplayName()).append("</span>\n")
          .append("    </div>\n")

          // KPI Cards
          .append("    <div class=\"grid-kpi\">\n")

          .append("      <div class=\"kpi-card\">\n")
          .append("        <div class=\"kpi-title\">Preservation Rate</div>\n")
          .append("        <div class=\"kpi-value\" style=\"color: #4ade80;\">").append(r.preservationPercentage()).append("%</div>\n")
          .append("        <div class=\"kpi-sub\">Original fields preserved</div>\n")
          .append("        <div class=\"progress-bar-bg\"><div class=\"progress-bar-fill\" style=\"width: ").append(r.preservationPercentage()).append("%; background: #22c55e;\"></div></div>\n")
          .append("      </div>\n")

          .append("      <div class=\"kpi-card\">\n")
          .append("        <div class=\"kpi-title\">NGFF Coverage</div>\n")
          .append("        <div class=\"kpi-value\" style=\"color: #38bdf8;\">").append(r.coveragePercentage()).append("%</div>\n")
          .append("        <div class=\"kpi-sub\">Standard OME-NGFF mapped</div>\n")
          .append("        <div class=\"progress-bar-bg\"><div class=\"progress-bar-fill\" style=\"width: ").append(r.coveragePercentage()).append("%; background: #38bdf8;\"></div></div>\n")
          .append("      </div>\n")

          .append("      <div class=\"kpi-card\">\n")
          .append("        <div class=\"kpi-title\">Loss Rate</div>\n")
          .append("        <div class=\"kpi-value\" style=\"color: #fca5a5;\">").append(r.lossPercentage()).append("%</div>\n")
          .append("        <div class=\"kpi-sub\">Unconverted / dropped</div>\n")
          .append("        <div class=\"progress-bar-bg\"><div class=\"progress-bar-fill\" style=\"width: ").append(r.lossPercentage()).append("%; background: #ef4444;\"></div></div>\n")
          .append("      </div>\n")

          .append("      <div class=\"kpi-card\">\n")
          .append("        <div class=\"kpi-title\">Field Counts</div>\n")
          .append("        <div class=\"kpi-value\">").append(r.totalOriginalCount()).append("</div>\n")
          .append("        <div class=\"kpi-sub\">Original: ").append(r.totalOriginalCount()).append(" | Converted: ").append(r.totalConvertedCount()).append("</div>\n")
          .append("      </div>\n")

          .append("    </div>\n")

          // Breakdown Section
          .append("    <div class=\"section\">\n")
          .append("      <div class=\"section-title\">Metadata Classification Breakdown</div>\n")
          .append("      <div style=\"display: flex; gap: 1.5rem; flex-wrap: wrap;\">\n")
          .append("        <div><span class=\"tag tag-mapped\">Mapped: ").append(r.mappedCount()).append("</span></div>\n")
          .append("        <div><span class=\"tag tag-renamed\">Renamed: ").append(r.renamedCount()).append("</span></div>\n")
          .append("        <div><span class=\"tag tag-vendor\">Vendor Custom: ").append(r.vendorCount()).append("</span></div>\n")
          .append("        <div><span class=\"tag tag-transitional\">Transitional OME-XML: ").append(r.transitionalCount()).append("</span></div>\n")
          .append("        <div><span class=\"tag tag-missing\">Missing: ").append(r.missingCount()).append("</span></div>\n")
          .append("      </div>\n")
          .append("    </div>\n")

          // Table Section
          .append("    <div class=\"section\">\n")
          .append("      <div class=\"section-title\">Detailed Field Comparison Inventory</div>\n")
          .append("      <div class=\"controls\">\n")
          .append("        <input type=\"text\" id=\"searchInput\" class=\"search-input\" placeholder=\"Search key, value, or path...\" onkeyup=\"filterTable()\">\n")
          .append("      </div>\n")
          .append("      <table id=\"reportTable\">\n")
          .append("        <thead>\n")
          .append("          <tr>\n")
          .append("            <th>Status</th>\n")
          .append("            <th>Original Key</th>\n")
          .append("            <th>Original Hierarchy Path</th>\n")
          .append("            <th>Original Value</th>\n")
          .append("            <th>Converted Location / Target Key</th>\n")
          .append("            <th>Converted Value</th>\n")
          .append("            <th>Explanation</th>\n")
          .append("          </tr>\n")
          .append("        </thead>\n")
          .append("        <tbody>\n");

        for (var entry : r.classificationDetails().entrySet()) {
            MetadataClassification classification = entry.getKey();
            String tagClass = switch (classification) {
                case MAPPED -> "tag-mapped";
                case RENAMED -> "tag-renamed";
                case VENDOR_METADATA -> "tag-vendor";
                case TRANSITIONAL_METADATA -> "tag-transitional";
                default -> "tag-missing";
            };

            for (var item : entry.getValue()) {
                sb.append("          <tr>\n")
                  .append("            <td><span class=\"tag ").append(tagClass).append("\">").append(classification.name()).append("</span></td>\n")
                  .append("            <td><strong>").append(escape(item.originalKey())).append("</strong></td>\n")
                  .append("            <td style=\"color: var(--text-sub); font-family: monospace;\">").append(escape(item.originalHierarchyPath())).append("</td>\n")
                  .append("            <td>").append(escape(item.originalValue())).append("</td>\n")
                  .append("            <td style=\"font-family: monospace;\">").append(escape(item.convertedLocationPath())).append("</td>\n")
                  .append("            <td>").append(escape(item.convertedValue())).append("</td>\n")
                  .append("            <td style=\"color: var(--text-sub);\">").append(escape(item.explanation())).append("</td>\n")
                  .append("          </tr>\n");
            }
        }

        sb.append("        </tbody>\n")
          .append("      </table>\n")
          .append("    </div>\n")

          .append("    <div class=\"footer\">\n")
          .append("      <p>OME Converter Engine v1.0.0 | Standalone Gap Analysis Report</p>\n")
          .append("    </div>\n")
          .append("  </div>\n")

          .append("  <script>\n")
          .append("    function filterTable() {\n")
          .append("      let input = document.getElementById('searchInput').value.toLowerCase();\n")
          .append("      let rows = document.querySelectorAll('#reportTable tbody tr');\n")
          .append("      rows.forEach(row => {\n")
          .append("        let text = row.innerText.toLowerCase();\n")
          .append("        row.style.display = text.includes(input) ? '' : 'none';\n")
          .append("      });\n")
          .append("    }\n")
          .append("  </script>\n")
          .append("</body>\n")
          .append("</html>\n");

        return sb.toString();
    }

    private String escape(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#39;");
    }
}
