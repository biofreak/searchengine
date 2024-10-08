package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.searching.SearchResponse;
import searchengine.dto.searching.SearchResult;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.SiteRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.IndexRepository;

import searchengine.utils.SplitToLemmas;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    @Autowired
    private final LemmaRepository lemmaRepository;

    @Autowired
    private final SiteRepository siteRepository;

    @Autowired
    private final PageRepository pageRepository;

    @Autowired
    private final IndexRepository indexRepository;

    private final Optional<SplitToLemmas> splitterRus = Optional.ofNullable(SplitToLemmas.getInstanceRus());
    private final Optional<SplitToLemmas> splitterEng = Optional.ofNullable(SplitToLemmas.getInstanceEng());

    private Map<String, List<Lemma>> lemmas;

    private final double MENTION_COEFFICIENT = 0.7;

    public SearchResponse startSearch(String query, String site, Integer offset, Integer limit) {
        SearchResponse response = new SearchResponse();
        if (query.isEmpty()) {
            response.setResult(false);
            response.setError("Задан пустой поисковый запрос");
        } else {
            response.setResult(true);
            List<Site> siteList = site == null
                    ? siteRepository.findAll() : List.of(siteRepository.findByUrl(site).orElseThrow());
            List<String> lemmaList = getLemmasFromQuery(query, siteList);
            if (Optional.ofNullable(lemmas).isEmpty() || lemmas.isEmpty() || lemmaList.isEmpty()) {
                response.setCount(0);
                response.setData(List.of());
            } else {
                List<Page> pageList = getPagesFromLemmas(lemmaList, indexRepository
                        .getPagesFromIndexIn(indexRepository.findByLemmaIn(lemmas.get(lemmaList.get(0)))));
                List<SearchResult> resultList = getResultsFromPages(pageList, offset, limit);
                response.setCount(pageList.size());
                response.setData(resultList);
            }
        }
        return response;
    }

    private List<Page> getPagesFromLemmas(List<String> lemmaList, List<Page> pageList) {
        return lemmaList.isEmpty() ? pageList : getPagesFromLemmas(lemmaList.subList(1, lemmaList.size()),
                indexRepository.getPagesFromIndexIn(indexRepository.findByPageInAndLemmaIn(pageList,
                lemmas.get(lemmaList.get(0)))));
    }

    private String getSnippetFromContent(String htmlCode, Set<String> lemmaSet) {
        return Jsoup.parse(htmlCode).getAllElements().stream().map(Element::ownText)
                .filter(Predicate.not(String::isEmpty))
                .flatMap(string -> Arrays.stream(string.split("([.!?](\\s+|\\z)|\\z)")))
                .map(string -> {
                    List<String> boldList = Arrays.stream(string.replaceAll("[^а-яА-Яa-zA-Z']", " ")
                                    .split("(\\s+|$)"))
                            .filter(Predicate.not(String::isEmpty))
                            .filter(word -> word.matches("[а-яА-Яa-zA-Z]{2,}"))
                            .filter(word -> lemmaSet.stream().anyMatch(lemma ->
                                    getNormalForms(word).anyMatch(lemma::equals))).toList();

                    String bolded = string;
                    for (String word : boldList) {
                        bolded = bolded.replaceAll(word, "<b>" + word + "</b>");
                    }
                    return Map.entry(boldList.size(), bolded);
                }).filter(entry -> entry.getKey() > 0)
                .limit(3).map(Map.Entry::getValue).collect(Collectors.joining("<br />"));
    }

    private List<String> getLemmasFromQuery(String query, List<Site> siteList) {
        Map<String, Long> lemmaMap = splitToLemmas(query);
        lemmas = lemmaMap.keySet().stream()
                .map(lemma -> Map.entry(lemma, lemmaRepository.findBySiteInAndLemma(siteList, lemma)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        int pageTotal = pageRepository.countAllBySiteIn(siteList);
        return lemmaMap.keySet().stream()
                .map(lemma -> Map.entry(lemma, lemmas.get(lemma).stream()
                        .mapToInt(x -> x == null ? 0 : x.getFrequency()).sum()))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.comparingByValue())
                .filter(entry -> {
                    return pageTotal > 0; // && (double) (entry.getValue() / pageTotal) > MENTION_COEFFICIENT;
                }).map(Map.Entry::getKey)
                .toList();
    }

    private List<SearchResult> getResultsFromPages(List<Page> pageList, int offset, int limit) {
        Map<Page, Double> rankMap = pageList.stream().map(pageEntity -> Map.entry(pageEntity,
                                        indexRepository.findByPageInAndLemmaIn(List.of(pageEntity),
                                                        lemmas.values().stream().flatMap(Collection::stream).toList())
                                                .stream().mapToDouble(Index::getRank).sum()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        double maxRank = rankMap.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getValue).orElse(0.0);

        return pageList.stream().sorted(Comparator
                        .comparingDouble(pageEntity -> maxRank > 0.0 ? rankMap.get(pageEntity) / maxRank : 0.0)
                        .reversed()).skip(offset).limit(Math.min(limit, pageList.size() - offset)).map(pageEntity -> {
                    SearchResult result = new SearchResult();
                    Site siteEntity = pageEntity.getSite();
                    String htmlCode = pageEntity.getContent();

                    result.setSite(siteEntity.getUrl());
                    result.setSiteName(siteEntity.getName());
                    result.setUri(pageEntity.getPath());
                    result.setTitle(Jsoup.parse(htmlCode).getElementsByTag("title").text());
                    result.setSnippet(getSnippetFromContent(htmlCode, lemmas.keySet()));
                    result.setRelevance(maxRank > 0.0 ? rankMap.get(pageEntity) / maxRank : 0.0);
                    return result;
                }).toList();
    }

    private Stream<String> getNormalForms(String word) {
        try {
            return splitterEng.map(x -> x.getNormalForms(word.toLowerCase()).stream()).orElseThrow();
        } catch (RuntimeException eng) {
            try {
                return splitterRus.map(x -> x.getNormalForms(word.toLowerCase()).stream()).orElseThrow();
            } catch (RuntimeException rus) {
                return Stream.of();
            }
        }
    }

    private Map<String, Long> splitToLemmas(String text) {
        try {
            return Stream.concat(splitterEng.map(x -> x.splitTextToLemmas(text).entrySet().stream()).orElseThrow(),
                            splitterRus.map(x -> x.splitTextToLemmas(text).entrySet().stream()).orElseThrow())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } catch (RuntimeException e) {
            return Map.of();
        }
    }
}
