package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.List;

@Getter
@Setter
@Entity
public class Lemma {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    // TODO Many to many on "index" table
    private List<Site> siteId;

    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String lemma;

    @Column(nullable = false)
    private int frequency;
}
