package com.chatapp.repository;

import com.chatapp.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByReceiverIdOrderByIdAsc(Long receiverId);

    List<Message> findByReceiverIdAndIdGreaterThanOrderByIdAsc(Long receiverId, Long id);

    @Query("""
            select m from Message m
            where (m.senderId = :userId and m.receiverId = :peerId)
               or (m.senderId = :peerId and m.receiverId = :userId)
            """)
    Page<Message> findConversation(
            @Param("userId") Long userId,
            @Param("peerId") Long peerId,
            Pageable pageable
    );

    List<Message> findByResourceIdOrVideoResourceId(Long resourceId, Long videoResourceId);

    @Modifying
    @Query("""
            delete from Message m
            where (m.senderId = :userId and m.receiverId = :peerId)
               or (m.senderId = :peerId and m.receiverId = :userId)
            """)
    int deleteConversation(
            @Param("userId") Long userId,
            @Param("peerId") Long peerId
    );
}
