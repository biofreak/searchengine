package org.example.searchengine.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.example.searchengine.config.SiteList;
import org.example.searchengine.dto.indexing.IndexingResponse;
import org.example.searchengine.model.*;
import org.example.searchengine.repositories.IndexRepository;
import org.example.searchengine.repositories.LemmaRepository;
import org.example.searchengine.repositories.PageRepository;
import org.example.searchengine.repositories.SiteRepository;
import org.example.searchengine.utils.SiteWalk;
import org.example.searchengine.utils.SplitToLemmas;

import java.io.IOException;
import java.net.*;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService  {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SiteList sites;
    private final int PAGES_CHUNK = 100;

    private ForkJoinPool taskPool = new ForkJoinPool();
    private final Set<ForkJoinTask<?>> TASKS = ConcurrentHashMap.newKeySet();

    private final Optional<SplitToLemmas> splitterEng = Optional.ofNullable(SplitToLemmas.getInstanceEng());
    private final Optional<SplitToLemmas> splitterRus = Optional.ofNullable(SplitToLemmas.getInstanceRus());
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NORMAL).build();

    public IndexingResponse fullIndex() {
        IndexingResponse result = new IndexingResponse(true);
        if (!siteRepository.existsByStatusIs(IndexStatus.INDEXING)) {
            siteRepository.findAll().stream().map(Site::getId)
                    .peek(id -> siteRepository.updateStatus(id, IndexStatus.INDEXING,null))
                    .forEach(siteRepository::deleteById);
            sites.getSites().forEach(config -> TASKS.add(taskPool.submit(() ->{
                Set<String> urlBuffer = new HashSet<>() {{ add(config.getUrl().replaceAll("/$", "")); }};
                Site siteEntity = serializeSite(urlBuffer.stream().findAny().orElse(""), config.getName());
                try {
                    serializePages(siteEntity, urlBuffer);
                    walkTask(urlBuffer, urlBuffer, siteEntity).map(pageEntity ->
                            taskPool.submit(() -> serializeIndex(pageEntity))).peek(TASKS::add).forEach(task -> {
                                try {
                                    task.join();
                                    TASKS.remove(task);
                                } catch (CancellationException e) {
                                    throw new CancellationException(IndexError.INTERRUPTED.toString());
                                }
                            });
                    siteRepository.updateStatus(siteEntity.getId(), IndexStatus.INDEXED, null);
                } catch (RuntimeException e) {
                    siteRepository.updateStatus(siteEntity.getId(), IndexStatus.FAILED, e.getMessage());
                }
            })));
        } else {
            result.setResult(false);
            result.setError(IndexError.STARTED.toString());
        }
        return result;
    }

    public  IndexingResponse stopIndex() {
        IndexingResponse response = new IndexingResponse(false);

        if (TASKS.isEmpty()) {
            response.setError(IndexError.NOTSTARTED.toString());
        } else if(taskPool.isTerminating()) {
            response.setError(IndexError.TERMINATING.toString());
        } else {
            taskPool.shutdownNow();
            while(!taskPool.isTerminated()) {
                TASKS.forEach(task -> task
                        .completeExceptionally(new CancellationException(IndexError.INTERRUPTED.toString())));
            }
            TASKS.clear();
            taskPool = new ForkJoinPool();
            response.setResult(true);
        }

        return response;
    }

    private Stream<Page> walkTask(Set<String> urlBuffer, Set<String> walkSet, Site siteEntity) {
        try {
            Set<String> children = walkSet.parallelStream()
                    .map(x -> x.replace(siteEntity.getUrl(), ""))
                    .map(path -> path.isEmpty() ? "/" : path)
                    .map(path -> pageRepository.findBySiteAndPath(siteEntity, path).orElse(null))
                    .filter(Objects::nonNull)
                    .map(pageEntity -> taskPool.submit(new SiteWalk(urlBuffer, pageEntity, siteEntity.getUrl())))
                    .peek(TASKS::add).flatMap(task -> {
                        try {
                            return task.join();
                        } finally {
                            TASKS.remove(task);
                        }}).collect(Collectors.toSet());
            if (children.isEmpty()) {
                return urlBuffer.stream()
                        .map(x -> x.replace(siteEntity.getUrl(), ""))
                        .map(path -> path.isEmpty() ? "/" : path)
                        .flatMap(path -> pageRepository.findBySiteAndPath(siteEntity, path).stream());
            } else {
                serializePages(siteEntity, children);
                return walkTask(Stream.concat(urlBuffer.stream(), children.stream()).collect(Collectors.toSet()), children, siteEntity);
            }
        } catch(CancellationException e) {
            throw new CancellationException(IndexError.INTERRUPTED.toString());
        } catch (RuntimeException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    public IndexingResponse addIndex(String link) {
        IndexingResponse result = new IndexingResponse(false);
        try {
            URI url = URI.create(link);
            String path = url.getPath();
            Map<String, String> siteConfig = getConfigSite(url.getScheme() + "://" + url.getHost());
            if (!siteConfig.isEmpty()) {
                Map.Entry<String, String> configEntry = siteConfig.entrySet().iterator().next();
                Site siteEntity = serializeSite(configEntry.getKey(), configEntry.getValue());
                pageRepository.findBySiteAndPath(siteEntity, path).ifPresent(pageEntity -> {
                    decreaseFrequencies(pageEntity.getSite().getId(), pageEntity.getIndices().stream()
                                    .map(Index::getLemma).map(Lemma::getLemma).collect(Collectors.toSet()));
                    pageRepository.delete(pageEntity);
                });
                serializePages(siteEntity, Set.of(link));
                siteRepository.updateStatus(siteEntity.getId(), IndexStatus.INDEXING, null);
                pageRepository.findBySiteAndPath(siteEntity, path).ifPresent(this::serializeIndex);
                siteRepository.updateStatus(siteEntity.getId(), IndexStatus.INDEXED, null);
                result.setResult(true);
            } else {
                result.setError(IndexError.PAGE_OUT_OF_CONFIG.toString());
            }
        } catch (RuntimeException e) {
            result.setError(e.getMessage());
        }
        return result;
    }

    private Site serializeSite(String url, String name) {
            return siteRepository.findByUrl(url).orElseGet(() -> siteRepository.saveAndFlush(new Site(url, name)));
    }

    private void serializePages(Site siteEntity, Set<String> urlSet) {
        Semaphore httpSemaphore = new Semaphore(PAGES_CHUNK);
        ConcurrentLinkedQueue<String> chunkBuffer = new ConcurrentLinkedQueue<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String url : urlSet) {
                executor.submit(() -> {
                    try {
                        httpSemaphore.acquire();
                        String path = new String(url.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8)
                                .replace(siteEntity.getUrl(), "");
                        URI baseUri = URI.create(siteEntity.getUrl());
                        URI base = new URI(baseUri.getScheme(), baseUri.getAuthority(), baseUri.getPath(), null);
                        HttpResponse<String> response = getHttpResponse(base.resolve(baseUri.getPath() + path + "/"));
                        path = URLDecoder.decode(path.isEmpty() ? "/" : path, StandardCharsets.UTF_8);
                        String jsonPage = objectMapper.writeValueAsString(
                                new Page(siteEntity, path, response.statusCode(), response.body()));
                        httpSemaphore.release();
                        chunkBuffer.add(jsonPage);
                        if (chunkBuffer.size() >= PAGES_CHUNK) {
                            savePages(chunkBuffer);
                        }
                    } catch (Exception e) {
                        httpSemaphore.release();
                    }
                });
            }
        }
        savePages(chunkBuffer);
    }

    private void savePages(ConcurrentLinkedQueue<String> buffer) {
        if (buffer.isEmpty()) return;
        List<String> toInsert = new ArrayList<>(buffer);
        buffer.clear();
        if (!toInsert.isEmpty()) {
            String jsonArray = toInsert.stream().collect(Collectors.joining(",", "[", "]"));
            pageRepository.insertAll(jsonArray);
        }
    }

    private synchronized Map<Lemma, Long> serializeLemmas(Page pageEntity) {
        try {
            Site siteEntity = pageEntity.getSite();
            String text = Jsoup.parse(pageEntity.getContent()).text();
            Map<String, Long> lemmaMap = splitterEng.orElseThrow().splitTextToLemmas(text);
            lemmaMap.putAll(splitterRus.orElseThrow().splitTextToLemmas(text));
            Set<String> lemmas = lemmaRepository.findExistingLemmas(pageEntity.getSite().getId(), lemmaMap.keySet());
            List<Lemma> result = lemmaRepository.saveAll(lemmaMap.keySet().stream()
                    .filter(Predicate.not(lemmas::contains))
                    .map(x -> new Lemma(siteEntity, x))
                    .toList());
            increaseFrequencies(pageEntity.getSite().getId(), lemmaMap.keySet());
            return result.stream().map(lemmaEntity -> Map.entry(lemmaEntity, lemmaMap.get(lemmaEntity.getLemma())))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } catch (CancellationException e) {
            throw new CancellationException(IndexError.INTERRUPTED.toString());
        }
    }

    private void serializeIndex(Page pageEntity) {
        try {
            indexRepository.insertAll(Stream.of(taskPool.submit(() -> serializeLemmas(pageEntity))).peek(TASKS::add)
                    .flatMap(task -> {
                        try {
                            return task.join().entrySet().stream().map(mapEntry -> {
                                        record index(int page_id, int lemma_id, float rank) {}
                                        return new index(pageEntity.getId(), mapEntry.getKey().getId(),
                                                Float.valueOf(mapEntry.getValue()));
                                    });
                        } catch (CancellationException e) {
                            throw new CancellationException(IndexError.INTERRUPTED.toString());
                        } finally {
                            TASKS.remove(task);
                        }
                    }).map(indexRecord -> {
                        try {
                            return objectMapper.writeValueAsString(indexRecord);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e.getMessage());
                        }
                    }).collect(Collectors.joining(",", "[", "]")));
        } catch (CancellationException e) {
            throw new CancellationException(IndexError.INTERRUPTED.toString());
        } catch (RuntimeException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private HttpResponse<String> getHttpResponse(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void increaseFrequencies(long siteId, Set<String> lemmas) {
            lemmaRepository.updateFrequencies(siteId, lemmas, 1);
    }

    private void decreaseFrequencies(long siteId, Set<String> lemmas) {
        lemmaRepository.updateFrequencies(siteId, lemmas, -1);
    }

    private Map<String, String> getConfigSite(String site_regex) {
        return sites.getSites().stream()
                .filter(config -> config.getUrl().matches(site_regex))
                .collect(Collectors.toMap(SiteList.SiteRecord::getUrl, SiteList.SiteRecord::getName));
    }
}
