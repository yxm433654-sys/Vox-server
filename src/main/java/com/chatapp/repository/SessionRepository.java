package com.chatapp.repository;

import com.chatapp.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {

    Optional<Session> findByUser1IdAndUser2Id(Long user1Id, Long user2Id);

    @Query("""
            select s from Session s
            where s.user1Id = :userId or s.user2Id = :userId
            order by coalesce(s.lastMessageTime, s.updateTime, s.createTime) desc, s.id desc
            """)
    List<Session> findByUserIdOrderByRecent(@Param("userId") Long userId);
}
