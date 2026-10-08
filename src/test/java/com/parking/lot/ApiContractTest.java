package com.parking.lot;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parking.lot.model.VehicleType;
import com.parking.lot.service.EntryService;
import com.parking.lot.testsupport.AbstractIntegrationTest;
import com.parking.lot.web.dto.CheckInRequest;
import com.parking.lot.web.dto.TicketDto;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Task E7 - HTTP contract tests: every endpoint and every error code of architecture §8,
 * plus contract rules C1 (stable codes), C2 (uniform envelope), C4 (validation) and C8 (paging).
 */
@AutoConfigureMockMvc
class ApiContractTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntryService entryService;

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private TicketDto park(String plate, VehicleType type) {
        return entryService.checkIn(new CheckInRequest(plate, type, Integer.valueOf(1)));
    }

    // ------------------------------------------------------------------ #1 entry

    @Test
    @DisplayName("#1 POST /parking/entry -> 201 with the documented fields")
    void entryReturns201() throws Exception {
        mockMvc.perform(post("/api/v1/parking/entry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CheckInRequest("KA01AB1234", VehicleType.CAR, 1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketNo").value(org.hamcrest.Matchers.startsWith("PKT-")))
                .andExpect(jsonPath("$.plate").value("KA01AB1234"))
                .andExpect(jsonPath("$.vehicleType").value("CAR"))
                .andExpect(jsonPath("$.floorNo").value(1))
                .andExpect(jsonPath("$.spotLabel").value("F1-C1"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.entryTime").value(not(nullValue())));
    }

    @Test
    @DisplayName("#1 duplicate entry -> 409 with the uniform problem+json envelope (C1/C2)")
    void duplicateEntryReturns409Envelope() throws Exception {
        String body = json(new CheckInRequest("KA01AB1234", VehicleType.CAR, 1));
        mockMvc.perform(post("/api/v1/parking/entry").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/parking/entry").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_PARKED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.timestamp").value(not(nullValue())))
                .andExpect(jsonPath("$.traceId").value(not(nullValue())))
                .andExpect(header().string("X-Trace-Id", not(nullValue())));
    }

    @Test
    @DisplayName("#1 missing plate -> 400 VALIDATION_ERROR listing the field")
    void entryValidation() throws Exception {
        mockMvc.perform(post("/api/v1/parking/entry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleType\":\"CAR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.plate").value(not(nullValue())));
    }

    @Test
    @DisplayName("#1 unknown floor -> 404 FLOOR_NOT_FOUND")
    void entryUnknownFloor() throws Exception {
        mockMvc.perform(post("/api/v1/parking/entry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CheckInRequest("KA01AB1234", VehicleType.CAR, 99))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FLOOR_NOT_FOUND"));
    }

    @Test
    @DisplayName("#1 full lot -> 409 PARKING_FULL_FOR_TYPE")
    void entryFullForType() throws Exception {
        for (int i = 1; i <= 6; i++) {
            park("KA01BUS00" + i, VehicleType.BUS);
        }
        mockMvc.perform(post("/api/v1/parking/entry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CheckInRequest("KA01BUSX1", VehicleType.BUS, 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(anyOf(is("PARKING_FULL_FOR_TYPE"), is("PARKING_FULL"))));
    }

    // ------------------------------------------------------------------ #2 exit

    @Test
    @DisplayName("#2 POST /parking/exit -> 200 with the receipt fields")
    void exitReturns200() throws Exception {
        TicketDto ticket = park("KA01AB1234", VehicleType.CAR);
        Instant exitTime = ticket.getEntryTime().plus(45, ChronoUnit.MINUTES);

        mockMvc.perform(post("/api/v1/parking/exit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketNo\":\"" + ticket.getTicketNo()
                                + "\",\"exitTime\":\"" + exitTime + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketNo").value(ticket.getTicketNo()))
                .andExpect(jsonPath("$.amount").value(40.00))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.durationMinutes").value(45))
                .andExpect(jsonPath("$.rateCard").value("CAR-SLAB"))
                .andExpect(jsonPath("$.replay").value(false));
    }

    @Test
    @DisplayName("#2 unknown ticket -> 404 TICKET_NOT_FOUND")
    void exitUnknownTicket() throws Exception {
        mockMvc.perform(post("/api/v1/parking/exit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketNo\":\"PKT-20261007-0000000000\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
    }

    @Test
    @DisplayName("#2 second exit -> 409 TICKET_ALREADY_CLOSED, ?replay=true -> 200")
    void exitIsIdempotent() throws Exception {
        TicketDto ticket = park("KA01AB1234", VehicleType.CAR);
        String body = "{\"ticketNo\":\"" + ticket.getTicketNo()
                + "\",\"exitTime\":\"" + ticket.getEntryTime().plus(45, ChronoUnit.MINUTES) + "\"}";

        mockMvc.perform(post("/api/v1/parking/exit").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/parking/exit").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKET_ALREADY_CLOSED"));
        mockMvc.perform(post("/api/v1/parking/exit?replay=true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replay").value(true))
                .andExpect(jsonPath("$.amount").value(40.00));
    }

    @Test
    @DisplayName("#2 exit before entry -> 400 INVALID_EXIT_TIME")
    void exitBeforeEntry() throws Exception {
        TicketDto ticket = park("KA01AB1234", VehicleType.CAR);

        mockMvc.perform(post("/api/v1/parking/exit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketNo\":\"" + ticket.getTicketNo()
                                + "\",\"exitTime\":\"" + ticket.getEntryTime().minus(10, ChronoUnit.MINUTES) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EXIT_TIME"));
    }

    // ----------------------------------------------------------------- #3/#6 availability

    @Test
    @DisplayName("#3 GET /availability -> 200 with per-floor free/total maps")
    void availabilityReturns200() throws Exception {
        mockMvc.perform(get("/api/v1/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").value(not(nullValue())))
                .andExpect(jsonPath("$.lotVersion").isNumber())
                .andExpect(jsonPath("$.floors", hasSize(3)))
                .andExpect(jsonPath("$.floors[0].floorNo").value(1))
                .andExpect(jsonPath("$.floors[0].total.CAR").value(6))
                .andExpect(jsonPath("$.floors[0].free.CAR").value(6))
                .andExpect(jsonPath("$.floors[0].free.BUS").value(2));
    }

    @Test
    @DisplayName("#6 GET /floors/{n}/spots -> 200 listing, unknown floor -> 404")
    void spotListing() throws Exception {
        mockMvc.perform(get("/api/v1/floors/1/spots?status=FREE&type=CAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].spotType").value("CAR"))
                .andExpect(jsonPath("$[0].status").value("FREE"));

        mockMvc.perform(get("/api/v1/floors/99/spots"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FLOOR_NOT_FOUND"));
    }

    // ------------------------------------------------------------------ #4/#5 tickets

    @Test
    @DisplayName("#4 GET /parking/tickets/{ticketNo} -> 200 / 404")
    void ticketByNumber() throws Exception {
        TicketDto ticket = park("KA01AB1234", VehicleType.CAR);

        mockMvc.perform(get("/api/v1/parking/tickets/" + ticket.getTicketNo()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketNo").value(ticket.getTicketNo()))
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(get("/api/v1/parking/tickets/PKT-20261007-0000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
    }

    @Test
    @DisplayName("#5 GET /parking/tickets?plate=&status= filters and paginates (C8)")
    void ticketListing() throws Exception {
        park("KA01AB1234", VehicleType.CAR);
        park("KA01CD5678", VehicleType.CAR);

        mockMvc.perform(get("/api/v1/parking/tickets?status=OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/v1/parking/tickets?plate=ka01ab1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].plate").value("KA01AB1234"));

        mockMvc.perform(get("/api/v1/parking/tickets?status=OPEN&size=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/v1/parking/tickets?status=CLOSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ------------------------------------------------------------------ #7 quote

    @Test
    @DisplayName("#7 POST /parking/estimate-fee quotes the documented amount")
    void estimateFee() throws Exception {
        mockMvc.perform(post("/api/v1/parking/estimate-fee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleType\":\"CAR\",\"durationMinutes\":45}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleType").value("CAR"))
                .andExpect(jsonPath("$.amount").value(40.00))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.strategy").value("SLAB"));

        mockMvc.perform(post("/api/v1/parking/estimate-fee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleType\":\"CAR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ------------------------------------------------------------------ #8 ops

    @Test
    @DisplayName("#8 GET /health is exposed at the documented path")
    void healthEndpoint() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("C7: the API lives under the versioned base path")
    void unversionedPathIsNotServed() throws Exception {
        mockMvc.perform(get("/availability"))
                .andExpect(status().isNotFound());
    }
}
