package questing.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;

@JsonIgnoreProperties(ignoreUnknown = true) // Ignores any JSON fields not present in the Java class
public class User {
    
    @JsonProperty("_id")
    private String id;
    private String firstname;
    private String lastname;
    private String username;
    private String email;
    private String birthday;
    private String country;
    private String avatarUrl;
    private List<String> preferences;
    private double rangePreferences;

    // Constructors
    public User() {
        // Empty constructor
    }

    // Constructor with all fields
    @JsonCreator
    public User(@JsonProperty("_id") String id,
                @JsonProperty("firstname") String firstname,
                @JsonProperty("lastname") String lastname,
                @JsonProperty("username") String username,
                @JsonProperty("email") String email,
                @JsonProperty("birthday") String birthday,
                @JsonProperty("country") String country,
                @JsonProperty("avatarUrl") String avatarUrl,
                @JsonProperty("preferences") List<String> preferences,
                @JsonProperty("range_preferences") double rangePreferences) {
        this.id = id;
        this.firstname = firstname;
        this.lastname = lastname;
        this.username = username;
        this.email = email;
        this.birthday = birthday;
        this.country = country;
        this.avatarUrl = avatarUrl;
        this.preferences = preferences;
        this.rangePreferences = rangePreferences;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFirstname() {
        return firstname;
    }

    public void setFirstname(String firstname) {
        this.firstname = firstname;
    }

    public String getLastname() {
        return lastname;
    }

    public void setLastname(String lastname) {
        this.lastname = lastname;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getBirthday() {
        return birthday;
    }

    public void setBirthday(String birthday) {
        this.birthday = birthday;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public List<String> getPreferences() {
        return preferences;
    }

    public void setPreferences(List<String> preferences) {
        this.preferences = preferences;
    }

    public double getRangePreferences() {
        return rangePreferences;
    }

    public void setRangePreferences(double rp) {
        this.rangePreferences = rp;
    }

    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", firstname='" + firstname + '\'' +
                ", lastname='" + lastname + '\'' +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", birthday=" + birthday + '\'' +
                ", country='" + country + '\'' +
                ", avatarUrl='" + avatarUrl + '\'' +
                ", preferences=" + preferences +
                '}';
    }
}
