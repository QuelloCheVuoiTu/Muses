package questing.controller;

import java.util.List;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import questing.exceptions.ArtworkServiceException;
import questing.model.Artwork;
import questing.service.ArtworkService;

@Path("/opere")
@Produces(MediaType.APPLICATION_JSON)
public class ArtworkController {

    private final ArtworkService artworkService = new ArtworkService();

    @GET
    @Path("/health")
    public Response getHealth() {
        return artworkService.health();
    }

    @GET
    @Path("/search")
    public Response getArtworksFromMuseum(@QueryParam("museum") String musuem) {
        try {
            List<Artwork> artworks = artworkService.getArtworksFromMuseum(musuem);
            return Response.status(Response.Status.OK).entity(artworks).build();
        } catch (ArtworkServiceException ase) {
            // Preserve upstream status and include body when available
            int status = ase.getStatus() > 0 ? ase.getStatus() : Response.Status.SERVICE_UNAVAILABLE.getStatusCode();
            String body = ase.getBody() != null ? ase.getBody() : ase.getMessage();
            return Response.status(status).entity(body).build();
        } catch (Exception e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(e.getMessage())
                    .build();
        }
    }
}
