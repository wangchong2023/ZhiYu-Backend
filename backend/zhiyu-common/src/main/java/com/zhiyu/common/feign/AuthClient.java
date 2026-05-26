package com.zhiyu.common.feign;

import com.zhiyu.ufp.common.annotation.UfpClient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Feign client for inter-service auth operations.
 * Used by admin-service to query user/role data from auth-service.
 *
 * <p>In monolith mode, this interface is registered but the actual
 * calls go through local service injection. After microservice split,
 * {@code @FeignClient} will route calls to the remote auth service.
 */
@UfpClient(name = "auth", path = "/api/v1/auth")
@FeignClient(name = "zhiyu-auth", path = "/api/v1/auth", fallbackFactory = AuthClientFallbackFactory.class)
public interface AuthClient {

    @GetMapping("/admin/users/{userId}")
    Map<String, Object> getUserById(@PathVariable Long userId);

    @GetMapping("/admin/users/by-username")
    Map<String, Object> getUserByUsername(@RequestParam String username);

    @GetMapping("/admin/roles/{roleId}")
    Map<String, Object> getRoleById(@PathVariable Long roleId);
}
