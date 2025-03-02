package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "site")
@Getter @Setter
@NoArgsConstructor
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "status")
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private Status status;

    @CreationTimestamp
    @Column(name = "status_time")
    private LocalDate statusTime;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "url")
    private String url;

    @Column(name = "name")
    private String name;

    @OneToMany(mappedBy = "site",cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Page> page;

}
