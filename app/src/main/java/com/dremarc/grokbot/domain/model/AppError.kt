package com.dremarc.grokbot.domain.model

/** Errors the UI can show as a snackbar. Each carries a Spanish user-facing message. */
sealed class AppError(val userMessage: String) {
    data object MissingApiKey : AppError("Falta la API key. Añádela en Ajustes.")
    data object InvalidApiKey : AppError("API key no válida. Revísala en Ajustes.")
    data object RateLimited : AppError("Límite de peticiones alcanzado. Espera un momento e inténtalo de nuevo.")
    data object Network : AppError("Sin conexión o la petición ha caducado.")
    class Server(detail: String) : AppError("Error del servidor: $detail")
    class Unknown(detail: String) : AppError("Error inesperado: $detail")
}

/** Thrown across layer boundaries carrying a typed [AppError]. */
class ChatException(val error: AppError) : Exception(error.userMessage)
