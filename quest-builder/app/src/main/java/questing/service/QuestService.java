package questing.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import questing.model.Artwork;
import questing.model.Location;
import questing.model.Museum;
import questing.model.Quest;
import questing.model.Task;
import questing.model.User;

public class QuestService {

    // Initialize other services
    private final ArtworkService artworkService = new ArtworkService();
    private final ChatService chatService = new ChatService();
    private final LocationService locationService = new LocationService();
    private final MuseumService museumService = new MuseumService();
    private final OSRMService osrmService = new OSRMService();
    private final QuestManagerService questManagerService = new QuestManagerService();
    private final UserService userService = new UserService();

    private final double crowdPenaltyCoeff = 1.0;

    // Notte dei Ricercatori special
    private String ndrName = System.getenv("NDR_MUSEUM_NAME") != null
            ? System.getenv("NDR_MUSEUM_NAME")
            : "Notte dei Ricercatori";

    // GET - .../single
    public Quest generateSingleQuest(String userId, String museumId, int maxTasks) {
        List<String> preferences = userService.getUserPreferences(userId);
        List<Artwork> artworks = artworkService.getArtworksFromMuseum(museumId);

        // Shuffle artworks list
        if (artworks == null) {     // Ensure we won't get NPE and shuffle the list (deterministic seed shown)
            artworks = new ArrayList<>();
        }
        // long seed = 42L;
        // Collections.shuffle(artworks, new Random(seed));
        Collections.shuffle(artworks, new Random());

        System.out.println("User preferences: " + preferences);
        for (Artwork a : artworks) {
            System.out.println(a.getName());
        }

        Quest quest = new Quest();
        List<Artwork> tasksArtworks = new ArrayList<>();

        int limit = Math.min(artworks == null ? 0 : artworks.size(), maxTasks);
        for (int i = 0; i < artworks.size() && tasksArtworks.size() < limit; i++) {
            Artwork artwork = artworks.get(i);

            if ((artworks.size() + tasksArtworks.size() - i) <= limit) {
                // If we are reaching the end of the artworks and have still space, fill tasks
                // Basically, we put the tail of the unchosen artworks into the tasks
                Task task = new Task(artwork.getId(), artwork.getName(), artwork.getDescription());
                quest.addTask(task);
                tasksArtworks.add(artwork);
            } else {
                // If we have enough artworks left, check if the artwork is compatible with the user's preferences
                if (artworkMatchPreferences(preferences, artwork)) {
                    Task task = new Task(artwork.getId(), artwork.getName(), artwork.getDescription());
                    quest.addTask(task);
                    tasksArtworks.add(artwork);
                }
            }
        }

        // Extract artwork names
        List<String> artworkNames = new ArrayList<>();
        for (Artwork a : tasksArtworks) {
            artworkNames.add(a.getName());
        }

        String titlePrompt = String.format("""
                You are an expert at creating engaging and evocative titles for quests 
                in a mobile game. Your task is to generate a single, highly creative title 
                for a new quest.

                The quest is set in the museum "%s".
                The museum types are: %s.
                The interests of the user are: %s.
                The artworks that the user will visit are: %s.

                Please craft a title that is captivating, concise, and related to the museum 
                and/or the artworks in general. The title should not be a question. 
                The tone should be adventurous and inspiring.

                Return just the title.
                Do not add any markdown or special characters.
                """, 
                museumService.getMuseumById(museumId).getName(),
                museumService.getMuseumById(museumId).getTypes(),
                preferences,
                artworkNames
            );

        String descriptionPrompt = String.format("""
                You are an expert at creating engaging and evocative descriptions for quests 
                in a mobile game. Your task is to generate a single, highly creative description 
                for a new quest.

                The quest is set in the museum "%s".
                The museum types are: %s.
                The interests of the user are: %s.
                The artworks that the user will visit are: %s.

                Please craft a description that is captivating, concise, and related to the museum 
                and/or the artworks in general. The description should not be a question. 
                The tone should be adventurous and inspiring.

                Return just the description.
                Do not add any markdown or special characters.
                """, 
                museumService.getMuseumById(museumId).getName(),
                museumService.getMuseumById(museumId).getTypes(),
                preferences,
                artworkNames
            );

        String ndrEnv = System.getenv("NOTTE_DEI_RICERCATORI");
        boolean ndr = ndrEnv != null ? Boolean.parseBoolean(ndrEnv) : false;

        if (ndr) {
            quest.setTitle(chatService.sendPrompt(titlePrompt).trim());
            quest.setDescription(chatService.sendPrompt(descriptionPrompt).trim());
        }
        quest.setMuseumId(museumId);

        return quest;
    }

    // GET - .../multiple
    public List<Quest> generateMultipleQuests(String userId, int nQuests, int maxTasks) {
        // Defensive: if no quests requested, return empty list
        if (nQuests <= 0) {
            return new ArrayList<>();
        }

        Location location = locationService.getUserLocation(userId);

        User user = userService.getUserById(userId);

        List<Museum> museums = getMuseumsInSquare(user.getRangePreferences(), location.getLatitude(), location.getLongitude());
        System.out.println("Before: " + museums);

        // FIXME
        // museums = addMissingMuseums(museums);
        // System.out.println("After: " + museums);

        // Select the most appropriate museums
        int limit = Math.min(museums == null ? 0 : museums.size(), nQuests);
        List<Museum> selectedMuseums = selectMuseums(museums, user.getPreferences(), limit);
        System.out.println("\nSelected museums: " + selectedMuseums + "\n");

        // Thread-safe set to collect quests as they become available
        Set<Quest> quests = Collections.newSetFromMap(new ConcurrentHashMap<>());

        int threads = Math.min(nQuests, Math.max(1, Runtime.getRuntime().availableProcessors()));
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CompletionService<Quest> completion = new ExecutorCompletionService<>(executor);

        try {
            // Submit tasks
            for (Museum museum : selectedMuseums) {
                completion.submit(() -> generateSingleQuest(user.getId(), museum.getId(), maxTasks));
            }

            // As each quest completes, insert into the concurrent set
            for (int i = 0; i < selectedMuseums.size(); i++) {
                try {
                    Future<Quest> future = completion.take(); // blocks until one finishes
                    Quest q = future.get();
                    if (q != null) {
                        quests.add(q);
                    }
                } catch (Exception e) {
                    // Keep going if one task fails; print stacktrace for debugging
                    e.printStackTrace();
                }
            }
        } finally {
            executor.shutdownNow();
        }

        List<Museum> sortedMuseums = reorderMuseums(location, selectedMuseums);
        System.out.println("\n\n\nSorted Museums: " + sortedMuseums + "\n\n\n");

        List<Quest> unsortedQuests = new ArrayList<>(quests);
        List<Quest> sortedQuests = new ArrayList<>();

        for (int i = 0; i < sortedMuseums.size(); i++) {
            for (int j = 0; j < unsortedQuests.size(); j++) {
                if (sortedMuseums.get(i).getId() == unsortedQuests.get(j).getMuseumId()) {
                    sortedQuests.add(unsortedQuests.get(j));
                    break;
                }
            }
        }

        System.out.println("Quests: " + quests);
        System.out.println();
        System.out.println("Sorted quests: " + sortedQuests);

        return sortedQuests;
    }

    // Utility methods
    private boolean artworkMatchPreferences(List<String> userPreferences, Artwork artwork) {
        String prompt = String.format("""
                Given the following artwork details and user preferences, determine if the 
                artwork is a good match for the preferences.

                ARTWORK:
                    Name: %s
                    Description: %s
                    Types: %s

                USER PREFERENCES: %s

                Respond with only the boolean value "true" or "false". 
                Do not include any other text, punctuation, or explanation.
                """,
                artwork.getName(),
                artwork.getDescription(),
                artwork.getTypes(),
                userPreferences
            );

        String response = chatService.sendPrompt(prompt);
        if (response == null) {
            return false;
        }

        // Extract a clear "true" or "false" token (robust to punctuation/casing)
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\\b(true|false)\\b", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(response);
        if (m.find()) {
            return Boolean.parseBoolean(m.group(1).toLowerCase());
        }

        // Fallbacks
        String trimmed = response.trim();
        if (trimmed.equalsIgnoreCase("true")) return true;
        if (trimmed.equalsIgnoreCase("false")) return false;

        // Default to false for anything unrecognized
        return false;
    }

    private List<Museum> getMuseumsInSquare(double halfSide, double userLat, double userLon) {
        // Earth radius in km
        double earthRadius = 6371.0;

        // Calculate latitude boundaries (halfSide km north and south)
        double deltaLat = (halfSide / earthRadius) * (180.0 / Math.PI);
        double minLat = userLat - deltaLat;
        double maxLat = userLat + deltaLat;

        // Calculate longitude boundaries (halfSide km east and west at the given latitude)
        double deltaLon = (halfSide / (earthRadius * Math.cos(Math.toRadians(userLat)))) * (180.0 / Math.PI);
        double minLon = userLon - deltaLon;
        double maxLon = userLon + deltaLon;

        System.out.println("userLat: " + userLat + " - userLon: " + userLon + "\n");
        System.out.println("minLat: " + minLat + "\n"
                            + "maxLat: " + maxLat + "\n"
                            + "minLon: " + minLon + "\n"
                            + "maxLon: " + maxLon);

        return museumService.getMuseumsInSquare(minLat, maxLat, minLon, maxLon);
    }

    // FIXME
    // Add missing primary museums to the given list
    // private List<Museum> addMissingMuseums(List<Museum> museums) {
    //     Set<String> existingIds = new HashSet<>();

    //     // First pass: populate set of existing IDs
    //     for (Museum museum : museums) {
    //         existingIds.add(museum.getId());
    //     }

    //     // Second pass: identify missing primary museums and add them
    //     for (Museum museum : museums) {
    //         if (museum.getParent() != null && !existingIds.contains(museum.getParent())) {
    //             // This is a missing primary museum: add it
    //             Museum primaryMuseum = museumService.getMuseumById(museum.getId());
    //             if (primaryMuseum != null) {
    //                 museums.add(primaryMuseum);
    //                 existingIds.add(museum.getId());
    //             }
    //         }
    //     }

    //     return museums;
    // }

    private List<Museum> selectMuseums(List<Museum> museums, List<String> preferences, int limit) {
        if (museums == null) {
            museums = new ArrayList<>();
        }
        long seed = 42L;
        Collections.shuffle(museums, new Random(seed));

        // Separate primary and secondary museums
        List<Museum> primaryMuseums = new ArrayList<>();
        List<Museum> secondaryMuseums = new ArrayList<>();
        for (Museum museum : museums) {
            if (museum.getParent() == null) {
                primaryMuseums.add(museum);
            } else {
                secondaryMuseums.add(museum);
            }
        }

        System.out.println("\nPrimary: " + primaryMuseums);
        System.out.println("Secondary: " + secondaryMuseums + "\n");

        List<Museum> selectedMuseums = new ArrayList<>();
        Set<String> selectedIds = new HashSet<>();

        // Iterate primary museums in shuffled order. For each primary museum that
        // matches the user's preferences, add it and then consider its secondary
        // museums (those with parent == primary.id). Stop once we've reached
        // the requested limit. If we exhaust all primaries (and their
        // secondaries) before reaching `limit`, return what we have.
        for (Museum primary : primaryMuseums) {
            if (selectedMuseums.size() >= limit) break;

            // Skip if already selected for some reason
            if (selectedIds.contains(primary.getId())) continue;

            // If this primary museum matches the user's preferences, select it
            if (museumMatchPreferences(preferences, primary)) {
                selectedMuseums.add(primary);
                selectedIds.add(primary.getId());
            }

            // If we've reached the limit by adding the primary, break early
            if (selectedMuseums.size() >= limit) break;

            // Consider secondary museums that belong to this primary
            for (Museum secondary : secondaryMuseums) {
                if (selectedMuseums.size() >= limit) break;

                if (secondary.getParent() == null) continue;
                if (!secondary.getParent().equals(primary.getId())) continue;
                if (selectedIds.contains(secondary.getId())) continue;

                if (museumMatchPreferences(preferences, secondary)) {
                    selectedMuseums.add(secondary);
                    selectedIds.add(secondary.getId());
                }
            }
        }
        
        return selectedMuseums;
    }

    private boolean museumMatchPreferences(List<String> userPreferences, Museum museum) {
        List<Artwork> artworks = artworkService.getArtworksFromMuseum(museum.getId());

        String prompt = String.format("""
                Given the following museum details and user preferences, determine if the 
                museum is a good match for the preferences.

                MUSEUM:
                    Name: %s
                    Description: %s
                    Types: %s
                    Artworks: %s

                USER PREFERENCES: %s

                Respond with only the boolean value "true" or "false". 
                Do not include any other text, punctuation, or explanation.
                """,
                museum.getName(),
                museum.getDescription(),
                museum.getTypes(),
                artworks,
                userPreferences
            );

        System.out.println(prompt);

        String response = chatService.sendPrompt(prompt);
        if (response == null) {
            return false;
        }

        // Extract a clear "true" or "false" token (robust to punctuation/casing)
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\\b(true|false)\\b", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(response);
        if (m.find()) {
            return Boolean.parseBoolean(m.group(1).toLowerCase());
        }

        // Fallbacks
        String trimmed = response.trim();
        if (trimmed.equalsIgnoreCase("true")) return true;
        if (trimmed.equalsIgnoreCase("false")) return false;

        // Default to false for anything unrecognized
        return false;
    }

    // Dummy mode
    public List<Quest> generateMultipleQuests(int nQuests, int maxTasks) {
        // Thread-safe set to collect quests as they become available
        Set<Quest> quests = Collections.newSetFromMap(new ConcurrentHashMap<>());

        int threads = Math.min(nQuests, Math.max(1, Runtime.getRuntime().availableProcessors()));
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CompletionService<Quest> completion = new ExecutorCompletionService<>(executor);

        try {
            // Submit tasks
            for (int i = 0; i < nQuests; i++) {
                int n = i + 1;
                completion.submit(() -> generateSingleQuest(n, maxTasks));
            }

            // As each quest completes, insert into the concurrent set
            for (int i = 0; i < nQuests; i++) {
                try {
                    Future<Quest> future = completion.take(); // blocks until one finishes
                    Quest q = future.get();
                    if (q != null) {
                        quests.add(q);
                    }
                } catch (Exception e) {
                    // Keep going if one task fails; print stacktrace for debugging
                    e.printStackTrace();
                }
            }
        } finally {
            executor.shutdownNow();
        }

        System.out.println("Quests: " + quests);

        return new ArrayList<>(quests);
    }

    // Fake quest generator for testing concurrency
    private Quest generateSingleQuest(int n, int n_t) {
        // try {
        //     Thread.sleep(5000);
        // } catch (InterruptedException e) {
        //     Thread.currentThread().interrupt();
        // }

        Quest q = new Quest();
        q.setTitle("Quest " + n);
        q.setDescription("Description for quest");
        q.setMuseumId("museumId");

        Set<Task> tasks = new HashSet<>();
        for (int i = 0; i < n_t; i++) {
            Task t = new Task("artworkId" + n + "_" + (i + 1), "Artwork " + (i + 1), "Description for artwork");
            tasks.add(t);
        }
        q.setTasks(tasks);
        
        return q;
    }

    private List<Museum> reorderMuseums(Location userLoc, List<Museum> museums) {
        List<Museum> sortedMuseums = new ArrayList<>();
        List<Museum> unsordedMuseums = new ArrayList<>(museums);
        Museum lastInserted = null;

        // 1. Insert first museum

        // Get converted locations
        List<Location> destinations = new ArrayList<>();
        for (Museum m : unsordedMuseums) {
            destinations.add(new Location(m.getLocation().getLatitude(), m.getLocation().getLongitude()));
        }

        // Obtain distances
        List<Double> distances = osrmService.getDistances(userLoc, destinations);

        // Retrieve crowd penalty
        List<Integer> crowdPenalty = new ArrayList<>();
        for (Museum m : unsordedMuseums) {
            crowdPenalty.add(questManagerService.getNumberStatusMuseum(m.getId(), "in_progress")
                + questManagerService.getNumberStatusMuseum(m.getId(), "pending"));
        }

        // Weight distances, with normalization
        for (int i = 0; i < distances.size(); i++) {
            if (crowdPenalty.get(i) <= 0) {
                continue;
            }

            distances.set(i, distances.get(i) * crowdPenaltyCoeff * crowdPenalty.get(i));
        }

        // Get index of minimum
        int minIndex = 0;
        double minVal = distances.get(0);

        for (int i = 1; i < distances.size(); i++) {
            if (distances.get(i) < minVal) {
                minVal = distances.get(i);
                minIndex = i;
            }
        }

        lastInserted = unsordedMuseums.get(minIndex);
        sortedMuseums.add(lastInserted);
        unsordedMuseums.remove(minIndex);

        // 2. Insert the rest with same logic
        while (!unsordedMuseums.isEmpty()) {
            Location source = new Location(lastInserted.getLocation().getLatitude(), lastInserted.getLocation().getLongitude());

            // Get converted locations
            destinations = new ArrayList<>();
            for (Museum m : unsordedMuseums) {
                destinations.add(new Location(m.getLocation().getLatitude(), m.getLocation().getLongitude()));
            }

            // Obtain distances
            distances = osrmService.getDistances(source, destinations);

            // Retrieve crowd penalty
            crowdPenalty = new ArrayList<>();
            for (Museum m : unsordedMuseums) {
                crowdPenalty.add(questManagerService.getNumberStatusMuseum(m.getId(), "in_progress")
                    + questManagerService.getNumberStatusMuseum(m.getId(), "pending"));
            }

            // Weight distances, with normalization
            for (int i = 0; i < distances.size(); i++) {
                if (crowdPenalty.get(i) <= 0) {
                    continue;
                }

                distances.set(i, distances.get(i) * crowdPenaltyCoeff * crowdPenalty.get(i));
            }

            // Get index of minimum
            minIndex = 0;
            minVal = distances.get(0);

            for (int i = 1; i < distances.size(); i++) {
                if (distances.get(i) < minVal) {
                    minVal = distances.get(i);
                    minIndex = i;
                }
            }

            lastInserted = unsordedMuseums.get(minIndex);
            sortedMuseums.add(lastInserted);
            unsordedMuseums.remove(minIndex);
        }
        
        return sortedMuseums;
    }

    // Notte dei Ricercatori
    public List<Quest> generateMultipleQuestsSpecial(String userId, int maxTasks) {
        List<Museum> museums = museumService.getMuseumsByName(ndrName);
        System.out.println("Museums retrieved: " + museums);

        // Suppose it's just one museum, get the first one
        Museum ndrMusem = museums.get(0);
        System.out.println("Museums selected: " + ndrMusem);

        // Generate a single quest based on the preferences of the user
        Quest quest = generateSingleQuest(userId, ndrMusem.getId(), maxTasks);

        quest.setTitle("Notte dei Ricercatori");
        quest.setDescription("Sei pronto per un'avventura elettrizzante? Bene, perché la Notte dei Ricercatori è iniziata, e la città di Benevento è piena di segreti scientifici da scoprire! La tua missione, se decidi di accettarla, è di immergerti nel Villaggio della Ricerca e sbloccare i misteri che si nascondono dietro gli stand. Ogni stand nasconde un'attività unica, ma solo i veri avventurieri avranno l'onore di scansionare i codici segreti e raccogliere preziosi punti esperienza.\n\nAttenzione, però, non sarà facile: la ricerca è un labirinto di meraviglie e curiosità. Usa la tua astuzia per trovare i codici QR sparsi per l'evento, e preparati a scoprire un mondo dove la scienza è la vera magia! Completa la quest e diventa un esperto di ricerca!");

        // Add the generated single quest to a list
        List<Quest> quests = new ArrayList<>();
        quests.add(quest);

        System.out.println("Quests generated: " + quests);

        // Return the list with the single generated quest
        return quests;
    }
}
