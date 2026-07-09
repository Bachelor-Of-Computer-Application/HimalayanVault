package com.himalayanvault.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.himalayanvault.db.DatabaseManager;

@DisplayName("Signup API Tests")
class SignupApiTest {

    private ApiServer apiServer;
    private final Gson gson = new Gson();

    @BeforeEach
    void setUp() throws Exception {
        DatabaseManager.getInstance();
        apiServer = new ApiServer();
        apiServer.start();
        Thread.sleep(200);
    }

    @AfterEach
    void tearDown() {
        if (apiServer != null) {
            apiServer.stop();
        }
    }

    @Test
    @DisplayName("POST /signup creates vault and returns 16 recovery words")
    void signupCreatesVault() throws Exception {
        String username = "testuser_" + System.currentTimeMillis();
        String body = gson.toJson(new SignupPayload(username, "SecurePass1!"));

        HttpResponse<String> response = post("/signup", body);
        assertEquals(201, response.statusCode(), response.body());

        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
        assertTrue(json.get("success").getAsBoolean());
        assertEquals(16, json.getAsJsonArray("recoveryWords").size());
        assertNotNull(DatabaseManager.getInstance().loadPasswordHash(username));
    }

    @Test
    @DisplayName("POST /signup rejects duplicate username")
    void signupRejectsDuplicate() throws Exception {
        String username = "dupuser_" + System.currentTimeMillis();
        String body = gson.toJson(new SignupPayload(username, "SecurePass1!"));

        HttpResponse<String> first = post("/signup", body);
        assertEquals(201, first.statusCode());

        String originalHash = DatabaseManager.getInstance().loadPasswordHash(username);
        assertNotNull(originalHash);

        String duplicateBody = gson.toJson(new SignupPayload(username, "DifferentPass2@"));
        HttpResponse<String> second = post("/signup", duplicateBody);
        assertEquals(409, second.statusCode());

        assertEquals(originalHash, DatabaseManager.getInstance().loadPasswordHash(username));
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiServer.getAddress() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static class SignupPayload {
        final String username;
        final String password;

        SignupPayload(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }
}
