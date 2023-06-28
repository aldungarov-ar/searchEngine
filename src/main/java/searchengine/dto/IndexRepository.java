package searchengine.dto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<Index, Long> {

    @Query(nativeQuery = true,
            value = "select * from `index` where `index`.lemma_id=?1")
    List<Index> findByLemmaId(Lemma lemma);

    @Query(nativeQuery = true,
            value = "select * from `index` where `index`.page_id=?1")
    List<Index> findByPageId(int pageId);

    @Query(nativeQuery = true,
            value = "select page_id from `index` where `index`.lemma_id=?1")
    List<Integer> findPageIdLemmaId(int lemmaId);

    // TODO check if this method is really needed
    @Query(nativeQuery = true,
            value = "SELECT page_id FROM (" +
                    "select page_id, count(lemma_id) as `count` " +
                    "from `index` " +
                    "where lemma_id IN (:lemmasIds) " +
                    "GROUP BY page_id) i " +
                    "where count = :count;")
    List<Page> findPagesContainsLemmas(List<Integer> lemmasIds, int count);

    @Query(nativeQuery = true,
            value = "SELECT * FROM search_engine.index i where i.lemma_id = :lemmaId AND i.page_id = :pageId;")
    Index findIndexWithLemmaAndPage(int lemmaId, int pageId);
}
