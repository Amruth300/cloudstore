package com.cloudstore.integration;

import com.cloudstore.config.TestCacheConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end security test exercising the real filter chain, controllers and DB
 * (H2 in-memory). Verifies the core promise of the system: a customer can never
 * read, modify or delete another customer's private files - only owners and
 * explicitly-shared users can.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCacheConfig.class)
@EmbeddedKafka(partitions = 1, topics = {"file.uploaded", "file.deleted", "file.shared", "file.version.created"})
class FileOwnershipSecurityIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String userAToken;
    private String userAEmail;
    private String userBToken;
    private String userBEmail;

    @BeforeEach
    void setUp() throws Exception {
        userAEmail = "usera-" + UUID.randomUUID() + "@example.com";
        userBEmail = "userb-" + UUID.randomUUID() + "@example.com";
        userAToken = registerAndLogin(userAEmail);
        userBToken = registerAndLogin(userBEmail);
    }

    private String registerAndLogin(String email) throws Exception {
        String body = """
                {"email":"%s","fullName":"Test User","password":"password123"}
                """.formatted(email);

        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private String uploadFileAsUserA() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "private.txt", "text/plain", "top secret".getBytes());

        String response = mockMvc.perform(multipart("/api/v1/files")
                        .file(file)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asText();
    }

    @Test
    void userB_cannotViewMetadataOf_userAsPrivateFile() throws Exception {
        String fileId = uploadFileAsUserA();

        mockMvc.perform(get("/api/v1/files/{id}", fileId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void userB_cannotDownload_userAsPrivateFile() throws Exception {
        String fileId = uploadFileAsUserA();

        mockMvc.perform(get("/api/v1/files/{id}/download", fileId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void userB_cannotDelete_userAsPrivateFile() throws Exception {
        String fileId = uploadFileAsUserA();

        mockMvc.perform(delete("/api/v1/files/{id}", fileId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void userB_cannotRename_userAsPrivateFile() throws Exception {
        String fileId = uploadFileAsUserA();

        mockMvc.perform(patch("/api/v1/files/{id}/rename", fileId)
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType("application/json")
                        .content("{\"name\":\"hacked.txt\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void userA_canAccessOwnFile() throws Exception {
        String fileId = uploadFileAsUserA();

        mockMvc.perform(get("/api/v1/files/{id}", fileId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("private.txt"));
    }

    @Test
    void unauthenticatedRequest_isRejected() throws Exception {
        String fileId = uploadFileAsUserA();

        mockMvc.perform(get("/api/v1/files/{id}", fileId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userB_gainsReadAccess_onlyAfterExplicitShare() throws Exception {
        String fileId = uploadFileAsUserA();

        // Before sharing: forbidden
        mockMvc.perform(get("/api/v1/files/{id}", fileId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden());

        // Owner shares the file with user B (VIEW permission)
        String shareBody = """
                {"userEmail":"%s","permission":"VIEW"}
                """.formatted(userBEmail);

        mockMvc.perform(post("/api/v1/files/{id}/shares", fileId)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType("application/json")
                        .content(shareBody))
                .andExpect(status().isCreated());

        // After sharing: user B can now read metadata...
        mockMvc.perform(get("/api/v1/files/{id}", fileId)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isOk());

        // ...but still cannot rename (VIEW is read-only, not EDIT)
        mockMvc.perform(patch("/api/v1/files/{id}/rename", fileId)
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType("application/json")
                        .content("{\"name\":\"hacked.txt\"}"))
                .andExpect(status().isForbidden());
    }
}
