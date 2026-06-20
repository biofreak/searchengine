package org.example.searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.example.searchengine.model.Index;
import org.example.searchengine.model.Lemma;
import org.example.searchengine.model.Page;

import java.util.List;
import java.util.Set;

@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {
    List<Index> findByPageInAndLemmaIn(List<Page> pageList, List<Lemma> lemmaList);

    @Query("select idx.page " +
            "from Index idx join idx.lemma lemma where lemma.lemma in :data " +
            "group by idx.page having count(distinct lemma.lemma) = :#{#data.size()}")
    List<Page> getPagesFromIndexIn(Set<String> data);

    @Transactional
    @Modifying
    @Query(value = "INSERT INTO `index` (page_id, lemma_id, `rank`) " +
            "SELECT ind.* FROM JSON_TABLE(:data, '$[*]'" +
            " COLUMNS (page_id INT PATH '$.page_id', " +
            "lemma_id INT PATH '$.lemma_id', " +
            "`rank` FLOAT PATH '$.rank')) ind",
            nativeQuery = true)
    void insertAll(String data);
}
