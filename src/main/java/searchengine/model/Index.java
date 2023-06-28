package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity(name = "`index`")
public class Index {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @ManyToOne
    @JoinColumn(name = "page_id", nullable = false)
    private Page pageId;

    @ManyToOne
    @JoinColumn(name = "lemma_id")
    private Lemma lemmaId;

    @Column(name = "`rank`", nullable = false)
    private float rank;
}
