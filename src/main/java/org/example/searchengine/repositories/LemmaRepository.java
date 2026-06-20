package org.example.searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.example.searchengine.model.Lemma;
import org.example.searchengine.model.Site;

import java.util.List;
import java.util.Set;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    List<Lemma> findBySiteInAndLemma(List<Site> siteList, String lemma);

    Integer countAllBySite(Site site);

    @Query("select lma.lemma from Lemma lma where lma.site.id = :id and lma.lemma in :data")
    Set<String> findExistingLemmas(int id, Set<String> data);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT IGNORE INTO lemma (site_id, lemma)
        SELECT lm.*
        FROM JSON_TABLE(:data, '$[*]'
            COLUMNS(site_id INT PATH '$.site.id', lemma VARCHAR(50) PATH '$.lemma')
        ) AS lm
        """, nativeQuery = true)
    void saveBatch(String data);

    @Transactional
    @Modifying
    @Query("UPDATE Lemma lemma SET lemma.frequency = lemma.frequency + :delta WHERE lemma.site.id = :id and lemma.lemma IN :data")
    void updateFrequencies(long id, Set<String> data, int delta);
}
