package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lemma")
@Getter
@Setter
@NoArgsConstructor
public class Lemma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private Site site;

    @Column(name = "lemma")
    private String lemma;

    @Column(name = "frequency")
    private Integer frequency;

    @OneToMany(mappedBy = "lemma",cascade = CascadeType.ALL,fetch = FetchType.LAZY)
    private List<Indices> indicesList;

}
