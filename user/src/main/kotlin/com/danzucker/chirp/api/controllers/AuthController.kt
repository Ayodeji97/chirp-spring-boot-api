package com.danzucker.chirp.api.controllers

import com.danzucker.chirp.api.dto.AuthenticatedUserDto
import com.danzucker.chirp.api.dto.LoginRequest
import com.danzucker.chirp.api.dto.RefreshRequest
import com.danzucker.chirp.api.dto.RegisterRequest
import com.danzucker.chirp.api.dto.UserDto
import com.danzucker.chirp.api.mappers.toAuthenticatedUserDto
import com.danzucker.chirp.api.mappers.toUserDto
import com.danzucker.chirp.service.auth.AuthService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {

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
}
