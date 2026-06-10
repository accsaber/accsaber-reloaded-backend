package com.accsaber.backend.config;

import java.nio.file.Paths;
import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.accsaber.backend.model.entity.map.Difficulty;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final CdnProperties cdnProperties;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new NullsLastPageableResolver());
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new DifficultySlugConverter());
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(cdnProperties.getStoragePath()).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/cdn/**")
                .addResourceLocations(location)
                .setCachePeriod(31536000);
    }

    static final class DifficultySlugConverter implements Converter<String, Difficulty> {
        @Override
        public Difficulty convert(String source) {
            String normalized = source.trim().toUpperCase().replace('-', '_');
            return Difficulty.valueOf(normalized);
        }
    }
}
