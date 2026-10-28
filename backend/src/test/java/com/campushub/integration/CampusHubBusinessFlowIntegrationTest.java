package com.campushub.integration;

import com.campushub.category.Category;
import com.campushub.category.CategoryRepository;
import com.campushub.notification.NotificationRepository;
import com.campushub.order.OrderRepository;
import com.campushub.review.ReviewRepository;
import com.campushub.task.TaskRepository;
import com.campushub.user.UserRepository;
import com.campushub.verification.VerificationCodeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CampusHubBusinessFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationCodeRepository verificationCodeRepository;

    private Integer reviewableCategoryId;

    @BeforeEach
    void resetDatabase() {
        reviewRepository.deleteAll();
        orderRepository.deleteAll();
        taskRepository.deleteAll();
        notificationRepository.deleteAll();
        userRepository.deleteAll();
        verificationCodeRepository.deleteAll();
        categoryRepository.deleteAll();

        Category category = Category.builder()
                .name("快递代取")
                .hasReview(true)
                .sortOrder(1)
                .build();
        reviewableCategoryId = categoryRepository.save(category).getId();
    }

    @Test
    void requesterCanPublishProviderCanAcceptRequesterCanCompleteAndReview() throws Exception {
        RegisteredUser requester = registerUser("张三", "20260001", "13800000001", "Password1!");
        RegisteredUser provider = registerUser("李四", "20260002", "13800000002", "Password1!");
        switchRole(provider.userId(), "provider");

        long taskId = publishTask(requester.token(), "帮取快递到宿舍", "请帮忙从校门口驿站取一个快递并送到宿舍楼下");

        mockMvc.perform(get("/v1/tasks")
                        .param("keyword", "快递")
                        .param("status", "published"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(taskId))
                .andExpect(jsonPath("$.data.items[0].status").value("published"));

        mockMvc.perform(get("/v1/tasks/{id}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(taskId))
                .andExpect(jsonPath("$.data.publisher.userId").value(requester.userId()))
                .andExpect(jsonPath("$.data.hasReview").value(true));

        long orderId = acceptTask(provider.token(), taskId)
                .path("data").path("orderId").asLong();

        mockMvc.perform(get("/v1/orders/task/{taskId}", taskId)
                        .header("Authorization", bearer(provider.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.taskStatus").value("in_progress"));

        mockMvc.perform(post("/v1/orders/{orderId}/complete", orderId)
                        .header("Authorization", bearer(requester.token()))
                        .header("X-User-Id", requester.userId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/v1/reviews/submit")
                        .header("Authorization", bearer(requester.token()))
                        .header("X-User-Id", requester.userId())
                        .param("orderId", String.valueOf(orderId))
                        .param("score", "5")
                        .param("comment", "服务很及时"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/v1/reviews/order/{orderId}", orderId)
                        .header("Authorization", bearer(requester.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(5))
                .andExpect(jsonPath("$.data.comment").value("服务很及时"));

        assertThat(notificationRepository.count()).isEqualTo(3);
        assertThat(userRepository.findById(provider.userId()).orElseThrow().getCreditScore()).isEqualTo(101);
    }

    @Test
    void protectedOrderEndpointRejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(post("/v1/orders/accept").param("taskId", "1"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void taskCreationRejectsInvalidInputBeforePersisting() throws Exception {
        RegisteredUser requester = registerUser("王五", "20260003", "13800000003", "Password1!");

        ObjectNode invalidTask = validTaskJson("短", "描述也太短");

        mockMvc.perform(post("/v1/tasks")
                        .header("Authorization", bearer(requester.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidTask.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(422))
                .andExpect(jsonPath("$.message").value("参数校验失败"));

        assertThat(taskRepository.count()).isZero();
    }

    @Test
    void secondProviderCannotAcceptTaskThatIsAlreadyInProgress() throws Exception {
        RegisteredUser requester = registerUser("赵六", "20260004", "13800000004", "Password1!");
        RegisteredUser firstProvider = registerUser("孙七", "20260005", "13800000005", "Password1!");
        RegisteredUser secondProvider = registerUser("周八", "20260006", "13800000006", "Password1!");
        switchRole(firstProvider.userId(), "provider");
        switchRole(secondProvider.userId(), "provider");

        long taskId = publishTask(requester.token(), "帮取教材到宿舍", "请帮忙从教学楼取一本教材并送到宿舍楼下");
        acceptTask(firstProvider.token(), taskId);

        mockMvc.perform(post("/v1/orders/accept")
                        .header("Authorization", bearer(secondProvider.token()))
                        .param("taskId", String.valueOf(taskId))
                        .param("paymentCode", "second-provider-pay-code.png"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("该需求不可接单"));

        assertThat(orderRepository.findByTaskId(taskId)).hasSize(1);
    }

    private RegisteredUser registerUser(String name, String studentId, String phone, String password) throws Exception {
        String code = sendVerificationCode(phone);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", name);
        body.put("studentId", studentId);
        body.put("phone", phone);
        body.put("password", password);
        body.put("smsCode", code);

        MvcResult registerResult = mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();

        long userId = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .path("data").path("userId").asLong();

        ObjectNode loginBody = objectMapper.createObjectNode();
        loginBody.put("account", studentId);
        loginBody.put("password", password);
        MvcResult loginResult = mockMvc.perform(post("/v1/auth/login/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andReturn();

        String loginToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data").path("token").asText();
        return new RegisteredUser(userId, loginToken);
    }

    private String sendVerificationCode(String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/verification/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phone", phone, "type", "REGISTER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").asText();
    }

    private void switchRole(Long userId, String role) throws Exception {
        mockMvc.perform(put("/v1/auth/role")
                        .header("X-User-Id", userId)
                        .param("role", role))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private long publishTask(String requesterToken, String title, String description) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/tasks")
                        .header("Authorization", bearer(requesterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTaskJson(title, description).toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("published"))
                .andExpect(jsonPath("$.data.categoryId").value(reviewableCategoryId))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private ObjectNode validTaskJson(String title, String description) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("title", title);
        body.put("description", description);
        body.put("categoryId", reviewableCategoryId);
        body.put("reward", new BigDecimal("6.50"));
        body.put("location", "3号宿舍楼下");
        body.put("deadline", LocalDateTime.now().plusDays(1).withNano(0).toString());
        return body;
    }

    private JsonNode acceptTask(String providerToken, long taskId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/orders/accept")
                        .header("Authorization", bearer(providerToken))
                        .param("taskId", String.valueOf(taskId))
                        .param("paymentCode", "provider-pay-code.png"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.orderStatus").value("in_progress"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record RegisteredUser(Long userId, String token) {
    }
}
