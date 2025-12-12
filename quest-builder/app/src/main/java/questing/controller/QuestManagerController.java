package questing.controller;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import questing.service.QuestManagerService;

@Path("/quest-manager")
public class QuestManagerController {
    
    private final QuestManagerService questManagerService = new QuestManagerService();

    @GET
    public Response getQuestsNumberStatus() {
        int n = questManagerService.getNumberStatusMuseum("museumId", "pending");

        return Response.status(Response.Status.OK).entity(n).build();
    }
}
