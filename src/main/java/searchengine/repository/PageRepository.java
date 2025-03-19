package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;

@Repository
public interface PageRepository extends JpaRepository<Page,Integer> {

    boolean existsByPath (String path);

    @Query("SELECT count(p) FROM Page p WHERE p.site.id = :id")
    int countPagesToSite(@Param("id") Integer id);

    void deletePageByPath(String path);
}
