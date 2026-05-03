package com.voiceassistant.repository;
import com.voiceassistant.entity.CodingProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface CodingProgressRepository extends JpaRepository<CodingProgress, Long> {
    List<CodingProgress> findByUserId(Long userId);
}