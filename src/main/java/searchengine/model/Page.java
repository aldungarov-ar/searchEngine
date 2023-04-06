package searchengine.model;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import javax.persistence.Index;

@Getter
@Setter
@Entity
@Table(indexes = {
        @Index(name = "path_index",
                columnList = "path",
                unique = true)
})
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Site siteId;

    @Column(columnDefinition = "VARCHAR(255)", nullable = false, unique = true)
    private String path;

    @Column(nullable = false)
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String content;
}
