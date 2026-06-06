package com.suivia.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Libera tudo para desenvolvimento do seu frontend
        registry.addMapping("/**").allowedOrigins("*").allowedMethods("*");
    }
}
