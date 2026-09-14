package com.apprch.app.ui.auth

import com.google.firebase.auth.FirebaseAuthException

object AuthErrors {
    private const val MISMATCH =
        "That email or password doesn’t match. If you just reset, use the new password from the email."

    fun userFacingMessage(error: Throwable): String {
        val code = (error as? FirebaseAuthException)?.errorCode.orEmpty()
        val text = error.localizedMessage.orEmpty()
        return when {
            code in setOf(
                "ERROR_INVALID_CREDENTIAL",
                "ERROR_INVALID_LOGIN_CREDENTIALS",
                "ERROR_WRONG_PASSWORD",
                "ERROR_USER_NOT_FOUND",
                "ERROR_INVALID_EMAIL",
                "invalid-credential"
            ) || text.contains("malformed", ignoreCase = true) ||
                text.contains("INVALID_LOGIN", ignoreCase = true) -> MISMATCH
            code == "ERROR_NETWORK_REQUEST_FAILED" -> "Check your connection and try again."
            code == "ERROR_TOO_MANY_REQUESTS" -> "Too many tries. Wait a minute and try again."
            code == "ERROR_USER_DISABLED" -> "This account is disabled."
            code == "ERROR_WEAK_PASSWORD" -> "Use a password with at least 6 characters."
            code == "ERROR_EMAIL_ALREADY_IN_USE" ->
                "That email already has an account. Sign in or reset the password."
            code == "ERROR_EXPIRED_ACTION_CODE" || code == "ERROR_INVALID_ACTION_CODE" ->
                "This reset link expired. Request a new one from Forgot password."
            else -> text.ifBlank { "Couldn’t finish that. Try again." }
        }
    }
}
