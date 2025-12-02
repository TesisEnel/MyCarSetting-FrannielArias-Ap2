package edu.ucne.loginapi.data.remote.repository

import edu.ucne.loginapi.data.dao.UserCarDao
import edu.ucne.loginapi.data.remote.Resource
import edu.ucne.loginapi.data.remote.dataSource.CarRemoteDataSource
import edu.ucne.loginapi.data.remote.mappers.toDomain
import edu.ucne.loginapi.data.remote.mappers.toEntity
import edu.ucne.loginapi.domain.model.UserCar
import edu.ucne.loginapi.domain.repository.CarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CarRepositoryImpl @Inject constructor(
    private val userCarDao: UserCarDao,
    private val remote: CarRemoteDataSource
) : CarRepository {

    override fun getCars(): Flow<List<UserCar>> =
        userCarDao.getCars().map { list -> list.map { it.toDomain() } }

    override suspend fun getCar(id: String): UserCar? {

        val intId = id.toIntOrNull() ?: return null
        return userCarDao.getCar(intId)?.toDomain()
    }

    override suspend fun addCar(car: UserCar): Resource<Unit> {
        return try {
            val existing = userCarDao.getCar(car.id)
            if (existing == null) {
                userCarDao.upsert(car.toEntity())
                Resource.Success(Unit)
            } else {
                Resource.Error("El vehículo ya existe")
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error al agregar vehículo")
        }
    }

    override suspend fun updateCar(car: UserCar): Resource<Unit> {
        return try {
            val existing = userCarDao.getCar(car.id)
            if (existing != null) {
                userCarDao.upsert(car.toEntity())
                Resource.Success(Unit)
            } else {
                Resource.Error("El vehículo no existe")
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error al actualizar vehículo")
        }
    }

    override suspend fun deleteCar(id: String): Resource<Unit> {
        return try {
            val intId = id.toIntOrNull() ?: return Resource.Error("ID inválido")
            userCarDao.delete(intId)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error al eliminar vehículo")
        }
    }

    override suspend fun setCurrentCar(id: String): Resource<Unit> {
        return try {
            val intId = id.toIntOrNull() ?: return Resource.Error("ID inválido")
            userCarDao.clearCurrent()
            userCarDao.setCurrent(intId)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error al cambiar vehículo actual")
        }
    }

    override suspend fun syncCars(): Resource<Unit> {
        return try {
            when (val result = remote.getCars()) {
                is Resource.Success -> {
                    val cars = result.data.orEmpty()
                    userCarDao.replaceAll(cars.map { it.toEntity() })
                    Resource.Success(Unit)
                }
                is Resource.Error -> {
                    Resource.Error(result.message ?: "Error al sincronizar vehículos")
                }
                is Resource.Loading -> Resource.Loading()
            }
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error de sincronización")
        }
    }

    override suspend fun pushPendingCars(): Resource<Unit> {
        return try {
            val pendingCars = userCarDao.getPendingSync()
            pendingCars.forEach { entity ->
                val car = entity.toDomain()
                when (remote.createCar(car)) {
                    is Resource.Success -> {
                        userCarDao.upsert(entity.copy(pendingSync = false))
                    }
                    is Resource.Error -> {
                    }
                    is Resource.Loading -> {}
                }
            }
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error al sincronizar cambios pendientes")
        }
    }
}