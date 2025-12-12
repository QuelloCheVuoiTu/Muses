package questing.controller;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import questing.service.ArtworkService;
import questing.service.HealthService;
import questing.service.MuseumService;
import questing.service.UserService;

@Path("/")
@Produces("application/json")
public class HealthController {

    private final ArtworkService artworkService = new ArtworkService();
    private final HealthService healthService = new HealthService();
    private final MuseumService museumService = new MuseumService();
    private final UserService userService = new UserService();
    

    @GET
    @Path("health")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getHealth() {
        return healthService.getHealth();
    }

    @GET
    @Path("ready")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getReady() {
        int status;
        
        status = userService.ready().getStatus();
        if (status >= 200 && status < 400) {
            status = museumService.ready().getStatus();
            if (status >= 200 && status < 400) {
                status = artworkService.ready().getStatus();

                return Response.status(Response.Status.OK).entity("All services are ready\n").build();
            }
        }

        return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("Services are not ready\n").build();
    }
}
