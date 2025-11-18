package edu.ucne.loginapi.data.mappers

import edu.ucne.loginapi.data.remote.dto.UsuariosDto
import edu.ucne.loginapi.domain.model.Usuarios

data class UsuariosMappers(
    val isValid: Boolean,
    val error: String
)

fun UsuariosDto.toDomain() = Usuarios(
    usuarioId = usuarioId,
    userName = userName,
    password = password
)

fun Usuarios.toDto() = UsuariosDto(
    usuarioId = usuarioId,
    userName = userName,
    password = password
)