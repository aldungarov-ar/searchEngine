package searchengine.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageSnippet {
    private String uri;
    private String title;
    private String snippet;
    private double relevance;

    public PageSnippet(String uri, String title, String snippet, double relevance) {
        this.uri = uri;
        this.title = title;
        this.snippet = snippet;
        this.relevance = relevance;
    }
}
