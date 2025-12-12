package questing.model;

import java.util.HashSet;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true) // Ignores any JSON fields not present in the Java class
public class Quest {
    
    private String title;
    private String description;
    private String museumId;
    private Set<Task> tasks;

    // Constructors
    public Quest() {
        // Empty constructor
        this.tasks = new HashSet<>();
    }

    @JsonCreator    // Constructor with all args
    public Quest(@JsonProperty("title") String title,
                    @JsonProperty("description") String description,
                    @JsonProperty("museum_id") String museumId,
                    @JsonProperty("tasks") Set<Task> tasks) {
        this.title = title;
        this.description = description;
        this.museumId = museumId;
        this.tasks = tasks;
    }

    // Getters and setters
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

    public String getMuseumId() {
        return museumId;
    }

    public void setMuseumId(String museumId) {
        this.museumId = museumId;
    }

    public Set<Task> getTasks() {
        return tasks;
    }

    public void setTasks(Set<Task> tasks) {
        this.tasks = tasks;
    }

    public void addTask(Task task) {
        this.tasks.add(task);
    }

    @Override
    public String toString() {
        return "Quest{" +
                "title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", museumId='" + museumId + '\'' +
                ", tasks=" + tasks +
                '}';
    }
}
