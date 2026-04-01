package com.vox.infrastructure.persistence.session;

import com.vox.domain.session.SessionSummary;

import java.util.List;

public interface SessionSummaryQueryRepository {
    List<SessionSummary> listByUserId(Long userId);
}
