package searchengine.repository;

import org.hibernate.Hibernate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;
import searchengine.model.Page;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<Index,Integer> {

    @Query("SELECT COUNT(i) FROM Index i WHERE i.lemma.id = :id")
    int countPageToLemma(@Param("id") int id);

    @Query("SELECT p FROM Index i INNER JOIN i.page p WHERE i.lemma.id = :id")
    List<Page> findPagesByLemma(@Param("id") Integer id);

    @Query("SELECT i FROM Index i WHERE i.lemma.id IN :lemmaIds")
    List<Index> findByLemmaIdIn(@Param("lemmaIds") List<Integer> lemmaIds);
}
