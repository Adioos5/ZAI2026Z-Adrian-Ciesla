package pl.zai.backend;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ApiContractTest {

    private static final String SERIES_JSON = """
            {"name":"Temperatura","minValue":-20,"maxValue":40,"color":"#1f77b4","unit":"°C"}""";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void health() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void loginLogoutAndPasswordChange() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        String token = login("admin");
        mvc.perform(put("/api/auth/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"bad\",\"newPassword\":\"newpassword\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/auth/password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"admin\",\"newPassword\":\"newpassword\"}"))
                .andExpect(status().isNoContent());
        login("newpassword");

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void seriesCrudAndValidation() throws Exception {
        mvc.perform(post("/api/series").contentType(MediaType.APPLICATION_JSON).content(SERIES_JSON))
                .andExpect(status().isUnauthorized());

        String token = login("admin");
        mvc.perform(post("/api/series").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"minValue\":5,\"maxValue\":5,\"color\":\"#000000\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/series").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType());

        long seriesId = createSeries(token);
        mvc.perform(get("/api/series/" + seriesId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.color").value("#1f77b4"));
        mvc.perform(get("/api/series").accept(MediaType.APPLICATION_XML)).andExpect(status().isNotAcceptable());
        mvc.perform(put("/api/series/" + seriesId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nowa\",\"minValue\":0,\"maxValue\":10,\"color\":\"#ff0000\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Nowa"));
        mvc.perform(delete("/api/series/" + seriesId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/series/" + seriesId)).andExpect(status().isNotFound());
    }

    @Test
    void sensorSubmitsMeasurements() throws Exception {
        String token = login("admin");
        long seriesId = createSeries(token);

        mvc.perform(post("/api/sensors").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"S\",\"seriesId\":999}"))
                .andExpect(status().isUnprocessableEntity());
        MvcResult registered = mvc.perform(post("/api/sensors").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Czujnik 1\",\"seriesId\":" + seriesId + "}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn();
        JsonNode sensor = json.readTree(registered.getResponse().getContentAsString());
        String apiKey = sensor.get("apiKey").asText();
        long sensorId = sensor.get("id").asLong();

        mvc.perform(get("/api/sensors/" + sensorId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.apiKey").doesNotExist());

        // Admin token alone is not enough to submit.
        mvc.perform(post("/api/measurements").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":21.5}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/measurements").header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":100}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/measurements").header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":1,\"timestamp\":\"2999-01-01T00:00:00Z\"}"))
                .andExpect(status().isUnprocessableEntity());

        submit(apiKey, "{\"value\":21.5,\"timestamp\":\"2026-10-15T08:30:00Z\"}");
        submit(apiKey, "{\"value\":22.5,\"timestamp\":\"2026-10-15T10:30:00+02:00\"}");
        MvcResult latest = submit(apiKey, "{\"value\":23.0,\"timestamp\":\"2026-10-15T09:00:00Z\"}");
        long measurementId = json.readTree(latest.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(get("/api/measurements")
                        .param("series", String.valueOf(seriesId))
                        .param("from", "2026-10-15T08:30:00Z")
                        .param("to", "2026-10-15T09:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].value").value(21.5))
                .andExpect(jsonPath("$[2].value").value(23.0));
        mvc.perform(get("/api/measurements").param("sort", "-timestamp").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].value").value(23.0));
        mvc.perform(get("/api/measurements").param("limit", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/measurements/" + measurementId)).andExpect(status().isOk());
        mvc.perform(delete("/api/measurements/" + measurementId)).andExpect(status().isMethodNotAllowed());

        // Narrowing the range below stored values conflicts.
        mvc.perform(put("/api/series/" + seriesId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"T\",\"minValue\":0,\"maxValue\":22,\"color\":\"#1f77b4\"}"))
                .andExpect(status().isConflict());

        // Unregistering revokes the key but keeps the data.
        mvc.perform(delete("/api/sensors/" + sensorId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/measurements").header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":21}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/measurements/" + measurementId)).andExpect(status().isOk());
    }

    private String login(String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private long createSeries(String token) throws Exception {
        MvcResult result = mvc.perform(post("/api/series").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(SERIES_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private MvcResult submit(String apiKey, String body) throws Exception {
        return mvc.perform(post("/api/measurements").header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
    }
}
