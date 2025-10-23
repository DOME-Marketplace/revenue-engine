package it.eng.dome.revenue.engine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**") // Tutti gli endpoint
                        .allowedOrigins("https://dome-marketplace-sbx.org") // Dominio frontend
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Metodi ammessi
                        .allowedHeaders("*") // Tutti gli header
                        .allowCredentials(true); // Necessario se ci sono cookie o auth headers
            }
        };
    }
}
