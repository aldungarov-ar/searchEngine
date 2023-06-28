package searchengine.dto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Site;

@Repository
public interface SiteRepository extends JpaRepository<Site, Long> {
    @Transactional
    void deleteByUrl(String url);
}
