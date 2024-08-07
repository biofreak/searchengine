package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;
import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {
    Optional<Page> findBySiteAndPath(Site site, String path);

    Integer countAllBySite(Site site);
    Integer countAllBySiteIn(List<Site> siteList);

    @Transactional
    @Modifying
    @Query(value = "INSERT INTO `page` (site_id, `path`, `code`, content) " +
            "SELECT pg.* FROM JSON_TABLE(:data, " +
            "'$[*]' COLUMNS (site_id INT PATH '$.site.id', `path` TEXT PATH '$.path', `code` INT PATH '$.code', " +
            "content MEDIUMTEXT PATH '$.content')) pg", nativeQuery = true)
    void insertAll(@Param("data") String data);
}
