package questing.controller;

import java.util.List;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import questing.exceptions.UserServiceException;
import questing.model.User;
import questing.service.UserService;


@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserController {

    private final UserService userService = new UserService();

    @GET
    @Path("/health")
    public Response getHealth() {
        return userService.health();
    }

    @GET
    @Path("/")
    public Response getAllUsers() {
        try {
            List<User> users = userService.getAllUsers();
            return Response.status(Response.Status.OK).entity(users).build();
        } catch (UserServiceException use) {
            // Preserve upstream status and include body when available
            int status = use.getStatus() > 0 ? use.getStatus() : Response.Status.SERVICE_UNAVAILABLE.getStatusCode();
            String body = use.getBody() != null ? use.getBody() : use.getMessage();
            return Response.status(status).entity(body).build();
        } catch (Exception e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/{id}")
    public Response getUserById(@PathParam("id") String id) {
        try {
            User user = userService.getUserById(id);
            return Response.status(Response.Status.OK).entity(user).build();
        } catch (UserServiceException use) {
            // Preserve upstream status and include body when available
            int status = use.getStatus() > 0 ? use.getStatus() : Response.Status.SERVICE_UNAVAILABLE.getStatusCode();
            String body = use.getBody() != null ? use.getBody() : use.getMessage();
            return Response.status(status).entity(body).build();
        } catch (Exception e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(e.getMessage())
                    .build();
        }    
    }

    @GET
    @Path("/{id}/preferences")
    public Response getUserPreferences(@PathParam("id") String id) {
        try {
            List<String> preferences = userService.getUserPreferences(id);
            return Response.status(Response.Status.OK).entity(preferences).build();
        } catch (UserServiceException use) {
            // Preserve upstream status and include body when available
            int status = use.getStatus() > 0 ? use.getStatus() : Response.Status.SERVICE_UNAVAILABLE.getStatusCode();
            String body = use.getBody() != null ? use.getBody() : use.getMessage();
            return Response.status(status).entity(body).build();
        } catch (Exception e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(e.getMessage())
                    .build();
        }    
    }
}