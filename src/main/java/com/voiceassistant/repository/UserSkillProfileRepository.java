package com.voiceassistant.repository;
import com.voiceassistant.entity.UserSkillProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
@Repository
public interface UserSkillProfileRepository extends JpaRepository<UserSkillProfile, Long> {
    Optional<UserSkillProfile> findByUserId(Long userId);
}