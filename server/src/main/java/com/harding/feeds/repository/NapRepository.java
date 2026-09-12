package com.harding.feeds.repository;

import com.harding.feeds.entity.Baby;
import com.harding.feeds.entity.Nap;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NapRepository extends CrudRepository<Nap, UUID> {

    /**
     * Naps for a baby, newest first, with optional startTime window
     * (from inclusive, to exclusive) and optional updatedSince incremental
     * filter. In-progress naps (endTime null) match like any other row.
     *
     * <p>{@code from} filters on startTime, never on overlap. That keeps this
     * predicate the exact complement of the client's local delete predicate
     * (NapDao.deleteSyncedNotIn), so a window refetch can never delete a live
     * row. Change one side and you must change the other.
     */
    // The null-checks cast the bind to a type: on PostgreSQL an untyped parameter used only
    // in `:p is null` cannot have its data type inferred (SQLState 42P18), unlike H2. Without
    // the cast every nap read 500s against Postgres while passing on the H2 test database.
    @Query("""
            from Nap n
            where n.baby = :baby
              and (cast(:from as timestamp) is null or n.startTime >= :from)
              and (cast(:to as timestamp) is null or n.startTime < :to)
              and (cast(:updatedSince as timestamp) is null or n.updatedAt > :updatedSince)
            order by n.startTime desc
            """)
    List<Nap> findForBaby(@Param("baby") Baby baby,
                          @Param("from") OffsetDateTime from,
                          @Param("to") OffsetDateTime to,
                          @Param("updatedSince") OffsetDateTime updatedSince);

    /** The in-progress nap, if any (nothing enforces at most one; newest wins). */
    Optional<Nap> findFirstByBabyAndEndTimeIsNullOrderByStartTimeDesc(Baby baby);
}
