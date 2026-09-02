package com.example.curlgui.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.curlgui.model.RequestCollection;

/** Spring Data repository for {@link RequestCollection}. */
public interface CollectionRepository extends JpaRepository<RequestCollection, Long> {

    /** All collections in creation order (so the default "My Requests" stays first). */
    List<RequestCollection> findAllByOrderByIdAsc();

    /** Used to keep the default collection unique and to reject duplicate names. */
    Optional<RequestCollection> findByNameIgnoreCase(String name);
}
