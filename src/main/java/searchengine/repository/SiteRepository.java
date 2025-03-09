package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Site;

import java.util.Optional;

@Repository
public interface SiteRepository extends JpaRepository<Site, Integer> {

    @Query("select u from Site u where u.url = :url")
    Site findByUrl(String url);

    boolean existsByUrl (String url);
}
