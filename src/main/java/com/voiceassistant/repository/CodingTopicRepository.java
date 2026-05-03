package com.voiceassistant.repository;
import com.voiceassistant.entity.CodingTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
@Repository
public interface CodingTopicRepository extends JpaRepository<CodingTopic, Long> {
    List<CodingTopic> findByUserId(Long userId);
    Optional<CodingTopic> findByUserIdAndTopicName(Long userId, String topicName);
    @Query("SELECT ct FROM CodingTopic ct WHERE ct.user.id = :userId ORDER BY ct.averageScore ASC")
    List<CodingTopic> findWeakestTopicsByUser(@Param("userId") Long userId);
}