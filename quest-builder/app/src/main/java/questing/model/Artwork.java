package questing.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true) // Ignores any JSON fields not present in the Java class
public class Artwork {

    @JsonProperty("_id") // Maps "_id" from JSON to "id" field in Java
    private String id;
    private String description;
    private String imageurl;
    private String museum;
    private String name;
    private List<String> types;

    // Constructors
    public Artwork() {
        // Empty constructor for Jackson
    }

    // Constructor with all fields
    @JsonCreator
    public Artwork(@JsonProperty("_id") String id,
                  @JsonProperty("description") String description,
                  @JsonProperty("imageurl") String imageurl,
                  @JsonProperty("museum") String museum,
                  @JsonProperty("name") String name,
                  @JsonProperty("types") List<String> types) {
        this.id = id;
        this.description = description;
        this.imageurl = imageurl;
        this.museum = museum;
        this.name = name;
        this.types = types;
    }


    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageurl() {
        return imageurl;
    }

    public void setImageurl(String imageurl) {
        this.imageurl = imageurl;
    }

    public String getMuseum() {
        return museum;
    }

    public void setMuseum(String museum) {
        this.museum = museum;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getTypes() {
        return types;
    }

    public void setType(List<String> types) {
        this.types = types;
    }

    @Override
    public String toString() {
        return "Artwork{" +
               "id='" + id + '\'' +
               ", description='" + description + '\'' +
               ", imageurl='" + imageurl + '\'' +
               ", museum='" + museum + '\'' +
               ", name='" + name + '\'' +
               ", types='" + types + '\'' +
               '}';
    }
}