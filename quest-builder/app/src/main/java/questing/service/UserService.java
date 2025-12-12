package questing.service;

import java.util.List;
import java.util.ArrayList;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import questing.exceptions.UserServiceException;
import questing.model.User;

public class UserService {
    
    // Set base url using the variable from the environment or use default value
    private String baseUrl = "http://" + (
        System.getenv("USER_SVC_NAME") != null
            ? System.getenv("USER_SVC_NAME")
            : "127.0.0.1:2000"
    );

    public Response health() {
        String resourcePath = "/health";
        Client client = ClientBuilder.newClient();
    
        try {
            Response upstream = client.target(baseUrl + resourcePath).request().get();
            return upstream;
        } catch (Exception e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("Service unavailable: " + e.getMessage())
                    .build();
        } finally {
            client.close();
        }
    }

    public Response ready() {
        String resourcePath = "/ready";
        Client client = ClientBuilder.newClient();
    
        try {
            Response upstream = client.target(baseUrl + resourcePath).request().get();
            return upstream;
        } catch (Exception e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("Service unavailable: " + e.getMessage())
                    .build();
        } finally {
            client.close();
        }
    }

    public List<User> getAllUsers() {
        String resourcePath = "/";
        Client client = ClientBuilder.newClient();
    
        Response upstream = null;
        try {
            upstream = client.target(baseUrl + resourcePath).request().accept("application/json").get();
            int status = upstream.getStatus();
            if (status >= 200 && status < 300) {
                // Deserialize JSON array into List<User>
                List<User> users = upstream.readEntity(new GenericType<List<User>>() {});
                return users;
            } else {
                String body = "";
                try {
                    body = upstream.readEntity(String.class);
                } catch (Exception ignore) {
                    // ignore
                }
                // throw a typed exception so callers can map status and body
                throw new UserServiceException("Upstream returned HTTP " + status + ": " + body, status, body);
            }
        } catch (Exception e) {
            // If it's already a UserServiceException rethrow, otherwise wrap with 503 context
            if (e instanceof UserServiceException) {
                throw (UserServiceException) e;
            }
            throw new UserServiceException("Failed to fetch users: " + e.getMessage(), e, Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), e.getMessage());
        } finally {
            if (upstream != null) {
                try {
                    upstream.close();
                } catch (Exception ignore) {
                }
            }
            client.close();
        }
    }

    public User getUserById(String id) {
        String resourcePath = "/" + id;
        Client client = ClientBuilder.newClient();

        Response upstream = null;
        try {
            upstream = client.target(baseUrl + resourcePath).request().accept("application/json").get();
            int status = upstream.getStatus();
            if (status >= 200 && status < 300) {
                // Deserialize JSON array into User
                User user = upstream.readEntity(new GenericType<User>() {});
                return user;
            } else {
                String body = "";
                try {
                    body = upstream.readEntity(String.class);
                } catch (Exception ignore) {
                    // ignore
                }
                // throw a typed exception so callers can map status and body
                throw new UserServiceException("Upstream returned HTTP " + status + ": " + body, status, body);
            }
        } catch (Exception e) {
            // If it's already a UserServiceException rethrow, otherwise wrap with 503 context
            if (e instanceof UserServiceException) {
                throw (UserServiceException) e;
            }
            throw new UserServiceException("Failed to fetch user: " + e.getMessage(), e, Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), e.getMessage());
        } finally {
            if (upstream != null) {
                try {
                    upstream.close();
                } catch (Exception ignore) {
                }
            }
            client.close();
        }
    }

    public List<String> getUserPreferences(String id) {
        String resourcePath = "/" + id + "/preferences";
        Client client = ClientBuilder.newClient();

        Response upstream = null;
        try {
            upstream = client.target(baseUrl + resourcePath).request().accept("application/json").get();
            int status = upstream.getStatus();
            if (status >= 200 && status < 300) {
                // Read raw body and parse defensively to support either:
                // - a bare JSON array: ["art","history"]
                // - a wrapper object: { "preferences": [ ... ] }
                String raw = upstream.readEntity(String.class);
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(raw);

                JsonNode prefsNode = null;
                if (root.isArray()) {
                    prefsNode = root;
                } else if (root.has("preferences") && root.get("preferences").isArray()) {
                    prefsNode = root.get("preferences");
                }

                if (prefsNode != null) {
                    List<String> prefs = new ArrayList<>();
                    for (JsonNode n : prefsNode) {
                        if (n.isTextual()) {
                            prefs.add(n.asText());
                        } else if (n.has("preferenza") && n.get("preferenza").isTextual()) {
                            prefs.add(n.get("preferenza").asText());
                        } else {
                            // Fall back to string representation of the element
                            prefs.add(n.toString());
                        }
                    }
                    return prefs;
                }

                // If we reach here, the response had an unexpected JSON shape - surface it
                throw new UserServiceException("Upstream returned JSON with unexpected shape: " + raw, Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), raw);
            } else {
                String body = "";
                try {
                    body = upstream.readEntity(String.class);
                } catch (Exception ignore) {
                    // ignore
                }
                // throw a typed exception so callers can map status and body
                throw new UserServiceException("Upstream returned HTTP " + status + ": " + body, status, body);
            }
        } catch (Exception e) {
            // If it's already a UserServiceException rethrow, otherwise wrap with 503 context
            if (e instanceof UserServiceException) {
                throw (UserServiceException) e;
            }
            throw new UserServiceException("Failed to fetch user: " + e.getMessage(), e, Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), e.getMessage());
        } finally {
            if (upstream != null) {
                try {
                    upstream.close();
                } catch (Exception ignore) {
                }
            }
            client.close();
        }
    }
}
