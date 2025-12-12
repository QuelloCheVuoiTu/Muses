package questing.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true) // Ignores any JSON fields not present in the Java class
public class Museum {

    @JsonProperty("_id") // Maps "_id" from JSON to "id" field in Java
    private String id;
    private String name;
    private String description;
    private Location location; // Represents the nested "location" JSON object
    private String hours;
    private String price;
    private double rating;
    private String imageurl;
    private List<String> types;
    private String parent;    // Optional - represented as ObjectID string

    // Inner class to represent the "location" object in JSON
    public static class Location {
        private double latitude;
        private double longitude;

        // Empty constructor for Jackson
        public Location() {}

        @JsonCreator
        public Location(@JsonProperty("latitude") double latitude, @JsonProperty("longitude") double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        // Getters and Setters for Location
        public double getLatitude() {
            return latitude;
        }

        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }

        @Override
        public String toString() {
            return "Location{" +
                   "latitude=" + latitude +
                   ", longitude=" + longitude +
                   '}';
        }
    }

    // No-arg constructor for Jackson
    public Museum() {
        // Empty constructor for Jackson
    }

    // JSON constructor for deserialization
    @JsonCreator
    public Museum(@JsonProperty("_id") String id,
                  @JsonProperty("description") String description,
                  @JsonProperty("hours") String hours,
                  @JsonProperty("imageurl") String imageurl,
                  @JsonProperty("location") Location location,
                  @JsonProperty("name") String name,
                  @JsonProperty("parent") String parent,
                  @JsonProperty("price") String price,
                  @JsonProperty("rating") double rating,
                  @JsonProperty("types") List<String> types) {
        this.id = id;
        this.description = description;
        this.hours = hours;
        this.imageurl = imageurl;
        this.location = location;
        this.name = name;
        this.parent = normalizeParent(parent);
        this.price = price;
        this.rating = rating;
        this.types = types;
    }


    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    // created_at and updated_at are intentionally omitted/ignored

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getHours() {
        return hours;
    }

    public void setHours(String hours) {
        this.hours = hours;
    }

    public String getImageurl() {
        return imageurl;
    }

    public void setImageurl(String imageurl) {
        this.imageurl = imageurl;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParent() {
        return parent;
    }

    public void setParent(String parent) {
        this.parent = normalizeParent(parent);
    }

    /**
     * Normalizes a parent id string: returns null when the value is null,
     * empty/blank, or contains only '0' characters. Otherwise returns the
     * trimmed original string.
     */
    private String normalizeParent(String parent) {
        if (parent == null) return null;
        String trimmed = parent.trim();
        if (trimmed.isEmpty()) return null;
        boolean allZeros = true;
        for (int i = 0; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) != '0') {
                allZeros = false;
                break;
            }
        }
        return allZeros ? null : trimmed;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public double getRating() {
        return rating;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }

    public List<String> getTypes() {
        return types;
    }

    public void setTypes(List<String> types) {
        this.types = types;
    }

    @Override
    public String toString() {
    return "Museum{" +
           "id='" + id + '\'' +
           ", description='" + description + '\'' +
           ", hours='" + hours + '\'' +
           ", imageurl='" + imageurl + '\'' +
           ", location=" + location +
           ", name='" + name + '\'' +
           ", parent='" + parent + '\'' +
           ", price='" + price + '\'' +
           ", rating=" + rating +
           ", types=" + types +
           '}';
    }
}