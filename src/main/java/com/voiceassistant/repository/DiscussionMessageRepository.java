package com.voiceassistant.repository;
import com.voiceassistant.entity.DiscussionMessage;
import com.voiceassistant.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
/**
 * Repository for DiscussionMessage entity.
 */
@Repository
public interface DiscussionMessageRepository extends JpaRepository<DiscussionMessage, Long> {
    /**
     * Find messages for a room ordered by creation date
     */
    List<DiscussionMessage> findByRoomIdOrderByCreatedAtDesc(Long roomId);

    void deleteByUser(User user);
}