package questing.service;

import jakarta.ws.rs.core.Response;

public class HealthService {
    public Response getHealth() {
        String msg = "Questing system is online\n";

        return Response.status(Response.Status.OK).entity(msg).build();
    }
}
