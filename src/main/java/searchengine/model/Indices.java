package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "indices")
@Getter
@Setter
@NoArgsConstructor
public class Indices {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne
    @JoinColumn(name = "page_id")
    private Page page;

    @OneToOne
    @JoinColumn(name = "lemma_id")
    private Lemma lemma;

    @Column(name = "rank")
    private Float rank;
}
