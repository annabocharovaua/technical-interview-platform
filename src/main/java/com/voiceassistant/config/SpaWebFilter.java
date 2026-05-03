package com.voiceassistant.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
@Configuration
public class SpaWebFilter implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/app.html");
        registry.addViewController("/{path:(?!api|ws|connect)[^\\.]*}").setViewName("forward:/app.html");
        registry.addViewController("/{path1:(?!api|ws)[^\\.]*}/{path2:[^\\.]*}").setViewName("forward:/app.html");
        registry.addViewController("/{path1:(?!api|ws)[^\\.]*}/{path2:[^\\.]*}/{path3:[^\\.]*}").setViewName("forward:/app.html");
    }
    @Value("${spring.profiles.active:prod}")
    private String profile;
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        int cachePeriod = "dev".equals(profile) ? 0 : 3600;
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(cachePeriod);
    }
}
