package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;

import java.sql.PreparedStatement;
import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<Index,Integer> {

}
