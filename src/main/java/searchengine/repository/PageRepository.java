package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<Page,Integer> {

    boolean existsByPath (String path);

    @Query("SELECT count(p) FROM Page p WHERE p.site.id = :id")
    int countPagesToSite(@Param("id") Integer id);

    void deletePageByPath(String path);

    @Query("SELECT s FROM Page p INNER JOIN p.site s WHERE s.id = :id")
    Site findSiteByPage(@Param("id") Integer id);

}
