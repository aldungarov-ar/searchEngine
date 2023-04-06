package searchengine.services;

import searchengine.dto.RequestAnswer;

public interface IndexingService {
    RequestAnswer startIndexing();
    RequestAnswer stopIndexing();
}
