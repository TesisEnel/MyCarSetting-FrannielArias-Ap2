package edu.ucne.loginapi.data.repository

import retrofit2.HttpException
import edu.ucne.loginapi.data.mappers.toDomain
import edu.ucne.loginapi.data.mappers.toDto
import edu.ucne.loginapi.data.remote.RemoteDataSource
import edu.ucne.loginapi.data.remote.Resource
import edu.ucne.loginapi.domain.model.Usuarios
import edu.ucne.loginapi.domain.repository.UsuariosRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

data class UsuariosRepositoryImpl @Inject constructor(
    private val remoteDataSource: RemoteDataSource
): UsuariosRepository {

    override fun getUsuario(id: Int): Flow<Resource<List<Usuarios>>> = flow {
        try {
            emit(Resource.Loading<List<Usuarios>>())
            val usuariosDto = remoteDataSource.getUsuario(id)
            val usuarios = usuariosDto.map { it.toDomain() }
            emit(Resource.Success(usuarios))
        } catch (e: HttpException) {
            emit(Resource.Error("Error de servidor: ${e.message}"))
        } catch (e: Exception) {
            emit(Resource.Error("Error desconocido: ${e.localizedMessage}"))
        }
    }

    override suspend fun saveUsuarios(usuarios: Usuarios): Resource<Unit> {
        return try {
            val dto = usuarios.toDto()
            remoteDataSource.saveUsuarios(dto)
            Resource.Success(Unit)
        } catch (e: HttpException) {
            Resource.Error("Error de servidor: ${e.message}")
        } catch (e: Exception) {
            Resource.Error("Error desconocido: ${e.localizedMessage}")
        }
    }

    override fun getUsuarios(): Flow<Resource<List<Usuarios>>> = flow {
        try {
            emit(Resource.Loading())
            val usuariosDto = remoteDataSource.getUsuarios()
            val usuarios = usuariosDto.map { it.toDomain() }
            emit(Resource.Success(usuarios))
        } catch (e: HttpException) {
            emit(Resource.Error("Error de servidor: ${e.message}"))
        } catch (e: Exception) {
            emit(Resource.Error("Error desconocido: ${e.localizedMessage}"))
        }
    }
    override suspend fun updateUsuarios(usuarios: Usuarios): Resource<Unit> {
        return try {
            val usuariosDto = usuarios.toDto()
            remoteDataSource.updateUsuarios(usuariosDto)
            Resource.Success(Unit)
        } catch (e: HttpException) {
            Resource.Error("Error de servidor: ${e.message}")
        } catch (e: Exception) {
            Resource.Error("Error desconocido: ${e.localizedMessage}")
        }
    }
}