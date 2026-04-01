package com.vox.application.session;

import com.vox.domain.session.SessionSummary;
import com.vox.infrastructure.persistence.session.SessionSummaryQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListSessionsUseCase {

    private final SessionSummaryQueryRepository sessionSummaryQueryRepository;

    @Transactional(readOnly = true)
    public List<SessionSummary> execute(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        return sessionSummaryQueryRepository.listByUserId(userId);
    }
}
