package edu.ucne.loginapi.data.remote

import edu.ucne.loginapi.data.remote.dto.UsuariosDto
import javax.inject.Inject

class RemoteDataSource @Inject constructor(
    private val usuariosApi: UsuariosApi
) {
    suspend fun getUsuarios(): List<UsuariosDto>{
        return usuariosApi.getUsuarios()
    }
    suspend fun getUsuario(id: Int): List<UsuariosDto>{
        return usuariosApi.getUsuario(id)
    }
    suspend fun saveUsuarios(usuariosDto: UsuariosDto){
        return usuariosApi.saveUsuarios(usuariosDto)
    }
    suspend fun updateUsuarios(usuariosDto: UsuariosDto){
        return usuariosApi.updateUsuarios(usuariosDto)
    }
}