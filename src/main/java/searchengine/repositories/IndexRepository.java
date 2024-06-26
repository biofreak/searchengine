package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {
    List<Index> findByPage(Page page);
    List<Index> findByLemma(Lemma lemma);
    Index findByPageAndLemma(Page page, Lemma lemma);

    @Transactional
    @Modifying
    @Query(value = "INSERT INTO `index` (page_id, lemma_id, `rank`) " +
            "SELECT ind.* FROM JSON_TABLE(:data, '$[*]'" +
            " COLUMNS (page_id INT PATH '$.page.id', lemma_id INT PATH '$.lemma.id', `rank` FLOAT PATH '$.rank')) ind",
            nativeQuery = true)
    void insertAll(@Param("data") String data);
}
