package com.danzucker.chirp.service.auth

import com.danzucker.chirp.domain.exception.UserAlreadyExistsException
import com.danzucker.chirp.domain.model.User
import com.danzucker.chirp.infra.database.entities.UserEntity
import com.danzucker.chirp.infra.database.mappers.toUser
import com.danzucker.chirp.infra.database.repositories.UserRepository
import com.danzucker.chirp.infra.security.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {

    fun register(email: String, username: String, password: String): User {
        val user = userRepository.findByEmailOrUsername(
            email = email.trim(),
            username = username.trim()
        )
        if (user != null) {
            throw UserAlreadyExistsException()
        }

        val savedUser = userRepository.save(
            UserEntity(
                email = email.trim(),
                username = username.trim(),
                hashedPassword = passwordEncoder.encode(password)
            )
        ).toUser()

        return savedUser
    }
}
