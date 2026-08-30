package org.example.searchengine.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.searchengine.utils.SplitToLemmas;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
public class ApplicationConfiguration {
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }

    @Bean("SplitterEnglish")
    public SplitToLemmas splitterEng() {
        return SplitToLemmas.getInstanceEng();
    }

    @Bean("SplitterRussian")
    public SplitToLemmas splitterRus() {
        return SplitToLemmas.getInstanceRus();
    }

    @Bean
    public HttpClient client() {
        return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL).build();
    }
}
