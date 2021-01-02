package me.udintsev.otus.architect.eventing.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.udintsev.otus.architect.eventing.order.domain.Order;
import me.udintsev.otus.architect.eventing.order.domain.OrderItem;
import me.udintsev.otus.architect.eventing.order.domain.UserOrders;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderControllerTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    private String createOrderRequest(List<OrderItem> items, String fingerprint) throws JsonProcessingException {
        return objectMapper.writeValueAsString(new OrderController.CreateOrderRequest(items, fingerprint));
    }

    @Test
    void shouldCreateFirstOrderWithNoFingerprint() throws Exception {
        // Create
        var responseContent = mvc.perform(
                post(OrderController.API_ROOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "john.doe@example.com")
                        .content(createOrderRequest(
                                List.of(
                                        new OrderItem(10, 1),
                                        new OrderItem(11, 5)
                                ),
                                null)
                        )
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("id").isNumber())
                .andExpect(jsonPath("userId").value("john.doe@example.com"))
                .andExpect(jsonPath("status").value("created"))
                .andExpect(jsonPath("items").isArray())
                .andExpect(jsonPath("items.length()").value(2))
                .andExpect(jsonPath("items[0].itemId").isNumber())
                .andExpect(jsonPath("items[0].quantity").isNumber())
                .andReturn().getResponse().getContentAsByteArray();

        Order order = objectMapper.readValue(responseContent, Order.class);

        // Get
        mvc.perform(get(OrderController.API_ROOT + "/{orderId}", order.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("id").value(order.getId()))
                .andExpect(jsonPath("userId").value(order.getUserId()))
                .andExpect(jsonPath("items[0].itemId").value(Matchers.anyOf(
                        Matchers.equalTo(10), Matchers.equalTo(11))));

        // List
        mvc.perform(get(OrderController.API_ROOT)
                .header("X-User-Id", "john.doe@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("fingerprint").isString())
                .andExpect(jsonPath("orders").isArray())
                .andExpect(jsonPath("orders.length()").value(1))
                .andExpect(jsonPath("orders[0].id").value(order.getId()));
    }

    @Test
    void shouldCreateFirstOrderWithFingerprint() throws Exception {
        // Get fingerprint
        var fingerprint = mvc.perform(get(OrderController.API_ROOT + "/fingerprint")
                .header("X-User-Id", "john.doe@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isString())
                .andReturn().getResponse().getContentAsString();

        assertThat(fingerprint).isNotEmpty();

        // Create
        mvc.perform(
                post(OrderController.API_ROOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "john.doe@example.com")
                        .content(createOrderRequest(
                                List.of(
                                        new OrderItem(10, 1),
                                        new OrderItem(11, 5)
                                ),
                                fingerprint)
                        )
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void shouldCreateTwoOrders() throws Exception {
        // Create 1st order
        mvc.perform(
                post(OrderController.API_ROOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "john.doe@example.com")
                        .content(createOrderRequest(
                                List.of(
                                        new OrderItem(10, 1),
                                        new OrderItem(11, 5)
                                ),
                                null)
                        )
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Get fingerprint
        var fingerprint1 = mvc.perform(get(OrderController.API_ROOT + "/fingerprint")
                .header("X-User-Id", "john.doe@example.com"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();


        // Create 2nd order
        mvc.perform(
                post(OrderController.API_ROOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "john.doe@example.com")
                        .content(createOrderRequest(
                                List.of(
                                        new OrderItem(20, 2),
                                        new OrderItem(22, 3)
                                ),
                                fingerprint1)
                        )
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("id").isNumber())
                .andExpect(jsonPath("userId").value("john.doe@example.com"))
                .andExpect(jsonPath("status").value("created"))
                .andExpect(jsonPath("items").isArray())
                .andExpect(jsonPath("items.length()").value(2))
                .andExpect(jsonPath("items[0].itemId").isNumber())
                .andExpect(jsonPath("items[0].quantity").isNumber());
    }

    @Test
    void shouldRefuseCreating2ndOrderIfNoFingerprint() throws Exception {
        // Create 1st order
        mvc.perform(
                post(OrderController.API_ROOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "john.doe@example.com")
                        .content(createOrderRequest(
                                List.of(
                                        new OrderItem(10, 1),
                                        new OrderItem(11, 5)
                                ),
                                null)
                        )
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Create 2nd order
        mvc.perform(
                post(OrderController.API_ROOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "john.doe@example.com")
                        .content(createOrderRequest(
                                List.of(
                                        new OrderItem(20, 2),
                                        new OrderItem(22, 3)
                                ),
                                null)
                        )
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("message").value(Matchers.matchesPattern("fingerprint.*match")));
    }

    @Test
    void shouldRefuseCreating2ndOrderIfWrongFingerprint() throws Exception {
        // Create 1st order
        mvc.perform(
                post(OrderController.API_ROOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "john.doe@example.com")
                        .content(createOrderRequest(
                                List.of(
                                        new OrderItem(10, 1),
                                        new OrderItem(11, 5)
                                ),
                                null)
                        )
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Get fingerprint
        var fingerprint1 = mvc.perform(get(OrderController.API_ROOT + "/fingerprint")
                .header("X-User-Id", "john.doe@example.com"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var wrongFingerprint = "wrong";

        // Validate assumption
        assertThat(wrongFingerprint).isNotEqualTo(fingerprint1);

        // Create 2nd order
        mvc.perform(
                post(OrderController.API_ROOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "john.doe@example.com")
                        .content(createOrderRequest(
                                List.of(
                                        new OrderItem(20, 2),
                                        new OrderItem(22, 3)
                                ),
                                wrongFingerprint)
                        )
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("message").value(Matchers.matchesPattern("fingerprint.*match")));
    }

    @Test
    void shouldRefuseCreating3rdOrderIfPreviousFingerprint() throws Exception {
        // Create 1st order
        mvc.perform(
                post(OrderController.API_ROOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "john.doe@example.com")
                        .content(createOrderRequest(
                                List.of(
                                        new OrderItem(10, 1),
                                        new OrderItem(11, 5)
                                ),
                                null)
                        )
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Get fingerprint
        var fingerprint1 = mvc.perform(get(OrderController.API_ROOT + "/fingerprint")
                .header("X-User-Id", "john.doe@example.com"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Create 2nd order
        mvc.perform(
                post(OrderController.API_ROOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "john.doe@example.com")
                        .content(createOrderRequest(
                                List.of(
                                        new OrderItem(20, 2),
                                        new OrderItem(22, 3)
                                ),
                                fingerprint1)
                        )
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Create 3rd order
        mvc.perform(
                post(OrderController.API_ROOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "john.doe@example.com")
                        .content(createOrderRequest(
                                List.of(
                                        new OrderItem(31, 4),
                                        new OrderItem(32, 5)
                                ),
                                fingerprint1)
                        )
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("message").value(Matchers.matchesPattern("fingerprint.*match")));
    }

    @Test
    void shouldCreateThreeOrders() throws Exception {
        // Create 1st order
        var responseContent = mvc.perform(
                post(OrderController.API_ROOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "john.doe@example.com")
                        .content(createOrderRequest(
                                List.of(
                                        new OrderItem(10, 1),
                                        new OrderItem(11, 5)
                                ),
                                null)
                        )
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        Order order1 = objectMapper.readValue(responseContent, Order.class);

        // Get fingerprint
        var fingerprint1 = mvc.perform(get(OrderController.API_ROOT + "/fingerprint")
                .header("X-User-Id", "john.doe@example.com"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Create 2nd order
        responseContent = mvc.perform(
                post(OrderController.API_ROOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "john.doe@example.com")
                        .content(createOrderRequest(
                                List.of(
                                        new OrderItem(20, 2),
                                        new OrderItem(22, 3)
                                ),
                                fingerprint1)
                        )
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        Order order2 = objectMapper.readValue(responseContent, Order.class);

        // Get fingerprint
        var fingerprint2 = mvc.perform(get(OrderController.API_ROOT + "/fingerprint")
                .header("X-User-Id", "john.doe@example.com"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Create 3rd order
        responseContent = mvc.perform(
                post(OrderController.API_ROOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "john.doe@example.com")
                        .content(createOrderRequest(
                                List.of(
                                        new OrderItem(31, 4),
                                        new OrderItem(32, 5)
                                ),
                                fingerprint2)
                        )
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        Order order3 = objectMapper.readValue(responseContent, Order.class);

        // Create also an order for some other user
        mvc.perform(
                post(OrderController.API_ROOT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "jane.doe@example.com")
                        .content(createOrderRequest(
                                List.of(
                                        new OrderItem(41, 6),
                                        new OrderItem(42, 7)
                                ),
                                null)
                        )
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // List orders for user1
        responseContent = mvc.perform(get(OrderController.API_ROOT)
                .header("X-User-Id", "john.doe@example.com"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        UserOrders userOrders = objectMapper.readValue(responseContent, UserOrders.class);

        assertThat(userOrders).isNotNull();
        assertThat(userOrders.getOrders()).containsExactly(order1, order2, order3);
    }
}
