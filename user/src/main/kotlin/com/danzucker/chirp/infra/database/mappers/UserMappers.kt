package com.danzucker.chirp.infra.database.mappers

import com.danzucker.chirp.domain.model.User
import com.danzucker.chirp.infra.database.entities.UserEntity

fun UserEntity.toUser(): User {
    return User(
        id = id!!,
        username = username,
        email = email,
        hasEmailVerified = hasVerifiedEmail
    )
}
