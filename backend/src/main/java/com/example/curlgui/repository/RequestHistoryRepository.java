package com.example.curlgui.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.curlgui.model.RequestHistory;

/**
 * Spring Data JPA repository for {@link RequestHistory}.
 *
 * <p>Extending {@code JpaRepository} gives {@code save}, {@code findById},
 * {@code existsById}, {@code deleteById}, {@code deleteAll}, {@code count}, etc.
 * for free - Spring generates the implementation at runtime. We add:
 * <ul>
 *   <li>{@link #findAllByOrderByIdDesc()} - derived query, newest first
 *       (id is monotonic with insertion order)</li>
 *   <li>{@link #deleteAllButNewest(int)} - trims the table to a maximum size</li>
 * </ul>
 */
public interface RequestHistoryRepository extends JpaRepository<RequestHistory, Long> {

    /** All rows, newest first. */
    List<RequestHistory> findAllByOrderByIdDesc();

    /**
     * Delete every row except the {@code keep} newest. One statement; SQLite
     * supports {@code LIMIT} inside the sub-select. {@code @Modifying} marks this
     * as a write query; it must run inside a transaction.
     *
     * @return number of rows deleted
     */
    @Modifying
    @Query(value = "DELETE FROM request_history WHERE id NOT IN "
            + "(SELECT id FROM request_history ORDER BY id DESC LIMIT :keep)",
            nativeQuery = true)
    int deleteAllButNewest(@Param("keep") int keep);
}
