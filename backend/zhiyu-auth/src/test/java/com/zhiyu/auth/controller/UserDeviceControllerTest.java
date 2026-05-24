package com.zhiyu.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zhiyu.auth.dto.DeviceDto;
import com.zhiyu.auth.service.DeviceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserDeviceControllerTest {

    @Mock
    private DeviceService deviceService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private UserDeviceController userDeviceController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userDeviceController).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(authentication.getPrincipal()).thenReturn("1001");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── List Devices ───────────────────────────────────────────

    @Test
    void shouldReturnEmptyListWhenNoDevices() throws Exception {
        when(deviceService.listDevices(eq(1001L), isNull()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/auth/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber());

        verify(deviceService).listDevices(eq(1001L), isNull());
    }

    @Test
    void shouldReturnDeviceListWhenDevicesExist() throws Exception {
        LocalDateTime now = LocalDateTime.of(2025, 1, 1, 12, 0);
        DeviceDto device = DeviceDto.builder()
                .id(1L)
                .deviceId("device-uuid-1")
                .deviceName("iPhone 15")
                .platform("IOS")
                .trusted(true)
                .lastActiveAt(now)
                .current(true)
                .build();

        when(deviceService.listDevices(eq(1001L), isNull()))
                .thenReturn(List.of(device));

        mockMvc.perform(get("/api/v1/auth/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].deviceId").value("device-uuid-1"))
                .andExpect(jsonPath("$.data[0].deviceName").value("iPhone 15"))
                .andExpect(jsonPath("$.data[0].platform").value("IOS"))
                .andExpect(jsonPath("$.data[0].trusted").value(true))
                .andExpect(jsonPath("$.data[0].current").value(true))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber());

        verify(deviceService).listDevices(eq(1001L), isNull());
    }

    @Test
    void shouldPassCurrentDeviceIdWhenHeaderPresent() throws Exception {
        String currentDeviceId = "current-device-uuid";
        when(deviceService.listDevices(eq(1001L), eq(currentDeviceId)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/auth/devices")
                        .header("X-Device-Id", currentDeviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());

        verify(deviceService).listDevices(eq(1001L), eq(currentDeviceId));
    }

    // ── Kick Device ────────────────────────────────────────────

    @Test
    void shouldKickDeviceWhenValidId() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/devices/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber());

        verify(deviceService).kickDevice(1001L, 1L);
    }

    @Test
    void shouldKickDeviceWhenLargeId() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/devices/99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"));

        verify(deviceService).kickDevice(1001L, 99999L);
    }

    // ── Trust Device ───────────────────────────────────────────

    @Test
    void shouldTrustDeviceWhenValidId() throws Exception {
        mockMvc.perform(put("/api/v1/auth/devices/1/trust"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber());

        verify(deviceService).trustDevice(1001L, 1L);
    }

    @Test
    void shouldToggleTrustDevice() throws Exception {
        mockMvc.perform(put("/api/v1/auth/devices/5/trust"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(deviceService).trustDevice(1001L, 5L);
    }
}
