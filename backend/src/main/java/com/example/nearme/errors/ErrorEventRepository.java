package com.example.nearme.errors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ErrorEventRepository extends JpaRepository<ErrorEventRecord, String> {

    List<ErrorEventRecord> findTop100ByOrderByOccurredAtDesc();

    /** Count by path since a time — "which endpoints fail most?" */
    @Query(value = """
            SELECT path AS path, count(*) AS count
            FROM error_event
            WHERE occurred_at > :since
            GROUP BY path
            ORDER BY count DESC
            """, nativeQuery = true)
    List<PathCount> countByPathSince(@Param("since") Instant since);

    /** Count by exception type since a time — "what's breaking?" */
    @Query(value = """
            SELECT exception AS exception, count(*) AS count
            FROM error_event
            WHERE occurred_at > :since
            GROUP BY exception
            ORDER BY count DESC
            """, nativeQuery = true)
    List<ExceptionCount> countByExceptionSince(@Param("since") Instant since);

    interface PathCount { String getPath(); long getCount(); }
    interface ExceptionCount { String getException(); long getCount(); }
}
