package questing.service;

import java.util.List;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import questing.exceptions.ArtworkServiceException;
import questing.model.Artwork;

public class ArtworkService {
    
    // Set base url using the variable from the environment or use default value
    private String baseUrl = "http://" + (
        System.getenv("ARTWORK_SVC_NAME") != null
            ? System.getenv("ARTWORK_SVC_NAME")
            : "127.0.0.1:4000"
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

    public List<Artwork> getArtworksFromMuseum(String museumId) {
        String resourcePath = "/search?museum=" + museumId;
        Client client = ClientBuilder.newClient();

        Response upstream = null;
        try {
            upstream = client.target(baseUrl + resourcePath).request().accept("application/json").get();
            int status = upstream.getStatus();

            if (status >= 200 && status <= 300) {
                List<Artwork> artworks = upstream.readEntity(new GenericType<List<Artwork>>() {});
                return artworks;
            } else {
                String body = "";
                try {
                    body = upstream.readEntity(String.class);
                } catch (Exception ignore) {
                    // ignore
                }
                // throw a typed exception so callers can map status and body
                throw new ArtworkServiceException("Upstream returned HTTP " + status + ": " + body, status, body);
            }
        } catch (Exception e) {
            // If it's already a MuseumServiceException rethrow, otherwise wrap with 503 context
            if (e instanceof ArtworkServiceException) {
                throw (ArtworkServiceException) e;
            }
            throw new ArtworkServiceException("Failed to fetch artworks: " + e.getMessage(), e, Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), e.getMessage());
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
