package com.voiceassistant.dto;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
/**
 * Data Transfer Object for extracted coding topics from job descriptions.
 * Contains identified technical skills and requirement categories.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtractedTopicsDTO {
    @SerializedName("topics")
    private List<String> topics;
    @SerializedName("category")
    private String category;
    @SerializedName("difficulty_level")
    private String difficultyLevel;
    @SerializedName("key_concepts")
    private List<String> keyConcepts;
}