package com.harding.feeds.repository;

import com.harding.feeds.entity.Baby;
import com.harding.feeds.entity.Feed;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeedRepository extends CrudRepository<Feed, UUID> {

    /**
     * Feeds for a baby, newest first, with optional startTime window
     * (from inclusive, to exclusive) and optional updatedSince incremental
     * filter. In-progress feeds (endTime null) match like any other row.
     */
    // The null-checks cast the bind to a type: on PostgreSQL an untyped parameter used only
    // in `:p is null` cannot have its data type inferred (SQLState 42P18), unlike H2. Without
    // the cast every feed read 500s against Postgres while passing on the H2 test database.
    @Query("""
            from Feed f
            where f.baby = :baby
              and (cast(:from as timestamp) is null or f.startTime >= :from)
              and (cast(:to as timestamp) is null or f.startTime < :to)
              and (cast(:updatedSince as timestamp) is null or f.updatedAt > :updatedSince)
            order by f.startTime desc
            """)
    List<Feed> findForBaby(@Param("baby") Baby baby,
                           @Param("from") OffsetDateTime from,
                           @Param("to") OffsetDateTime to,
                           @Param("updatedSince") OffsetDateTime updatedSince);

    /** The in-progress feed, if any (nothing enforces at most one; newest wins). */
    Optional<Feed> findFirstByBabyAndEndTimeIsNullOrderByStartTimeDesc(Baby baby);

    /** The newest feed of any type, in progress or finished. */
    Optional<Feed> findFirstByBabyOrderByStartTimeDesc(Baby baby);

    /** The newest feed of a type that has a side recorded (drives next-side alternation). */
    Optional<Feed> findFirstByBabyAndTypeAndSideIsNotNullOrderByStartTimeDesc(Baby baby, Feed.Type type);
}
