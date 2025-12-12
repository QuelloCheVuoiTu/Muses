package questing.controller;

import java.util.ArrayList;
import java.util.List;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import questing.model.Location;
import questing.service.OSRMService;

@Path("/osrm")
public class OSRMController {
    
    private final OSRMService osrmService = new OSRMService();

    @GET
    public Response getDistancesTest() {
        Location source = new Location(014.78285, 41.128950);
        List<Location> destinations = new ArrayList<>();

        destinations.add(new Location(014.77908, 41.13257));
        destinations.add(new Location(14.77451, 41.13181));

        List<Double> response = osrmService.getDistances(source, destinations);
        System.out.println("\n\n\n Response: " + response + "\n\n\n");

        return Response.status(Response.Status.NO_CONTENT).build();
    }
}
