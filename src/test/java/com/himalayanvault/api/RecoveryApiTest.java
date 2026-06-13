package com.himalayanvault.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.himalayanvault.db.DatabaseManager;

@DisplayName("Recovery API Tests")
class RecoveryApiTest {

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
    @DisplayName("Recovery verify allows password reset and login with new password")
    void recoveryVerifyResetAndLogin() throws Exception {
        String username = "recovery_" + System.currentTimeMillis();
        String oldPassword = "SecurePass1!";
        String newPassword = "NewSecurePass1!";

        HttpResponse<String> signup = post("/signup", gson.toJson(new SignupPayload(username, oldPassword)));
        assertEquals(201, signup.statusCode(), signup.body());

        JsonObject signupJson = gson.fromJson(signup.body(), JsonObject.class);
        List<String> recoveryWords = gson.fromJson(
                signupJson.getAsJsonArray("recoveryWords"),
                new TypeToken<List<String>>() {
                }.getType());

        HttpResponse<String> verify = post("/recovery/verify",
                gson.toJson(new RecoveryVerifyPayload(username, recoveryWords)));
        assertEquals(200, verify.statusCode(), verify.body());
        assertTrue(gson.fromJson(verify.body(), JsonObject.class).get("success").getAsBoolean());

        HttpResponse<String> reset = post("/recovery/reset",
                gson.toJson(new RecoveryResetPayload(username, newPassword)));
        assertEquals(200, reset.statusCode(), reset.body());

        HttpResponse<String> oldLogin = post("/login", gson.toJson(new LoginPayload(username, oldPassword)));
        assertEquals(401, oldLogin.statusCode(), oldLogin.body());

        HttpResponse<String> newLogin = post("/login", gson.toJson(new LoginPayload(username, newPassword)));
        assertEquals(200, newLogin.statusCode(), newLogin.body());
        assertTrue(gson.fromJson(newLogin.body(), JsonObject.class).get("success").getAsBoolean());
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

    private static class LoginPayload {
        final String username;
        final String password;

        LoginPayload(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    private static class RecoveryVerifyPayload {
        final String username;
        final List<String> words;

        RecoveryVerifyPayload(String username, List<String> words) {
            this.username = username;
            this.words = words;
        }
    }

    private static class RecoveryResetPayload {
        final String username;
        final String newPassword;

        RecoveryResetPayload(String username, String newPassword) {
            this.username = username;
            this.newPassword = newPassword;
        }
    }
}
