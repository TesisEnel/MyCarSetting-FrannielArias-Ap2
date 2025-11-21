package edu.ucne.loginapi.domain.validation

data class ValidationResult(
    val isValid: Boolean,
    val error: String
)