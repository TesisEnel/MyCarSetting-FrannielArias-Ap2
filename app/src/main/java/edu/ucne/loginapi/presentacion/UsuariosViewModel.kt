package edu.ucne.loginapi.presentacion

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import edu.ucne.loginapi.data.remote.Resource
import edu.ucne.loginapi.domain.model.Usuarios
import edu.ucne.loginapi.domain.useCase.GetUsuariosUseCase
import edu.ucne.loginapi.domain.useCase.SaveUsuariosUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UsuariosViewModel @Inject constructor(
    private val getUsuariosUseCase: GetUsuariosUseCase,
    private val saveUsuariosUseCase: SaveUsuariosUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(UsuarioUiState(isLoading = true))
    val state: StateFlow<UsuarioUiState> = _state.asStateFlow()

    private var usuariosJob: Job? = null

    init {
        obtenerUsuarios()
    }

    private fun obtenerUsuarios() {
        usuariosJob?.cancel()

        usuariosJob = viewModelScope.launch {
            getUsuariosUseCase().collect { result ->
                when (result) {
                    is Resource.Loading -> {
                        _state.update {
                            it.copy(
                                isLoading = true,
                                error = null,
                                message = null
                            )
                        }
                    }
                    is Resource.Success -> {
                        _state.update {
                            it.copy(
                                listaUsuarios = result,
                                isLoading = false,
                                error = null
                            )
                        }
                    }
                    is Resource.Error -> {
                        _state.update {
                            it.copy(
                                error = result.message,
                                isLoading = false
                            )
                        }
                    }
                }
            }
        }
    }

    fun onEvent(event: UsuariosUiEvent) {
        when (event) {
            is UsuariosUiEvent.Crear -> crearUsuario(event.usuarios)
            is UsuariosUiEvent.Login -> login()
            is UsuariosUiEvent.Logout -> {
                _state.update {
                    it.copy(
                        isLoggedIn = false,
                        currentUser = null,
                        userName = "",
                        password = "",
                        error = null,
                        message = null
                    )
                }
            }

            is UsuariosUiEvent.ShowBottonSheet -> {
                _state.update {
                    it.copy(
                        isSheetVisible = true,
                        isLoading = false,
                        error = null,
                        message = null
                    )
                }
            }

            is UsuariosUiEvent.HideBottonSheet -> {
                _state.update {
                    it.copy(
                        isSheetVisible = false,
                        isLoading = false,
                        userName = "",
                        password = "",
                        error = null,
                        message = null
                    )
                }
            }

            is UsuariosUiEvent.UserNameChange -> {
                _state.update { it.copy(userName = event.value, error = null) }
            }

            is UsuariosUiEvent.PasswordChange -> {
                _state.update { it.copy(password = event.value, error = null) }
            }
        }
    }

    private fun login() {
        viewModelScope.launch {
            try {
                Log.d("UsuariosViewModel", "Intentando login para: ${_state.value.userName}")

                val userName = _state.value.userName.trim()
                val password = _state.value.password.trim()

                if (userName.isEmpty() || password.isEmpty()) {
                    _state.update {
                        it.copy(error = "Por favor complete todos los campos")
                    }
                    return@launch
                }

                _state.update { it.copy(isLoading = true, error = null) }

                val listaUsuarios = _state.value.listaUsuarios?.data ?: emptyList()

                Log.d("UsuariosViewModel", "Total usuarios: ${listaUsuarios.size}")

                val usuarioEncontrado = listaUsuarios.find { usuario ->
                    usuario.userName?.trim() == userName &&
                            usuario.password?.trim() == password
                }

                if (usuarioEncontrado != null) {
                    Log.d("UsuariosViewModel", "Login exitoso para: ${usuarioEncontrado.userName}")
                    _state.update {
                        it.copy(
                            isLoggedIn = true,
                            currentUser = usuarioEncontrado,
                            error = null,
                            message = null,
                            isLoading = false,
                            password = ""
                        )
                    }
                } else {
                    Log.d("UsuariosViewModel", "Usuario o contraseña incorrectos")
                    _state.update {
                        it.copy(
                            error = "Usuario o contraseña incorrectos",
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("UsuariosViewModel", "Error en login: ${e.message}")
                _state.update {
                    it.copy(
                        error = "Error al iniciar sesión: ${e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun crearUsuario(usuario: Usuarios) {
        viewModelScope.launch {
            try {
                Log.d("UsuariosViewModel", "Intentando crear usuario: $usuario")

                if (usuario.userName.isNullOrBlank() || usuario.password.isNullOrBlank()) {
                    _state.update {
                        it.copy(
                            error = "Por favor complete todos los campos",
                            isLoading = false
                        )
                    }
                    return@launch
                }

                _state.update { it.copy(isLoading = true, error = null) }

                val listaUsuarios = _state.value.listaUsuarios?.data ?: emptyList()
                val usuarioExiste = listaUsuarios.any {
                    it.userName?.trim() == usuario.userName?.trim()
                }

                if (usuarioExiste) {
                    _state.update {
                        it.copy(
                            error = "Este nombre de usuario ya existe",
                            isLoading = false
                        )
                    }
                    return@launch
                }

                val result = saveUsuariosUseCase(usuario)

                when (result) {
                    is Resource.Success -> {
                        Log.d("UsuariosViewModel", "Usuario creado exitosamente")
                        _state.update {
                            it.copy(
                                message = "Usuario creado correctamente. Ahora puedes iniciar sesión",
                                isSheetVisible = false,
                                userName = "",
                                password = "",
                                error = null,
                                isLoading = false
                            )
                        }
                        obtenerUsuarios()
                    }
                    is Resource.Error -> {
                        Log.e("UsuariosViewModel", "Error: ${result.message}")
                        _state.update {
                            it.copy(
                                error = result.message ?: "Error al crear usuario",
                                isLoading = false
                            )
                        }
                    }
                    is Resource.Loading -> {
                        _state.update { it.copy(isLoading = true) }
                    }
                }

            } catch (e: Exception) {
                Log.e("UsuariosViewModel", "Exception: ${e.message}")
                _state.update {
                    it.copy(
                        error = e.message ?: "Error al crear usuario",
                        isLoading = false
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        usuariosJob?.cancel()
    }
}