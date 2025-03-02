package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "page")
@Getter
@Setter
@NoArgsConstructor
@ToString
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

    public Page(String path) {
        this.path = path;
    }
}
