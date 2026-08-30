package org.example.searchengine.config;

import lombok.Data;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "indexing-settings")
public class SiteListConfiguration {
    public record SiteRecord(@Getter String url, @Getter String name) {}
    private List<SiteRecord> sites;
}
