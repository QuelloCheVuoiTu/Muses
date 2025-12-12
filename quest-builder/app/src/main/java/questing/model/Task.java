package questing.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true) // Ignores any JSON fields not present in the Java class
public class Task {
    
    private String artworkId;
    private String title;
    private String description;

    //Constructors
    public Task() {
        // Empty constructor
    }

    @JsonCreator
    public Task(@JsonProperty("artwork_id") String artworkId,
                @JsonProperty("title") String title,
                @JsonProperty("description") String description) {
        this.artworkId = artworkId;
        this.title = title;
        this.description = description;
    }

    // Getters and setters
    public String getArtworkId() {
        return artworkId;
    }

    public void setArtworkId(String artworkId) {
        this.artworkId = artworkId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "Task{" +
                "artworkId='" + artworkId + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
