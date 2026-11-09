package com.amandadamata.ticketing.api;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.amandadamata.ticketing.support.PostgresTest;
import com.jayway.jsonpath.JsonPath;

/** The HTTP contract: status codes, headers and problem details, end to end. */
@AutoConfigureMockMvc
class BookingApiTest extends PostgresTest {

    private static final MediaType PROBLEM = MediaType.APPLICATION_PROBLEM_JSON;

    @Autowired
    private MockMvc mvc;

    private ResultActions postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long createEvent(int capacity) throws Exception {
        String body = postJson("/api/events", """
                {"name": "Jazz Night", "venue": "Blue Room", "startsAt": "2026-11-27T21:00:00Z", "capacity": %d}
                """.formatted(capacity))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String hold(long eventId, int quantity) throws Exception {
        String body = postJson("/api/events/" + eventId + "/reservations",
                "{\"customerEmail\": \"ana@example.com\", \"quantity\": " + quantity + "}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void createsAndListsEvents() throws Exception {
        long id = createEvent(100);

        mvc.perform(get("/api/events/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jazz Night"))
                .andExpect(jsonPath("$.availableSeats").value(100))
                .andExpect(jsonPath("$.startsAt").value("2026-11-27T21:00:00Z"));
        mvc.perform(get("/api/events").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void createEventReturnsLocation() throws Exception {
        postJson("/api/events", """
                {"name": "Jazz Night", "venue": "Blue Room", "startsAt": "2026-11-27T21:00:00Z", "capacity": 5}
                """)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/events/1")));
    }

    @Test
    void invalidEventListsEveryBadField() throws Exception {
        postJson("/api/events", "{\"name\": \"\", \"capacity\": 0}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.name").exists())
                .andExpect(jsonPath("$.fields.venue").exists())
                .andExpect(jsonPath("$.fields.startsAt").exists())
                .andExpect(jsonPath("$.fields.capacity").exists());
    }

    @Test
    void eventInThePastIsRejected() throws Exception {
        postJson("/api/events", """
                {"name": "Old show", "venue": "Blue Room", "startsAt": "2026-01-01T21:00:00Z", "capacity": 5}
                """)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("EVENT_IN_THE_PAST"));
    }

    @Test
    void holdConfirmAndCancel() throws Exception {
        long eventId = createEvent(10);
        String id = hold(eventId, 3);

        mvc.perform(get("/api/events/" + eventId)).andExpect(jsonPath("$.availableSeats").value(7));
        mvc.perform(post("/api/reservations/" + id + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(post("/api/reservations/" + id + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/api/events/" + eventId)).andExpect(jsonPath("$.availableSeats").value(10));
    }

    @Test
    void idempotencyKeyReplaysTheFirstResponse() throws Exception {
        long eventId = createEvent(10);
        String json = "{\"customerEmail\": \"ana@example.com\", \"quantity\": 2}";
        String url = "/api/events/" + eventId + "/reservations";

        String first = mvc.perform(post(url).header("Idempotency-Key", "order-42")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post(url).header("Idempotency-Key", "order-42")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value((String) JsonPath.read(first, "$.id")));
        mvc.perform(post(url).header("Idempotency-Key", "order-42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerEmail\": \"ana@example.com\", \"quantity\": 3}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        mvc.perform(get("/api/events/" + eventId)).andExpect(jsonPath("$.availableSeats").value(8));
    }

    @Test
    void malformedIdempotencyKeyIsRejected() throws Exception {
        long eventId = createEvent(10);

        mvc.perform(post("/api/events/" + eventId + "/reservations").header("Idempotency-Key", "has spaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerEmail\": \"ana@example.com\", \"quantity\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void soldOutAnswersConflictWithSeatsLeft() throws Exception {
        long eventId = createEvent(2);

        postJson("/api/events/" + eventId + "/reservations",
                "{\"customerEmail\": \"ana@example.com\", \"quantity\": 3}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_SEATS"))
                .andExpect(jsonPath("$.availableSeats").value(2))
                .andExpect(jsonPath("$.detail").value("You asked for 3 seat(s), but only 2 are left."));
    }

    @Test
    void tooManySeatsInOneReservationIsInvalid() throws Exception {
        long eventId = createEvent(50);

        postJson("/api/events/" + eventId + "/reservations",
                "{\"customerEmail\": \"not-an-email\", \"quantity\": 11}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.customerEmail").exists())
                .andExpect(jsonPath("$.fields.quantity").exists());
    }

    @Test
    void confirmingTooLateExplainsWhatHappened() throws Exception {
        long eventId = createEvent(10);
        String id = hold(eventId, 4);
        clock.advance(Duration.ofMinutes(11));

        mvc.perform(post("/api/reservations/" + id + "/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));
        mvc.perform(get("/api/reservations/" + id)).andExpect(jsonPath("$.status").value("EXPIRED"));
        mvc.perform(get("/api/events/" + eventId)).andExpect(jsonPath("$.availableSeats").value(10));
    }

    @Test
    void unknownIdsAnswerNotFound() throws Exception {
        mvc.perform(get("/api/events/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
        mvc.perform(get("/api/reservations/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void healthAndApiDocsAreAvailable() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths").exists());
    }
}
