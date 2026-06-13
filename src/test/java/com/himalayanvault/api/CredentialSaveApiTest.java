package com.himalayanvault.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.himalayanvault.db.DatabaseManager;

@DisplayName("Credential Save API Tests")
class CredentialSaveApiTest {

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
    @DisplayName("POST /save stores credentials from extension payload")
    void saveCredentialFromExtension() throws Exception {
        String username = "credsuser_" + System.currentTimeMillis();
        String password = "SecurePass1!";

        HttpResponse<String> signup = post("/signup", gson.toJson(new SignupPayload(username, password)));
        assertEquals(201, signup.statusCode(), signup.body());

        HttpResponse<String> login = post("/login", gson.toJson(new LoginPayload(username, password)));
        assertEquals(200, login.statusCode(), login.body());
        String token = gson.fromJson(login.body(), JsonObject.class).get("token").getAsString();

        JsonObject saveBody = new JsonObject();
        saveBody.addProperty("siteUrl", "example.com");
        saveBody.addProperty("siteName", "Example");
        saveBody.addProperty("siteUsername", "alice@example.com");
        saveBody.addProperty("encryptedPassword", "plain-test-password");
        saveBody.addProperty("notes", "from extension");
        saveBody.addProperty("category", "Email");
        saveBody.addProperty("tags", "work, critical");
        saveBody.addProperty("favorite", true);

        HttpResponse<String> save = post("/save", saveBody.toString(), token);
        assertEquals(201, save.statusCode(), save.body());

        JsonObject saveJson = gson.fromJson(save.body(), JsonObject.class);
        assertTrue(saveJson.get("success").getAsBoolean());

        HttpResponse<String> credentials = get("/credentials?site=example.com", token);
        assertEquals(200, credentials.statusCode(), credentials.body());

        JsonObject credsJson = gson.fromJson(credentials.body(), JsonObject.class);
        assertTrue(credsJson.get("success").getAsBoolean());
        assertEquals(1, credsJson.getAsJsonArray("credentials").size());
        JsonObject credential = credsJson.getAsJsonArray("credentials").get(0).getAsJsonObject();
        assertEquals("plain-test-password",
                credential.get("encryptedPassword").getAsString());
        assertEquals("Email", credential.get("category").getAsString());
        assertEquals("work, critical", credential.get("tags").getAsString());
        assertTrue(credential.get("favorite").getAsBoolean());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return post(path, body, null);
    }

    private HttpResponse<String> post(String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(apiServer.getAddress() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiServer.getAddress() + path))
                .header("Authorization", "Bearer " + token)
                .GET()
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

    private static class LoginPayload {
        final String username;
        final String password;

        LoginPayload(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }
}
