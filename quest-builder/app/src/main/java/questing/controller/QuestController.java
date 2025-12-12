package questing.controller;

import java.util.List;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import questing.model.Quest;
import questing.service.QuestService;;

@Path("/quest")
@Produces(MediaType.APPLICATION_JSON)
public class QuestController {
    
    private final QuestService questService = new QuestService();

    @GET
    @Path("/health")
    public Response getHealth() {
        return Response.status(Response.Status.OK).entity("Quest service is online").build();
    }

    @GET
    @Path("/single")
    public Response getSingleQuest(@QueryParam("user_id") String userId,
                                    @QueryParam("museum_id") String museumId,
                                    @QueryParam("max_tasks") int maxTasks) {
        Quest quest = questService.generateSingleQuest(userId, museumId, maxTasks);

        if (quest == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        
        return Response.status(Response.Status.OK).entity(quest).build();
    }

    @GET
    @Path("/multiple")
    public Response getMultipleQuests(@QueryParam("user_id") String userId,
                                        @QueryParam("n_quests") int nQuests,
                                        @QueryParam("max_tasks") int maxTasks) {
        String dummyEnv = System.getenv("DUMMY_MODE");
        boolean dummy = dummyEnv != null ? Boolean.parseBoolean(dummyEnv) : false;

        String ndrEnv = System.getenv("NOTTE_DEI_RICERCATORI");
        boolean ndr = ndrEnv != null ? Boolean.parseBoolean(ndrEnv) : false;

        List<Quest> quests;
        if (dummy) {
            System.out.println("Using profile: dummy");
            quests = questService.generateMultipleQuests(nQuests, maxTasks);
        } else {
            if (ndr) {
                System.out.println("Using profile: Notte dei Ricercatori");
                quests = questService.generateMultipleQuestsSpecial(userId, maxTasks);
            } else {
                quests = questService.generateMultipleQuests(userId, nQuests, maxTasks);
            }
        }

        if (quests == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        return Response.status(Response.Status.OK).entity(quests).build();
    }
}
