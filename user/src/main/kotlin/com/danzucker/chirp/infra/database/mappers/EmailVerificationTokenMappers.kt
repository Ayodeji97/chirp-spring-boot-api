package com.danzucker.chirp.infra.database.mappers

import com.danzucker.chirp.domain.model.EmailVerificationToken
import com.danzucker.chirp.infra.database.entities.EmailVerificationTokenEntity

fun EmailVerificationTokenEntity.toEmailVerificationToken(): EmailVerificationToken {
    return EmailVerificationToken(
        id = id,
        token = token,
        user = user.toUser()
    )
}
