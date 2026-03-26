package com.danzucker.chirp.api.mappers

import com.danzucker.chirp.api.dto.AuthenticatedUserDto
import com.danzucker.chirp.api.dto.UserDto
import com.danzucker.chirp.domain.model.AuthenticatedUser
import com.danzucker.chirp.domain.model.User

fun AuthenticatedUser.toAuthenticatedUserDto(): AuthenticatedUserDto {
    return AuthenticatedUserDto(
        user = user.toUserDto(),
        accessToken = accessToken,
        refreshToken = refreshToken
    )
}

fun User.toUserDto(): UserDto {
    return UserDto(
        id = id,
        email = email,
        username = username,
        hasVerifiedEmail = hasEmailVerified
    )
}
