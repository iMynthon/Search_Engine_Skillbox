package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "index")
@Getter
@Setter
@NoArgsConstructor
public class Index {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,generator = "index_id_seq")
    @SequenceGenerator(name = "index_id_seq",sequenceName = "index_id_seq")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id")
    private Page page;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lemma_id")
    private Lemma lemma;

    @Column(name = "rank")
    private Float rank;
}
