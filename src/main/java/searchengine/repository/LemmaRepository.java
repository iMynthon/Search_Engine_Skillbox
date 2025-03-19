package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.List;
import java.util.Optional;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma,Integer> {

    @Query("SELECT l FROM Lemma l WHERE l.lemma = :lemma")
    List<Lemma> findByLemma(@Param("lemma") String lemma);

    @Query("SELECT l FROM Lemma l WHERE l.lemma = :lemma and l.site = :site")
    Lemma findByLemmaToSiteId(@Param("lemma") String lemma, @Param("site")Site site);

    @Query("SELECT count(l) FROM Lemma l WHERE l.site.id = :id")
    int countLemmaToSite(@Param("id") Integer id);

}
