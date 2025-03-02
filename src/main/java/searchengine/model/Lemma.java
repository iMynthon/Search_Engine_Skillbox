package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lemma")
@Getter
@Setter
@NoArgsConstructor
public class Lemma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne
    @JoinColumn(name = "site_id")
    private Site site;

    @Column(name = "lemma")
    private String lemma;

    @Column(name = "frequency")
    private Integer frequency;
}
