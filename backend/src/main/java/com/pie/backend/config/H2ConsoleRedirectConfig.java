package com.pie.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * H2 Console Configuration for Spring Boot 4.1.0 (Jakarta EE)
 * 
 * H2 WebServlet has compatibility issues with Jakarta EE namespace.
 * Instead, we use a REST controller at /h2-console to provide database connection info
 * and access to H2 database metadata.
 */
@Configuration
public class H2ConsoleRedirectConfig implements WebMvcConfigurer {
    // H2 console is now served via H2ConsoleController
}
