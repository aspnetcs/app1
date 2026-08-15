package com.webchat.platformapi.trace;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TraceSpanRepository extends JpaRepository<TraceSpanEntity, Long> {

    List<TraceSpanEntity> findByTraceIdOrderByStartAtAsc(String traceId);

    List<TraceSpanEntity> findByRequestIdOrderByStartAtDesc(String requestId, Pageable pageable);

    @Query("select distinct t.traceId from TraceSpanEntity t where t.requestId = :requestId")
    List<String> findDistinctTraceIdsByRequestId(@Param("requestId") String requestId);
}

