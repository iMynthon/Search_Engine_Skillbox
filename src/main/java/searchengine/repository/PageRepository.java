package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;

import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<Page,Integer> {

    boolean existsByPath (String path);

    @Query("Select count(*) from Page p where p.site.id = :id")
    int countPagesToSite(@Param("id") int id);

    void deletePageByPath(String path);

}
