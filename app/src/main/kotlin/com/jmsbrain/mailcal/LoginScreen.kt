package com.jmsbrain.mailcal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf(Repo.DEFAULT_SERVER) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun signIn() {
        if (email.isBlank() || password.isEmpty()) {
            error = "Enter your email and password."
            return
        }
        busy = true
        error = null
        scope.launch {
            runCatching { Repo.signIn(server, email, password) }
                .onFailure { error = it.message ?: "Couldn't sign in." }
            busy = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = designDp(24f), vertical = designDp(32f)),
    ) {
        BasicText("Mailcal", style = headingStyle())
        Gap(12f)
        BasicText(
            "Sign in with your mailbox.org email and password. If you use two-factor sign-in, use an app password instead.",
            style = paragraphStyle(),
        )
        Gap(16f)
        Field("EMAIL", email, { email = it; error = null }, "you@mailbox.org",
            keyboardType = KeyboardType.Email, capitalization = KeyboardCapitalization.None)
        Field("PASSWORD", password, { password = it; error = null }, password = true)
        Field("SERVER", server, { server = it; error = null },
            keyboardType = KeyboardType.Uri, capitalization = KeyboardCapitalization.None)
        Gap(20f)
        if (busy) Note("Signing in…") else TextButton("SIGN IN") { signIn() }
        error?.let {
            Gap(12f)
            BasicText(it, style = paragraphStyle())
        }
        Gap(24f)
        Note("Your password stays on this phone, encrypted, and is only sent to the server above.")
    }
}
