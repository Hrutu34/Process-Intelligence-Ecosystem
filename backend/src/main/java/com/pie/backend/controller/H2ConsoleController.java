package com.pie.backend.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * H2 Console Controller for Spring Boot 4.x with Jakarta EE
 * 
 * Since H2 console servlet has compatibility issues with Jakarta EE,
 * this controller provides access to H2 database information and an alternative
 * way to interact with the H2 database.
 */
@RestController
@RequestMapping("/h2-console")
public class H2ConsoleController {

    /**
     * H2 console info page - returns HTML for browser, JSON for API clients
     */
    @GetMapping({"", "/"})
    public ResponseEntity<?> h2ConsoleInfo(
            @RequestHeader(value = "Accept", defaultValue = "application/json") String accept) {
        if (accept.contains("text/html")) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(generateH2ConsoleHtml());
        }
        return ResponseEntity.ok(Map.of(
                "status", "H2 Console Access",
                "message", "H2 In-Memory Database is running",
                "database", Map.of(
                        "url", "jdbc:h2:mem:pie_db",
                        "driver", "org.h2.Driver",
                        "username", "sa",
                        "password", "",
                        "note", "For local development only"
                ),
                "alternatives", Map.of(
                        "option1", "Use an H2 console client application",
                        "option2", "Connect via IDE (IntelliJ, VS Code with SQL tools)",
                        "option3", "Use the REST API endpoints provided by this backend"
                ),
                "api_endpoints", Map.of(
                        "documents", "GET /api/v1/documents - List all documents",
                        "processes", "POST /api/v1/process/extract-text - Extract process information",
                        "health", "GET /api/v1/health - Check backend health"
                )
        ));
    }

    /**
     * Provide database connection details for external tools
     */
    @GetMapping("/connection")
    public ResponseEntity<Map<String, Object>> connectionDetails() {
        return ResponseEntity.ok(Map.of(
                "jdbc_url", "jdbc:h2:mem:pie_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "username", "sa",
                "password", "",
                "driver_class", "org.h2.Driver",
                "instructions", "Use these credentials to connect from any H2 client tool or IDE"
        ));
    }

    private String generateH2ConsoleHtml() {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>H2 Database Console - Process Intelligence Ecosystem</title>\n");
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
        html.append("            max-width: 800px;\n");
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
        html.append("        .content {\n");
        html.append("            margin-top: 30px;\n");
        html.append("        }\n");
        html.append("        .section {\n");
        html.append("            margin-bottom: 30px;\n");
        html.append("        }\n");
        html.append("        .section h2 {\n");
        html.append("            color: #333;\n");
        html.append("            font-size: 18px;\n");
        html.append("            margin-bottom: 15px;\n");
        html.append("        }\n");
        html.append("        .database-info {\n");
        html.append("            background: #f8f9fa;\n");
        html.append("            padding: 20px;\n");
        html.append("            border-radius: 8px;\n");
        html.append("            border-left: 4px solid #667eea;\n");
        html.append("        }\n");
        html.append("        .info-row {\n");
        html.append("            display: grid;\n");
        html.append("            grid-template-columns: 150px 1fr;\n");
        html.append("            gap: 15px;\n");
        html.append("            padding: 12px 0;\n");
        html.append("            border-bottom: 1px solid #e0e0e0;\n");
        html.append("        }\n");
        html.append("        .info-row:last-child {\n");
        html.append("            border-bottom: none;\n");
        html.append("        }\n");
        html.append("        .info-label {\n");
        html.append("            color: #666;\n");
        html.append("            font-weight: bold;\n");
        html.append("            font-size: 13px;\n");
        html.append("            text-transform: uppercase;\n");
        html.append("        }\n");
        html.append("        .info-value {\n");
        html.append("            background: #fff;\n");
        html.append("            padding: 8px 12px;\n");
        html.append("            border-radius: 4px;\n");
        html.append("            border: 1px solid #e0e0e0;\n");
        html.append("            font-family: 'Courier New', monospace;\n");
        html.append("            color: #333;\n");
        html.append("            font-size: 14px;\n");
        html.append("            word-break: break-all;\n");
        html.append("        }\n");
        html.append("        .alternatives {\n");
        html.append("            display: grid;\n");
        html.append("            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));\n");
        html.append("            gap: 15px;\n");
        html.append("            margin-top: 20px;\n");
        html.append("        }\n");
        html.append("        .alt-card {\n");
        html.append("            background: #f8f9fa;\n");
        html.append("            padding: 20px;\n");
        html.append("            border-radius: 8px;\n");
        html.append("            border-left: 4px solid #667eea;\n");
        html.append("        }\n");
        html.append("        .alt-card h3 {\n");
        html.append("            color: #333;\n");
        html.append("            font-size: 14px;\n");
        html.append("            margin-bottom: 8px;\n");
        html.append("        }\n");
        html.append("        .alt-card p {\n");
        html.append("            color: #666;\n");
        html.append("            font-size: 13px;\n");
        html.append("            line-height: 1.5;\n");
        html.append("        }\n");
        html.append("        .note {\n");
        html.append("            background: #fff3cd;\n");
        html.append("            border-left: 4px solid #ffc107;\n");
        html.append("            padding: 15px;\n");
        html.append("            border-radius: 4px;\n");
        html.append("            margin-top: 15px;\n");
        html.append("            font-size: 13px;\n");
        html.append("            color: #856404;\n");
        html.append("        }\n");
        html.append("        .endpoints {\n");
        html.append("            display: grid;\n");
        html.append("            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));\n");
        html.append("            gap: 15px;\n");
        html.append("            margin-top: 20px;\n");
        html.append("        }\n");
        html.append("        .endpoint-card {\n");
        html.append("            background: #f8f9fa;\n");
        html.append("            padding: 15px;\n");
        html.append("            border-radius: 8px;\n");
        html.append("            border-left: 3px solid #667eea;\n");
        html.append("        }\n");
        html.append("        .endpoint-card code {\n");
        html.append("            background: #e9ecef;\n");
        html.append("            padding: 4px 8px;\n");
        html.append("            border-radius: 3px;\n");
        html.append("            font-family: 'Courier New', monospace;\n");
        html.append("            color: #d63031;\n");
        html.append("            font-size: 12px;\n");
        html.append("            display: block;\n");
        html.append("            margin-bottom: 5px;\n");
        html.append("        }\n");
        html.append("        .endpoint-desc {\n");
        html.append("            color: #666;\n");
        html.append("            font-size: 12px;\n");
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
        html.append("            <h1>🗄️ H2 Database Console</h1>\n");
        html.append("            <p>Process Intelligence Ecosystem</p>\n");
        html.append("            <div class=\"status-badge\">✓ RUNNING</div>\n");
        html.append("        </div>\n");
        html.append("        \n");
        html.append("        <div class=\"content\">\n");
        html.append("            <div class=\"section\">\n");
        html.append("                <h2>📋 Database Connection</h2>\n");
        html.append("                <div class=\"database-info\">\n");
        html.append("                    <div class=\"info-row\">\n");
        html.append("                        <div class=\"info-label\">JDBC URL</div>\n");
        html.append("                        <div class=\"info-value\">jdbc:h2:mem:pie_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE</div>\n");
        html.append("                    </div>\n");
        html.append("                    <div class=\"info-row\">\n");
        html.append("                        <div class=\"info-label\">Driver</div>\n");
        html.append("                        <div class=\"info-value\">org.h2.Driver</div>\n");
        html.append("                    </div>\n");
        html.append("                    <div class=\"info-row\">\n");
        html.append("                        <div class=\"info-label\">Username</div>\n");
        html.append("                        <div class=\"info-value\">sa</div>\n");
        html.append("                    </div>\n");
        html.append("                    <div class=\"info-row\">\n");
        html.append("                        <div class=\"info-label\">Password</div>\n");
        html.append("                        <div class=\"info-value\">(empty)</div>\n");
        html.append("                    </div>\n");
        html.append("                    <div class=\"note\">\n");
        html.append("                        ℹ️ This is an in-memory database for local development only. Data is lost when the backend restarts.\n");
        html.append("                    </div>\n");
        html.append("                </div>\n");
        html.append("            </div>\n");
        html.append("            \n");
        html.append("            <div class=\"section\">\n");
        html.append("                <h2>🔌 Access Methods</h2>\n");
        html.append("                <div class=\"alternatives\">\n");
        html.append("                    <div class=\"alt-card\">\n");
        html.append("                        <h3>💻 IDE Integration</h3>\n");
        html.append("                        <p>Use IntelliJ IDEA, VS Code, or DataGrip with the JDBC connection details above.</p>\n");
        html.append("                    </div>\n");
        html.append("                    <div class=\"alt-card\">\n");
        html.append("                        <h3>🛠️ H2 Console App</h3>\n");
        html.append("                        <p>Download and run the standalone H2 console client to connect remotely.</p>\n");
        html.append("                    </div>\n");
        html.append("                    <div class=\"alt-card\">\n");
        html.append("                        <h3>🔗 REST API</h3>\n");
        html.append("                        <p>Use the backend REST API endpoints below to query and manage data.</p>\n");
        html.append("                    </div>\n");
        html.append("                </div>\n");
        html.append("            </div>\n");
        html.append("            \n");
        html.append("            <div class=\"section\">\n");
        html.append("                <h2>📡 API Endpoints</h2>\n");
        html.append("                <div class=\"endpoints\">\n");
        html.append("                    <div class=\"endpoint-card\">\n");
        html.append("                        <code>GET /api/v1/health</code>\n");
        html.append("                        <div class=\"endpoint-desc\">Backend health status</div>\n");
        html.append("                    </div>\n");
        html.append("                    <div class=\"endpoint-card\">\n");
        html.append("                        <code>GET /api/v1/documents</code>\n");
        html.append("                        <div class=\"endpoint-desc\">List all documents</div>\n");
        html.append("                    </div>\n");
        html.append("                    <div class=\"endpoint-card\">\n");
        html.append("                        <code>POST /api/v1/process/extract-text</code>\n");
        html.append("                        <div class=\"endpoint-desc\">Extract process information</div>\n");
        html.append("                    </div>\n");
        html.append("                </div>\n");
        html.append("            </div>\n");
        html.append("        </div>\n");
        html.append("        \n");
        html.append("        <footer>\n");
        html.append("            H2 Database Console | Process Intelligence Ecosystem © 2024\n");
        html.append("        </footer>\n");
        html.append("    </div>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        return html.toString();
    }
}
