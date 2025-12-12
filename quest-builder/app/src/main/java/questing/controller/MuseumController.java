package questing.controller;

import java.util.List;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import questing.exceptions.MuseumServiceException;
import questing.model.Museum;
import questing.service.MuseumService;

@Path("/museum")
@Produces(MediaType.APPLICATION_JSON)
public class MuseumController {
    private final MuseumService museumService = new MuseumService();

    @GET
    @Path("/health")
    public Response getHealth() {
        return museumService.health();
    }

    @GET
    @Path("/")
    public Response getAllMuseums() {
        try {
            List<Museum> museums = museumService.getAllMuseums();
            return Response.status(Response.Status.OK).entity(museums).build();
        } catch (MuseumServiceException mse) {
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

    @GET
    @Path("/{id}")
    public Response getMuseumById(@PathParam("id") String id) {
        try {
            Museum museum = museumService.getMuseumById(id);
            return Response.status(Response.Status.OK).entity(museum).build();
        } catch (MuseumServiceException mse) {
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

    @GET
    @Path("/getmuseumsbytype/{type}")
    public Response getMuseumsByType(@PathParam("type") String type) {
        return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }
}
