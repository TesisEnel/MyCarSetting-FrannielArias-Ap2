package edu.ucne.loginapi.data.remote.repository

import edu.ucne.loginapi.data.dao.MaintenanceHistoryDao
import edu.ucne.loginapi.data.dao.MaintenanceTaskDao
import edu.ucne.loginapi.data.remote.Resource
import edu.ucne.loginapi.data.remote.dataSource.MaintenanceRemoteDataSource
import edu.ucne.loginapi.data.remote.mappers.toDomain
import edu.ucne.loginapi.data.remote.mappers.toEntity
import edu.ucne.loginapi.domain.model.MaintenanceHistory
import edu.ucne.loginapi.domain.model.MaintenanceStatus
import edu.ucne.loginapi.domain.model.MaintenanceTask
import edu.ucne.loginapi.domain.repository.MaintenanceHistoryRepository
import edu.ucne.loginapi.domain.repository.MaintenanceRepository
import edu.ucne.loginapi.domain.repository.MaintenanceTaskRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MaintenanceRepositoryImpl @Inject constructor(
    private val taskDao: MaintenanceTaskDao,
    private val historyDao: MaintenanceHistoryDao,
    private val remote: MaintenanceRemoteDataSource
) : MaintenanceRepository, MaintenanceTaskRepository, MaintenanceHistoryRepository {

    override fun observeTasksForCar(carId: Int): Flow<List<MaintenanceTask>> =
        taskDao.observeTasksForCar(carId).map { list -> list.map { it.toDomain() } }

    override fun observeUpcomingTasksForCar(carId: Int): Flow<List<MaintenanceTask>> =
        observeTasksForCar(carId).map { tasks ->
            val now = System.currentTimeMillis()
            tasks.filter { task ->
                task.status != MaintenanceStatus.COMPLETED &&
                        (task.dueDateMillis == null || task.dueDateMillis >= now)
            }
        }

    override fun observeOverdueTasksForCar(carId: Int): Flow<List<MaintenanceTask>> =
        observeTasksForCar(carId).map { tasks ->
            val now = System.currentTimeMillis()
            tasks.filter { task ->
                task.status != MaintenanceStatus.COMPLETED &&
                        task.dueDateMillis != null &&
                        task.dueDateMillis < now
            }
        }

    override suspend fun getTaskById(id: Int): MaintenanceTask? =
        taskDao.getTaskById(id)?.toDomain()

    override suspend fun createTaskLocal(task: MaintenanceTask): Resource<MaintenanceTask> {
        taskDao.upsert(task.toEntity())
        return Resource.Success(task)
    }

    override suspend fun updateTaskLocal(task: MaintenanceTask): Resource<MaintenanceTask> {
        taskDao.upsert(task.toEntity())
        return Resource.Success(task)
    }

    override suspend fun deleteTaskLocal(id: Int): Resource<Unit> {
        taskDao.deleteTask(id)
        return Resource.Success(Unit)
    }

    override suspend fun markTaskCompleted(
        taskId: Int,
        completionDateMillis: Long
    ): Resource<Unit> {
        val task = getTaskById(taskId) ?: return Resource.Error("La tarea no existe")

        val updated = task.copy(
            status = MaintenanceStatus.COMPLETED,
            updatedAtMillis = completionDateMillis
        )
        taskDao.upsert(updated.toEntity())

        val historyRecord = MaintenanceHistory(
            id = 0,
            carId = task.carId,
            taskType = task.type,
            serviceDateMillis = completionDateMillis,
            mileageKm = task.dueMileageKm,
            cost = null,
            workshopName = null,
            notes = task.title
        )
        historyDao.upsert(historyRecord.toEntity())

        return Resource.Success(Unit)
    }

    override fun observeHistoryForCar(carId: Int): Flow<List<MaintenanceHistory>> =
        historyDao.observeHistoryForCar(carId).map { list -> list.map { it.toDomain() } }

    override suspend fun getHistoryById(id: Int): MaintenanceHistory? =
        historyDao.getHistoryById(id)?.toDomain()

    override suspend fun addRecord(record: MaintenanceHistory): Resource<MaintenanceHistory> {
        return try {
            historyDao.upsert(record.toEntity())
            Resource.Success(record)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error al guardar historial", record)
        }
    }

    override suspend fun deleteRecord(id: Int): Resource<Unit> {
        return try {
            historyDao.delete(id)
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.localizedMessage ?: "Error al eliminar historial")
        }
    }

    override suspend fun syncFromRemote(carId: Int): Resource<Unit> {
        val tasksRes = remote.getTasksForCar(carId)
        if (tasksRes is Resource.Error) {
            return Resource.Error(tasksRes.message ?: "Error al obtener tareas")
        }

        val historyRes = remote.getHistoryForCar(carId)
        if (historyRes is Resource.Error) {
            return Resource.Error(historyRes.message ?: "Error al obtener historial")
        }

        val tasks = (tasksRes as Resource.Success).data.orEmpty()
        val history = (historyRes as Resource.Success).data.orEmpty()

        taskDao.clearForCar(carId)
        historyDao.clearForCar(carId)

        taskDao.upsertAll(tasks.map { it.toEntity() })
        historyDao.upsertAll(history.map { it.toEntity() })

        return Resource.Success(Unit)
    }

    override suspend fun pushPending(): Resource<Unit> = postPendingTasks()

    override suspend fun postPendingTasks(): Resource<Unit> {
        val pendingCreates = taskDao.getPendingCreateTasks()
        for (entity in pendingCreates) {
            val task = entity.toDomain()
            val res = remote.createTask(task)
            if (res is Resource.Success) {
                val created = res.data ?: continue
                val updated = created.copy(id = entity.id, remoteId = created.remoteId)
                taskDao.upsert(updated.toEntity())
            }
        }

        val pendingUpdates = taskDao.getPendingUpdateTasks()
        for (entity in pendingUpdates) {
            val task = entity.toDomain()
            remote.updateTask(task)
            taskDao.upsert(task.toEntity())
        }

        val pendingDeletes = taskDao.getPendingDeleteTasks()
        for (entity in pendingDeletes) {
            val remoteId = entity.remoteId
            if (remoteId != null) {
                remote.deleteTask(remoteId)
            }
            taskDao.deleteTask(entity.id)
        }

        return Resource.Success(Unit)
    }
}