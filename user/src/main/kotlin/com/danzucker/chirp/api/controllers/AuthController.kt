package com.danzucker.chirp.api.controllers

import com.danzucker.chirp.api.dto.AuthenticatedUserDto
import com.danzucker.chirp.api.dto.ChangePasswordRequest
import com.danzucker.chirp.api.dto.EmailRequest
import com.danzucker.chirp.api.dto.LoginRequest
import com.danzucker.chirp.api.dto.RefreshRequest
import com.danzucker.chirp.api.dto.RegisterRequest
import com.danzucker.chirp.api.dto.ResetPasswordRequest
import com.danzucker.chirp.api.dto.UserDto
import com.danzucker.chirp.api.mappers.toAuthenticatedUserDto
import com.danzucker.chirp.api.mappers.toUserDto
import com.danzucker.chirp.service.auth.AuthService
import com.danzucker.chirp.service.auth.EmailVerificationService
import com.danzucker.chirp.service.auth.PasswordResetService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val emailVerificationService: EmailVerificationService,
    private val passwordResetService: PasswordResetService
) {

    @PostMapping("/register")
    fun register(
        @Valid @RequestBody body: RegisterRequest
    ): UserDto {
        return authService.register(
            email = body.email,
            username = body.username,
            password = body.password
        ).toUserDto()
    }

    @PostMapping("/login")
    fun login(
        @RequestBody body: LoginRequest
    ): AuthenticatedUserDto {
        return authService.login(
            email = body.email,
            password = body.password
        ).toAuthenticatedUserDto()
    }

    @PostMapping("/refresh")
    fun refresh(
        @RequestBody body: RefreshRequest
    ): AuthenticatedUserDto {
        return authService
            .refresh(body.refreshToken)
            .toAuthenticatedUserDto()
    }

    @PostMapping("/logout")
    fun logout(
        @RequestBody body: RefreshRequest
    ) {
        authService.logout(body.refreshToken)
    }

    @GetMapping("/verify")
    fun verifyEmail(
        @RequestParam token: String
    ) {
        emailVerificationService.verifyEmail(token)
    }

    @PostMapping("/forgot-password")
    fun forgotPassword(
        @Valid @RequestBody body: EmailRequest
    ) {
        passwordResetService.requestPasswordReset(body.email)
    }

    @PostMapping("/reset-password")
    fun resetPassword(
        @Valid @RequestBody body: ResetPasswordRequest
    ) {
        passwordResetService.resetPassword(
            token = body.token,
            newPassword = body.newPassword
        )
    }

    @PostMapping("/change-password")
    fun changePassword(
        @Valid @RequestBody body: ChangePasswordRequest
    ) {
        // TODO: Extract userId from JWT auth filter (implemented in a later section)
    }
}
