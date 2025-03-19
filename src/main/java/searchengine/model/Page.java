package searchengine.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "page")
@Getter
@Setter
@NoArgsConstructor
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private Site site;

    @Column(name = "path")
    private String path;

    @Column(name = "code")
    private Integer code;

    @Column(name = "content")
    private String content;

    @OneToMany(mappedBy = "page",cascade = CascadeType.REMOVE,fetch = FetchType.LAZY)
    private List<Index> indexList;

    public Page(String path) {
        this.path = path;
    }
}
