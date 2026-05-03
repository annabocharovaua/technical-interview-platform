package com.voiceassistant.repository;
import com.voiceassistant.entity.DiscussionRoom;
import com.voiceassistant.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;
/**
 * Repository for DiscussionRoom entity.
 */
@Repository
public interface DiscussionRoomRepository extends JpaRepository<DiscussionRoom, Long> {
    /**
     * Find room by topic name
     */
    Optional<DiscussionRoom> findByTopicName(String topicName);

    List<DiscussionRoom> findByCreatedBy(User createdBy);
}