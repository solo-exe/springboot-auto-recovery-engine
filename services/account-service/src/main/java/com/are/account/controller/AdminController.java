package com.are.account.controller;

import com.are.account.dto.FundRequest;
import com.are.account.service.AdminService;
import com.are.common.dto.ApiResponse;
import com.are.common.exception.ForbiddenException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin-only endpoints requiring JWT with role = ADMIN.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/fund")
    public ResponseEntity<ApiResponse<Map<String, Object>>> fundAccount(
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody FundRequest request) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ForbiddenException("Only ADMIN users can fund accounts");
        }
        Map<String, Object> result = adminService.fundAccount(request);
        return ResponseEntity.ok(ApiResponse.success(result, "Account funded successfully"));
    }
}
