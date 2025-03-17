package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma,Integer> {

    Lemma findByLemma(@Param("lemma") String lemma);

    @Query("SELECT count(l) FROM Lemma l WHERE l.site.id = :id")
    int countLemmaToSite(@Param("id") Integer id);

}
