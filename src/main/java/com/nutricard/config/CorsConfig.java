package com.nutricard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${cors.allowed.origins:}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = new ArrayList<>();
        origins.add("https://nutricard-frontend-three.vercel.app");
        origins.add("https://nutricard-frontend-kwes.vercel.app");
        origins.add("http://localhost:5173");
        origins.add("http://localhost:5174");

        if (allowedOrigins != null && !allowedOrigins.isBlank()) {
            origins.addAll(Arrays.asList(allowedOrigins.split(",")));
        }

        registry.addMapping("/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
