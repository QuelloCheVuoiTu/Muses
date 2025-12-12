package questing.service;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import questing.exceptions.LocationServiceException;
import questing.model.Location;

public class LocationService {
    
    // Set base url using the variable from the environment or use default value
    private String baseUrl = "http://" + (
        System.getenv("LOC_SVC_NAME") != null
            ? System.getenv("LOC_SVC_NAME")
            : "127.0.0.1:9000"
    );

    public Response health() {
        String resourcePath = "/health";
        Client client = ClientBuilder.newClient();

        Response upstream = null;
        try {
            upstream = client.target(baseUrl + resourcePath).request().accept("application/json").get();
            int status = upstream.getStatus();

            String body = "";
            try {
                body = upstream.readEntity(String.class);
            } catch (Exception ignore) {
                // ignore
            }

            if (status >= 200 && status < 300) {
                // try to extract the "message" field from the JSON response
                String message = body;
                try {
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"");
                    java.util.regex.Matcher m = p.matcher(body);
                    if (m.find()) {
                        message = m.group(1);
                    }
                } catch (Exception ignore) {
                    // fall back to returning full body
                }

                return Response.ok(message).build();
            } else {
                // capture body when returning an error so callers can inspect it
                throw new LocationServiceException("Upstream returned HTTP " + status + ": " + body, status, body);
            }
        } catch (Exception e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("Service unavailable: " + e.getMessage())
                    .build();
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

    public Location getUserLocation(String id) {
        String resourcePath = "/" + id;
        Client client = ClientBuilder.newClient();

        Response upstream = null;
        try {
            upstream = client.target(baseUrl + resourcePath).request().accept("application/json").get();
            int status = upstream.getStatus();
            if (status >= 200 && status < 300) {
                // Deserialize JSON array into Museum
                Location location = upstream.readEntity(new GenericType<Location>() {});
                return location;
            } else {
                String body = "";
                try {
                    body = upstream.readEntity(String.class);
                } catch (Exception ignore) {
                    // ignore
                }
                // throw a typed exception so callers can map status and body
                throw new LocationServiceException("Upstream returned HTTP " + status + ": " + body, status, body);
            }
        } catch (Exception e) {
            // If it's already a LocationServiceException rethrow, otherwise wrap with 503 context
            if (e instanceof LocationServiceException) {
                throw (LocationServiceException) e;
            }
            throw new LocationServiceException("Failed to fetch user location: " + e.getMessage(), e, Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), e.getMessage());
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
