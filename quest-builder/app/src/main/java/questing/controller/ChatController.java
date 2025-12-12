package questing.controller;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import questing.service.ChatService;

@Path("/chat")
@Produces("application/json")
public class ChatController {
    private final ChatService chatService = new ChatService();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response sendPrompt() {
        String response = chatService.sendPrompt("Hello");
        return Response.status(Response.Status.OK).entity(response).build();
    }

    @POST
    @Path("/generate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response sendPromptFromBody(PromptRequest request) {
        if (request == null || request.getPrompt() == null || request.getPrompt().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"'prompt' field is required\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        String response = chatService.sendPrompt(request.getPrompt());
        return Response.status(Response.Status.OK).entity(response).build();
    }

    // Simple DTO for JSON binding: { "prompt": "..." }
    public static class PromptRequest {
        private String prompt;

        public PromptRequest() { }

        public String getPrompt() {
            return prompt;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }
    }
}
