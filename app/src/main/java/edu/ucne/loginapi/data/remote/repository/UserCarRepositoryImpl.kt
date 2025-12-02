package edu.ucne.loginapi.data.remote.repository

import edu.ucne.loginapi.data.dao.UserCarDao
import edu.ucne.loginapi.data.remote.Resource
import edu.ucne.loginapi.data.remote.dataSource.CarRemoteDataSource
import edu.ucne.loginapi.data.remote.mappers.toDomain
import edu.ucne.loginapi.data.remote.mappers.toEntity
import edu.ucne.loginapi.domain.model.UserCar
import edu.ucne.loginapi.domain.repository.UserCarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class UserCarRepositoryImpl @Inject constructor(
    private val dao: UserCarDao,
    private val remoteDataSource: CarRemoteDataSource
) : UserCarRepository {

    override fun observeCurrentCar(): Flow<UserCar?> =
        dao.getCars().map { list ->
            list.firstOrNull { it.isCurrent }?.toDomain()
        }

    override fun observeCars(): Flow<List<UserCar>> =
        dao.getCars().map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun getCurrentCar(): UserCar? =
        dao.getCurrentCar()?.toDomain()

    override suspend fun getCarById(id: Int): UserCar? =
        dao.getCar(id)?.toDomain()

    override suspend fun upsertCar(car: UserCar): Resource<UserCar> {
        return try {
            if (car.id == 0) {
                // Crear nuevo en el servidor
                when (val result = remoteDataSource.createCar(car)) {
                    is Resource.Success -> {
                        val serverCar = result.data!!
                        dao.upsert(serverCar.toEntity())
                        Resource.Success(serverCar)
                    }
                    is Resource.Error -> {
                        // Guardar localmente si falla
                        dao.upsert(car.toEntity().copy(pendingSync = true))
                        Resource.Error(
                            "Guardado localmente. ${result.message}",
                            car
                        )
                    }
                    else -> Resource.Loading()
                }
            } else {
                // Actualizar existente
                when (val result = remoteDataSource.updateCar(car)) {
                    is Resource.Success -> {
                        dao.upsert(car.toEntity())
                        Resource.Success(car)
                    }
                    is Resource.Error -> {
                        dao.upsert(car.toEntity().copy(pendingSync = true))
                        Resource.Error(
                            "Guardado localmente. ${result.message}",
                            car
                        )
                    }
                    else -> Resource.Loading()
                }
            }
        } catch (e: Exception) {
            Resource.Error(
                e.localizedMessage ?: "Error al guardar",
                car
            )
        }
    }

    override suspend fun setCurrentCar(id: Int): Resource<Unit> {
        return try {
            dao.clearCurrent()
            dao.setCurrent(id)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error al cambiar vehículo actual")
        }
    }

    override suspend fun deleteCar(id: Int): Resource<Unit> {
        return try {
            when (val result = remoteDataSource.deleteCar(id)) {
                is Resource.Success -> {
                    dao.delete(id)
                    Resource.Success(Unit)
                }
                is Resource.Error -> {
                    dao.delete(id)
                    Resource.Error("Eliminado localmente. ${result.message}")
                }
                else -> Resource.Loading()
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error al eliminar")
        }
    }
}