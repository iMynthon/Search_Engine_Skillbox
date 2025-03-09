package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Indices;

@Repository
public interface IndicesRepository extends JpaRepository<Indices,Integer> {

}
