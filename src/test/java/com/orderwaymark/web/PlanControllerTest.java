package com.orderwaymark.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PlanControllerTest {

    @Autowired
    MockMvc mvc;

    private org.springframework.test.web.servlet.ResultActions post(String json) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/plan")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void validScenarioRequest() throws Exception {
        post("{\"scenario\":\"one-box-wins\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.winner.totalCents").value(800))
                .andExpect(jsonPath("$.winner.assignment[0]").value("OTT"))
                .andExpect(jsonPath("$.winner.boxes[0].arrives").value("2026-11-13"))
                .andExpect(jsonPath("$.strategies", hasSize(3)))
                .andExpect(jsonPath("$.strategies[1].strategy").value("CHEAPEST_PER_ITEM"))
                .andExpect(jsonPath("$.strategies[1].plan.totalCents").value(1100))
                .andExpect(jsonPath("$.candidates", hasSize(4)));
    }

    @Test
    void validCustomRequest() throws Exception {
        String body = """
                {"deadline":"2026-11-13","items":["keyboard","mouse"],
                 "warehouses":[{"id":"MTL","name":"Montréal","stock":{"keyboard":1,"mouse":1}},
                               {"id":"OTT","name":"Ottawa","stock":{"keyboard":1,"mouse":1}}],
                 "quotes":[{"warehouse":"MTL","items":["keyboard"],"priceCents":500,"arrives":"2026-11-13"},
                           {"warehouse":"MTL","items":["mouse"],"priceCents":800,"arrives":"2026-11-13"},
                           {"warehouse":"MTL","items":["keyboard","mouse"],"priceCents":1200,"arrives":"2026-11-13"},
                           {"warehouse":"OTT","items":["keyboard"],"priceCents":700,"arrives":"2026-11-13"},
                           {"warehouse":"OTT","items":["mouse"],"priceCents":600,"arrives":"2026-11-13"},
                           {"warehouse":"OTT","items":["mouse","keyboard"],"priceCents":1400,"arrives":"2026-11-13"}]}
                """;
        post(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.winner.totalCents").value(1100))
                .andExpect(jsonPath("$.winner.boxCount").value(2))
                .andExpect(jsonPath("$.strategies[2].plan.totalCents").value(1200));
    }

    @Test
    void noPlanResult() throws Exception {
        post("{\"scenario\":\"all-late\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_ON_TIME_PLAN"))
                .andExpect(jsonPath("$.winner").doesNotExist())
                .andExpect(jsonPath("$.candidates[0].valid").value(false))
                .andExpect(jsonPath("$.candidates[0].rejections[0]", containsString("Late box")));
    }

    @Test
    void invalidRequests() throws Exception {
        post("{\"scenario\":\"nope\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Unknown scenario")));
        post("{\"items\":[\"keyboard\"]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("deadline")));
        post("{\"deadline\":\"2026-11-13\",\"items\":[\"keyboard\"],\"warehouses\":[{\"id\":\"MTL\"}],"
                + "\"quotes\":[{\"warehouse\":\"MTL\",\"items\":[\"keyboard\"],\"priceCents\":-1,\"arrives\":\"2026-11-13\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("priceCents")));
        post("{not json").andExpect(status().isBadRequest());
    }

    @Test
    void servesThePage() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
        mvc.perform(get("/index.html")).andExpect(status().isOk())
                .andExpect(content().string(containsString("One Box or Two?")));
    }
}
