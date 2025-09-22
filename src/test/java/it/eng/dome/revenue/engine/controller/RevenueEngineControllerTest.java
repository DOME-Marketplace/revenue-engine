package it.eng.dome.revenue.engine.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import it.eng.dome.revenue.engine.RevenueEngineApplication;

import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(classes = RevenueEngineApplication.class)
@AutoConfigureMockMvc
public class RevenueEngineControllerTest {

    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private BuildProperties buildProperties;

    @Test
    public void shouldReturnExpectedMessage() throws Exception {

        mockMvc.perform(get("/revenue/info").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(buildProperties.getVersion()))
            .andExpect(jsonPath("$.name").value(buildProperties.getName()));
    }
}
