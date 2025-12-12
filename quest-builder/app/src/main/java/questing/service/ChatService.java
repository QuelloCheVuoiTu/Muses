package questing.service;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import questing.exceptions.ChatServiceException;
import jakarta.json.Json;
import jakarta.json.JsonObject;

public class ChatService {

    // Set base url using the variable from the environment or use default value
    private String baseUrl = "http://" + (
        System.getenv("CHAT_SVC_NAME") != null
            ? System.getenv("CHAT_SVC_NAME")
            : "192.168.250.20:11434/api"
    );

    public String sendPrompt(String prompt) {
        String resourcePath = "/generate";        
        Client client = ClientBuilder.newClient();

        Response upstream = null;
        try {
            // Build JSON body: { "prompt": <prompt> }
            JsonObject body;
            if (System.getenv("CHAT_SVC_NAME") == null) {
                body = Json.createObjectBuilder()
                .add("model", "CustomGemma9")
                .add("prompt", prompt)
                .add("stream", false)
                .build();
            } else {
                body = Json.createObjectBuilder()
                .add("prompt", prompt)
                .build();
            }

            upstream = client.target(baseUrl + resourcePath)
                .request()
                .accept(MediaType.TEXT_PLAIN)
                .post(Entity.entity(body.toString(), MediaType.APPLICATION_JSON));
            int status = upstream.getStatus();

            if (status >= 200 && status <= 300) {
                // Response is plain text
                String response = upstream.readEntity(String.class);
                return response;
            } else {
                String errorBody = "";
                try {
                    errorBody = upstream.readEntity(String.class);
                } catch (Exception ignore) {
                    // ignore
                }
                // throw a typed exception so callers can map status and body
                throw new ChatServiceException("Upstream returned HTTP " + status + ": " + errorBody, status, errorBody);
            }
        } catch (Exception e) {
            // If it's already a ChatServiceException rethrow, otherwise wrap with 503 context
            if (e instanceof ChatServiceException) {
                throw (ChatServiceException) e;
            }
            throw new ChatServiceException("Failed to fetch response: " + e.getMessage(), e, Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), e.getMessage());
        } finally {
            if (upstream != null) {
                try {
                    upstream.close();
                } catch (Exception ignore) {
                }
            }
            client.close();
        }
    }
}
