package questing.service;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.StringReader;

public class QuestManagerService {

    // Set base url using the variable from the environment or use default value
    private String baseUrl = "http://" + (
        System.getenv("QUEST_MAN_SVC_NAME") != null
            ? System.getenv("QUEST_MAN_SVC_NAME")
            : "quest-manager"
    );

    public int getNumberStatusMuseum(String museumId, String status) {
        String resourcePath = "/status?subject=" + museumId + "&status=" + status + "&full=false";
        Client client = ClientBuilder.newClient();

        try {
            Response upstream = client.target(baseUrl + resourcePath).request().get();

            // Check for successful response
            if (upstream.getStatus() != Response.Status.OK.getStatusCode()) {
                return -1;
            }

            String body = upstream.readEntity(String.class);
            if (body == null || body.isEmpty()) {
                return -1;
            }

            try (JsonReader jr = Json.createReader(new StringReader(body))) {
                JsonObject obj = jr.readObject();
                if (obj.containsKey("count") && !obj.isNull("count")) {
                    try {
                        return obj.getInt("count");
                    } catch (Exception e) {
                        // If count is not an int, try to parse as number
                        try {
                            return Integer.parseInt(obj.get("count").toString());
                        } catch (Exception ex) {
                            return -1;
                        }
                    }
                } else {
                    return -1;
                }
            }
        } catch (Exception e) {
            // On any error, return -1 to indicate failure
            return -1;
        } finally {
            client.close();
        }
    }
}
