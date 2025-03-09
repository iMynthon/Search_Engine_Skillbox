package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;

import java.util.Optional;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma,Integer> {

    @Query("SELECT l FROM Lemma l WHERE l.lemma = :lemma AND l.site.id = :id")
    Optional<Lemma> findByLemmaAndSite(String lemma, Integer id);
}
