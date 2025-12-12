package questing.service;

import java.util.List;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.GenericType;
import questing.exceptions.MuseumServiceException;
import questing.model.Museum;

public class MuseumService {

    // Set base url using the variable from the environment or use default value
    private String baseUrl = "http://" + (
        System.getenv("MUSEUM_SVC_NAME") != null
            ? System.getenv("MUSEUM_SVC_NAME")
            : "127.0.0.1:8000"
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

    public List<Museum> getAllMuseums() {
        String resourcePath = "/";
        Client client = ClientBuilder.newClient();
    
        Response upstream = null;
        try {
            upstream = client.target(baseUrl + resourcePath).request().accept("application/json").get();
            int status = upstream.getStatus();
            if (status >= 200 && status < 300) {
                // Deserialize JSON array into List<Museum>
                List<Museum> museums = upstream.readEntity(new GenericType<List<Museum>>() {});
                return museums;
            } else {
                String body = "";
                try {
                    body = upstream.readEntity(String.class);
                } catch (Exception ignore) {
                    // ignore
                }
                // throw a typed exception so callers can map status and body
                throw new MuseumServiceException("Upstream returned HTTP " + status + ": " + body, status, body);
            }
        } catch (Exception e) {
            // If it's already a MuseumServiceException rethrow, otherwise wrap with 503 context
            if (e instanceof MuseumServiceException) {
                throw (MuseumServiceException) e;
            }
            throw new MuseumServiceException("Failed to fetch museums: " + e.getMessage(), e, Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), e.getMessage());
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

    public Museum getMuseumById(String id) {
        String resourcePath = "/" + id;
        Client client = ClientBuilder.newClient();

        Response upstream = null;
        try {
            upstream = client.target(baseUrl + resourcePath).request().accept("application/json").get();
            int status = upstream.getStatus();
            if (status >= 200 && status < 300) {
                // Deserialize JSON array into Museum
                Museum museum = upstream.readEntity(new GenericType<Museum>() {});
                return museum;
            } else {
                String body = "";
                try {
                    body = upstream.readEntity(String.class);
                } catch (Exception ignore) {
                    // ignore
                }
                // throw a typed exception so callers can map status and body
                throw new MuseumServiceException("Upstream returned HTTP " + status + ": " + body, status, body);
            }
        } catch (Exception e) {
            // If it's already a MuseumServiceException rethrow, otherwise wrap with 503 context
            if (e instanceof MuseumServiceException) {
                throw (MuseumServiceException) e;
            }
            throw new MuseumServiceException("Failed to fetch museum: " + e.getMessage(), e, Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), e.getMessage());
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

    public List<Museum> getMuseumsInSquare(double minLat, double maxLat, double minLon, double maxLon) {
        String resourcePath = "/search?"
            + "minLat=" + minLat + "&"
            + "maxLat=" + maxLat + "&"
            + "minLon=" + minLon + "&"
            + "maxLon=" + maxLon;
        Client client = ClientBuilder.newClient();
        
        Response upstream = null;
        try {
            upstream = client.target(baseUrl + resourcePath).request().accept("application/json").get();
            int status = upstream.getStatus();
            if (status >= 200 && status < 300) {
                // Deserialize JSON array into List<Museum>
                List<Museum> museums = upstream.readEntity(new GenericType<List<Museum>>() {});
                return museums;
            } else {
                String body = "";
                try {
                    body = upstream.readEntity(String.class);
                } catch (Exception ignore) {
                    // ignore
                }
                // throw a typed exception so callers can map status and body
                throw new MuseumServiceException("Upstream returned HTTP " + status + ": " + body, status, body);
            }
        } catch (Exception e) {
            // If it's already a MuseumServiceException rethrow, otherwise wrap with 503 context
            if (e instanceof MuseumServiceException) {
                throw (MuseumServiceException) e;
            }
            throw new MuseumServiceException("Failed to fetch museums: " + e.getMessage(), e, Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), e.getMessage());
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

    public List<Museum> getMuseumsByName(String name) {
        String resourcePath = "/search?"
            + "name=" + name;
        Client client = ClientBuilder.newClient();
        
        Response upstream = null;
        try {
            upstream = client.target(baseUrl + resourcePath).request().accept("application/json").get();
            int status = upstream.getStatus();
            if (status >= 200 && status < 300) {
                // Deserialize JSON array into List<Museum>
                List<Museum> museums = upstream.readEntity(new GenericType<List<Museum>>() {});
                return museums;
            } else {
                String body = "";
                try {
                    body = upstream.readEntity(String.class);
                } catch (Exception ignore) {
                    // ignore
                }
                // throw a typed exception so callers can map status and body
                throw new MuseumServiceException("Upstream returned HTTP " + status + ": " + body, status, body);
            }
        } catch (Exception e) {
            // If it's already a MuseumServiceException rethrow, otherwise wrap with 503 context
            if (e instanceof MuseumServiceException) {
                throw (MuseumServiceException) e;
            }
            throw new MuseumServiceException("Failed to fetch museums: " + e.getMessage(), e, Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), e.getMessage());
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
