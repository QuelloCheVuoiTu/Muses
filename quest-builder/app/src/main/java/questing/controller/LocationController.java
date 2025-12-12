package questing.controller;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import questing.exceptions.LocationServiceException;
import questing.model.Location;
import questing.service.LocationService;

@Path("/location")
@Produces(MediaType.APPLICATION_JSON)
public class LocationController {
    private final LocationService locationService = new LocationService();

    @GET
    @Path("/health")
    public Response getHealth() {
        return locationService.health();
    }

    @GET
    @Path("/ready")
    public Response getReadiness() {
        return locationService.ready();
    }

    @GET
    @Path("/{id}")
    public Response getUserLocation(@PathParam("id") String userId) {
        try {
            Location location = locationService.getUserLocation(userId);
            return Response.status(Response.Status.OK).entity(location).build();
        } catch (LocationServiceException mse) {
            // Preserve upstream status and include body when available
            int status = mse.getStatus() > 0 ? mse.getStatus() : Response.Status.SERVICE_UNAVAILABLE.getStatusCode();
            String body = mse.getBody() != null ? mse.getBody() : mse.getMessage();
            return Response.status(status).entity(body).build();
        } catch (Exception e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(e.getMessage())
                    .build();
        }
    }
}
