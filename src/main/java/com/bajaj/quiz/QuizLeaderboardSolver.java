package com.bajaj.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

public class QuizLeaderboardSolver {

    private static final String BASE_URL      = "https://devapigw.vidalhealthtpa.com/srm-quiz-task";
    private static final String REG_NO        = "RA2311026010130";
    private static final int    TOTAL_POLLS   = 10;
    private static final int    POLL_DELAY_MS = 5000;

    private final HttpClient   httpClient;
    private final ObjectMapper mapper;

    private final Set<String>          seenEvents = new HashSet<>();
    private final Map<String, Integer> scoreboard = new LinkedHashMap<>();

    private String capturedSetId = null;

    public QuizLeaderboardSolver() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
    }

    public static void main(String[] args) throws Exception {
        new QuizLeaderboardSolver().run();
    }

    private void run() throws Exception {
        System.out.println("=== Quiz Leaderboard Solver ===");
        System.out.println("Registration Number : " + REG_NO);
        System.out.println("Total polls         : " + TOTAL_POLLS);
        System.out.println("Delay between polls : " + POLL_DELAY_MS + "ms\n");

        for (int i = 0; i < TOTAL_POLLS; i++) {
            System.out.printf("[Poll %d/%d] Fetching (poll=%d)...%n", i + 1, TOTAL_POLLS, i);
            fetchAndProcess(i);

            if (i < TOTAL_POLLS - 1) {
                Thread.sleep(POLL_DELAY_MS);
            }
        }

        List<Map.Entry<String, Integer>> leaderboard = buildLeaderboard();
        printLeaderboard(leaderboard);

        submitLeaderboard(leaderboard);
    }

    private void fetchAndProcess(int pollIndex) throws Exception {
        String url = BASE_URL + "/quiz/messages?regNo=" + REG_NO + "&poll=" + pollIndex;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("  Raw response: " + resp.body());

        if (resp.statusCode() != 200) {
            System.out.printf("  [WARN] HTTP %d — skipping poll %d%n", resp.statusCode(), pollIndex);
            return;
        }

        JsonNode root = mapper.readTree(resp.body());

        if (capturedSetId == null) {
            for (String candidate : new String[]{"setId", "set_id", "SetId"}) {
                if (root.hasNonNull(candidate)) {
                    capturedSetId = root.path(candidate).asText();
                    System.out.println("  [INFO] Captured setId: " + capturedSetId);
                    break;
                }
            }
        }

        processEvents(root, pollIndex);
    }

    private void processEvents(JsonNode root, int pollIndex) {
        JsonNode events = root.path("events");
        if (!events.isArray()) {
            events = root;
        }

        if (!events.isArray() || events.size() == 0) {
            System.out.printf("  [INFO] Poll %d: no events in response%n", pollIndex);
            return;
        }

        int fresh = 0, dupes = 0;

        for (JsonNode event : events) {
            String roundId     = event.path("roundId").asText("").trim();
            String participant = event.path("participant").asText("").trim();
            int    score       = event.path("score").asInt(0);

            if (roundId.isEmpty() || participant.isEmpty()) continue;

            String key = roundId + "::" + participant;

            if (seenEvents.contains(key)) {
                dupes++;
                continue;
            }

            seenEvents.add(key);
            scoreboard.merge(participant, score, Integer::sum);
            fresh++;
        }

        System.out.printf("  ✓ Processed %d new event(s), skipped %d duplicate(s)%n", fresh, dupes);
    }

    private List<Map.Entry<String, Integer>> buildLeaderboard() {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(scoreboard.entrySet());
        sorted.sort((a, b) -> {
            int cmp = Integer.compare(b.getValue(), a.getValue());
            return cmp != 0 ? cmp : a.getKey().compareTo(b.getKey());
        });
        return sorted;
    }

    private void printLeaderboard(List<Map.Entry<String, Integer>> leaderboard) {
        System.out.println("\n── Final Leaderboard ──────────────────────");
        System.out.printf("%-5s %-20s %s%n", "Rank", "Participant", "Total Score");
        System.out.println("-------------------------------------------");

        int rank = 1, grandTotal = 0;
        for (Map.Entry<String, Integer> e : leaderboard) {
            System.out.printf("%-5d %-20s %d%n", rank++, e.getKey(), e.getValue());
            grandTotal += e.getValue();
        }

        System.out.println("-------------------------------------------");
        System.out.printf("Grand Total Score : %d%n", grandTotal);
        System.out.printf("setId captured    : %s%n%n", capturedSetId != null ? capturedSetId : "(not returned by API)");
    }

    private void submitLeaderboard(List<Map.Entry<String, Integer>> leaderboard) throws Exception {
        System.out.println("Submitting leaderboard...");

        ObjectNode payload = mapper.createObjectNode();
        payload.put("regNo", REG_NO);

        if (capturedSetId != null && !capturedSetId.isEmpty()) {
            payload.put("setId", capturedSetId);
        }

        ArrayNode arr = mapper.createArrayNode();
        int submittedTotal = 0;
        for (Map.Entry<String, Integer> e : leaderboard) {
            ObjectNode row = mapper.createObjectNode();
            row.put("participant", e.getKey());
            row.put("totalScore",  e.getValue());
            arr.add(row);
            submittedTotal += e.getValue();
        }
        payload.set("leaderboard", arr);

        String body = mapper.writeValueAsString(payload);
        System.out.println("Payload        : " + body);
        System.out.println("Submitted total: " + submittedTotal);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/quiz/submit"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        System.out.println("\n── Submission Response ────────────────────");
        System.out.println("HTTP Status    : " + resp.statusCode());
        System.out.println("Raw body       : " + resp.body());

        try {
            JsonNode result = mapper.readTree(resp.body());

            if (result.has("submittedTotal")) {
                System.out.println("Submitted Total: " + result.path("submittedTotal").asInt());
            }
            if (result.has("totalPollsMade")) {
                System.out.println("Total Polls Made: " + result.path("totalPollsMade").asInt());
            }
            if (result.has("attemptCount")) {
                System.out.println("Attempt Count  : " + result.path("attemptCount").asInt());
            }

            if (result.has("isCorrect")) {
                System.out.println("isCorrect      : " + result.path("isCorrect").asBoolean());
            }
            if (result.has("isIdempotent")) {
                System.out.println("isIdempotent   : " + result.path("isIdempotent").asBoolean());
            }
            if (result.has("expectedTotal")) {
                System.out.println("Expected Total : " + result.path("expectedTotal").asInt());
            }
            if (result.has("message")) {
                System.out.println("Message        : " + result.path("message").asText());
            }

            System.out.println("-------------------------------------------");

            boolean correct = result.has("isCorrect")
                    ? result.path("isCorrect").asBoolean()
                    : (resp.statusCode() == 200);

            if (correct) {
                System.out.println("\n✅ Submission accepted!");
            } else {
                System.out.println("\n❌ Incorrect — check raw body above for details.");
            }

        } catch (Exception ex) {
            System.out.println("Could not parse response JSON: " + ex.getMessage());
        }
    }
}

