package questing.service;

import java.util.List;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.StringReader;
import jakarta.json.JsonNumber;
import questing.model.Location;
import java.util.ArrayList;

public class OSRMService {
    
    // Set base url using the variable from the environment or use default value
    private String baseUrl = "http://" + (
        System.getenv("OSRM_SVC_NAME") != null
            ? System.getenv("OSRM_SVC_NAME")
            : "127.0.0.1:3000"
    );

    public List<Double> getDistances(Location source, List<Location> destinations) {
        // Validate inputs
        List<Double> result = new ArrayList<>();
        if (source == null || destinations == null || destinations.isEmpty()) {
            return result;
        }

        String resourcePath = "/table/v1/walking/";
        // Build coordinate list: source first, then all destinations separated by ';'
        StringBuilder coords = new StringBuilder();
        coords.append(source.getLatitude()).append(',').append(source.getLongitude());
        for (Location l : destinations) {
            coords.append(';').append(l.getLatitude()).append(',').append(l.getLongitude());
        }

        // Build destinations indices: destinations are after source, so indices 1..n
        StringBuilder destIdx = new StringBuilder();
        for (int i = 0; i < destinations.size(); i++) {
            if (i > 0) destIdx.append(';');
            destIdx.append(i + 1);
        }

        String queryParams = "?sources=0&destinations=" + destIdx.toString() + "&annotations=distance";

        String fullPath = baseUrl + resourcePath + coords.toString() + queryParams;
        System.out.println("OSRM request: " + fullPath);

        Client client = ClientBuilder.newClient();
        Response upstream = null;

        try {
            upstream = client.target(fullPath).request().get();
            if (upstream.getStatus() != 200) {
                return result;
            }


            String json = upstream.readEntity(String.class);
            try (JsonReader jr = Json.createReader(new StringReader(json))) {
                JsonObject root = jr.readObject();
                JsonArray distances = root.getJsonArray("distances");
                if (distances == null || distances.isEmpty()) {
                    return result;
                }

                // distances is an array of arrays. We return the first row (sources=0)
                JsonArray firstRow = distances.getJsonArray(0);
                for (int i = 0; i < firstRow.size(); i++) {
                    if (firstRow.isNull(i)) {
                        result.add(Double.NaN);
                    } else {
                        JsonNumber num = firstRow.getJsonNumber(i);
                        result.add(num.doubleValue());
                    }
                }
            }

            return result;
        } catch (Exception e) {
            System.err.println("Error calling OSRM: " + e.getMessage());
            return result;
        } finally {
            if (upstream != null) upstream.close();
            client.close();
        }
    }
}
