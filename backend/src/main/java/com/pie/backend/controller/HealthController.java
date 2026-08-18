package com.pie.backend.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/")
    public ResponseEntity<?> health(
            @RequestHeader(value = "Accept", defaultValue = "application/json") String accept) {
        if (accept.contains("text/html")) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(generateHealthHtml());
        }
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "message", "Process Intelligence Ecosystem - Backend Service is running",
                "version", "0.0.1-SNAPSHOT",
                "timestamp", System.currentTimeMillis()
        ));
    }

    @GetMapping("/api/v1/health")
    public ResponseEntity<Map<String, Object>> apiHealth() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Process Intelligence Backend",
                "timestamp", System.currentTimeMillis()
        ));
    }

    private String generateHealthHtml() {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>Process Intelligence Ecosystem - Backend Status</title>\n");
        html.append("    <style>\n");
        html.append("        * { margin: 0; padding: 0; box-sizing: border-box; }\n");
        html.append("        body {\n");
        html.append("            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;\n");
        html.append("            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n");
        html.append("            min-height: 100vh;\n");
        html.append("            display: flex;\n");
        html.append("            align-items: center;\n");
        html.append("            justify-content: center;\n");
        html.append("            padding: 20px;\n");
        html.append("        }\n");
        html.append("        .container {\n");
        html.append("            background: white;\n");
        html.append("            border-radius: 12px;\n");
        html.append("            box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);\n");
        html.append("            padding: 40px;\n");
        html.append("            max-width: 600px;\n");
        html.append("            width: 100%;\n");
        html.append("        }\n");
        html.append("        .header {\n");
        html.append("            text-align: center;\n");
        html.append("            margin-bottom: 30px;\n");
        html.append("            border-bottom: 3px solid #667eea;\n");
        html.append("            padding-bottom: 20px;\n");
        html.append("        }\n");
        html.append("        .header h1 {\n");
        html.append("            color: #333;\n");
        html.append("            font-size: 28px;\n");
        html.append("            margin-bottom: 10px;\n");
        html.append("        }\n");
        html.append("        .status-badge {\n");
        html.append("            display: inline-block;\n");
        html.append("            background: #10b981;\n");
        html.append("            color: white;\n");
        html.append("            padding: 8px 16px;\n");
        html.append("            border-radius: 20px;\n");
        html.append("            font-weight: bold;\n");
        html.append("            font-size: 14px;\n");
        html.append("        }\n");
        html.append("        .info-grid {\n");
        html.append("            display: grid;\n");
        html.append("            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));\n");
        html.append("            gap: 20px;\n");
        html.append("            margin-top: 30px;\n");
        html.append("        }\n");
        html.append("        .info-card {\n");
        html.append("            background: #f8f9fa;\n");
        html.append("            padding: 20px;\n");
        html.append("            border-radius: 8px;\n");
        html.append("            border-left: 4px solid #667eea;\n");
        html.append("        }\n");
        html.append("        .info-card label {\n");
        html.append("            color: #666;\n");
        html.append("            font-size: 12px;\n");
        html.append("            text-transform: uppercase;\n");
        html.append("            font-weight: bold;\n");
        html.append("            display: block;\n");
        html.append("            margin-bottom: 8px;\n");
        html.append("        }\n");
        html.append("        .info-card .value {\n");
        html.append("            color: #333;\n");
        html.append("            font-size: 16px;\n");
        html.append("            font-weight: bold;\n");
        html.append("        }\n");
        html.append("        .endpoints {\n");
        html.append("            margin-top: 40px;\n");
        html.append("            padding-top: 30px;\n");
        html.append("            border-top: 2px solid #eee;\n");
        html.append("        }\n");
        html.append("        .endpoints h2 {\n");
        html.append("            color: #333;\n");
        html.append("            font-size: 18px;\n");
        html.append("            margin-bottom: 15px;\n");
        html.append("        }\n");
        html.append("        .endpoint-item {\n");
        html.append("            background: #f8f9fa;\n");
        html.append("            padding: 15px;\n");
        html.append("            margin-bottom: 10px;\n");
        html.append("            border-radius: 6px;\n");
        html.append("            border-left: 3px solid #667eea;\n");
        html.append("        }\n");
        html.append("        .endpoint-item code {\n");
        html.append("            background: #e9ecef;\n");
        html.append("            padding: 2px 6px;\n");
        html.append("            border-radius: 3px;\n");
        html.append("            font-family: 'Courier New', monospace;\n");
        html.append("            color: #d63031;\n");
        html.append("            font-size: 12px;\n");
        html.append("        }\n");
        html.append("        .endpoint-desc {\n");
        html.append("            color: #666;\n");
        html.append("            font-size: 13px;\n");
        html.append("            margin-top: 5px;\n");
        html.append("        }\n");
        html.append("        footer {\n");
        html.append("            margin-top: 40px;\n");
        html.append("            text-align: center;\n");
        html.append("            color: #999;\n");
        html.append("            font-size: 12px;\n");
        html.append("            border-top: 1px solid #eee;\n");
        html.append("            padding-top: 20px;\n");
        html.append("        }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"container\">\n");
        html.append("        <div class=\"header\">\n");
        html.append("            <h1>🚀 Process Intelligence Ecosystem</h1>\n");
        html.append("            <p>Backend Service Status</p>\n");
        html.append("            <div class=\"status-badge\">✓ RUNNING</div>\n");
        html.append("        </div>\n");
        html.append("        \n");
        html.append("        <div class=\"info-grid\">\n");
        html.append("            <div class=\"info-card\">\n");
        html.append("                <label>Status</label>\n");
        html.append("                <div class=\"value\">UP</div>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"info-card\">\n");
        html.append("                <label>Version</label>\n");
        html.append("                <div class=\"value\">0.0.1-SNAPSHOT</div>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"info-card\">\n");
        html.append("                <label>Timestamp</label>\n");
        html.append("                <div class=\"value\">").append(System.currentTimeMillis()).append("</div>\n");
        html.append("            </div>\n");
        html.append("        </div>\n");
        html.append("        \n");
        html.append("        <div class=\"endpoints\">\n");
        html.append("            <h2>📋 API Endpoints</h2>\n");
        html.append("            <div class=\"endpoint-item\">\n");
        html.append("                <code>GET /api/v1/health</code>\n");
        html.append("                <div class=\"endpoint-desc\">Check backend health status</div>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"endpoint-item\">\n");
        html.append("                <code>GET /h2-console/</code>\n");
        html.append("                <div class=\"endpoint-desc\">H2 Database connection information</div>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"endpoint-item\">\n");
        html.append("                <code>POST /api/v1/process/extract-text</code>\n");
        html.append("                <div class=\"endpoint-desc\">Extract process information from text</div>\n");
        html.append("            </div>\n");
        html.append("        </div>\n");
        html.append("        \n");
        html.append("        <footer>\n");
        html.append("            Process Intelligence Ecosystem © 2024 | Backend Running Successfully\n");
        html.append("        </footer>\n");
        html.append("    </div>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        return html.toString();
    }
}
