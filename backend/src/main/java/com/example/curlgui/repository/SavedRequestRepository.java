package com.example.curlgui.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.example.curlgui.model.SavedRequest;

/** Spring Data repository for {@link SavedRequest}. */
public interface SavedRequestRepository extends JpaRepository<SavedRequest, Long> {

    /** Every saved request, oldest first - used to build the sidebar tree in one query. */
    List<SavedRequest> findAllByOrderByIdAsc();

    /**
     * Delete all saved requests in a collection. Spring Data recognises the
     * {@code deleteBy...} name; it must run in a transaction (the service is
     * {@code @Transactional}).
     */
    @Transactional
    void deleteByCollectionId(Long collectionId);

    long countByCollectionId(Long collectionId);
}
