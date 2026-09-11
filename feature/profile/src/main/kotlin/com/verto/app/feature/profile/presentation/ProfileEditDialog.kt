package com.verto.app.feature.profile.presentation

import com.verto.feature.profile.R

import com.verto.app.ui.components.VertoOutlinedTextField
import com.verto.app.ui.components.VertoButton

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.verto.app.ui.components.DialogTextField
import com.verto.app.ui.components.dialogFieldColors
import com.verto.app.ui.theme.*
import com.verto.app.ui.components.VertoIconButton

@Composable
fun ProfileEditDialog(
    initialName      : String,
    initialPhone     : String,
    canEditName      : Boolean,
    isSavingProfile  : Boolean,
    isSavingPassword : Boolean,
    passwordError    : String?,
    onSaveProfile    : (name: String, phone: String) -> Unit,
    onSavePassword   : (current: String, newPass: String) -> Unit,
    onDismiss        : () -> Unit
) {
    var name  by remember(initialName)  { mutableStateOf(initialName) }
    var phone by remember(initialPhone) { mutableStateOf(initialPhone) }

    var currentPass by remember { mutableStateOf("") }
    var newPass     by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var showCurrent by remember { mutableStateOf(false) }
    var showNew     by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    var profileSaved by remember { mutableStateOf(false) }

    val mismatch = newPass.isNotBlank() && confirmPass.isNotBlank() && newPass != confirmPass
    val tooShort = newPass.isNotBlank() && newPass.length < 6
    val canSavePass = currentPass.isNotBlank() && newPass.length >= 6 && newPass == confirmPass && !isSavingPassword

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = BgCard,
        title = {
            Text(androidx.compose.ui.res.stringResource(R.string.ds_2f1419b78059), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = ProfileTextScale.sp15)
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ProfileDimensions.dp12)
            ) {
                // ── بيانات المستخدم ──────────────────────────────────────────
                DialogTextField(
                    label = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_name),
                    value = name,
                    onValueChange = { if (canEditName) name = it },
                    enabled = canEditName
                )
                DialogTextField(
                    label         = androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_phone_number),
                    value         = phone,
                    onValueChange = { phone = it },
                    keyboardType  = KeyboardType.Phone
                )
                if (profileSaved) {
                    Text(androidx.compose.ui.res.stringResource(R.string.ds_cbe6963fbdf4), color = SuccessColor, fontSize = ProfileTextScale.sp12)
                }
                VertoButton(
                    onClick = {
                        onSaveProfile(name.trim(), phone.trim())
                        profileSaved = true
                    },
                    enabled  = !isSavingProfile,
                    colors   = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                    shape    = RoundedCornerShape(ProfileDimensions.dp10),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSavingProfile) CircularProgressIndicator(Modifier.size(ProfileDimensions.dp16), color = TextPrimary, strokeWidth = ProfileDimensions.dp2)
                    else Text(androidx.compose.ui.res.stringResource(R.string.ds_2729e3381153), fontSize = ProfileTextScale.sp13)
                }

                HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = ProfileDimensions.dp4))

                // ── تغيير كلمة السر ──────────────────────────────────────────
                Text(androidx.compose.ui.res.stringResource(R.string.ds_77c545c8cf05), color = TextSecondary, fontSize = ProfileTextScale.sp12, fontWeight = FontWeight.Bold)

                VertoOutlinedTextField(
                    value         = currentPass,
                    onValueChange = { currentPass = it },
                    label         = { Text(androidx.compose.ui.res.stringResource(R.string.ds_c8d8277deccd), fontSize = ProfileTextScale.sp12) },
                    singleLine    = true,
                    visualTransformation = if (showCurrent) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        VertoIconButton(onClick = { showCurrent = !showCurrent }) {
                            Icon(
                                if (showCurrent) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                null, tint = TextMuted, modifier = Modifier.size(ProfileDimensions.dp18)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = dialogFieldColors()
                )

                VertoOutlinedTextField(
                    value         = newPass,
                    onValueChange = { newPass = it },
                    label         = { Text(androidx.compose.ui.res.stringResource(R.string.ds_a218343095f1), fontSize = ProfileTextScale.sp12) },
                    singleLine    = true,
                    isError       = tooShort,
                    supportingText = if (tooShort) {
                        { Text(androidx.compose.ui.res.stringResource(R.string.ds_b9509a00a55a), color = ErrorColor, fontSize = ProfileTextScale.sp11) }
                    } else null,
                    visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        VertoIconButton(onClick = { showNew = !showNew }) {
                            Icon(
                                if (showNew) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                null, tint = TextMuted, modifier = Modifier.size(ProfileDimensions.dp18)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = dialogFieldColors()
                )

                VertoOutlinedTextField(
                    value         = confirmPass,
                    onValueChange = { confirmPass = it },
                    label         = { Text(androidx.compose.ui.res.stringResource(R.string.ds_6fcea98fc894), fontSize = ProfileTextScale.sp12) },
                    singleLine    = true,
                    isError       = mismatch,
                    supportingText = if (mismatch) {
                        { Text(androidx.compose.ui.res.stringResource(R.string.ds_f5768a2540ab), color = ErrorColor, fontSize = ProfileTextScale.sp11) }
                    } else null,
                    visualTransformation = if (showConfirm) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        VertoIconButton(onClick = { showConfirm = !showConfirm }) {
                            Icon(
                                if (showConfirm) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                null, tint = TextMuted, modifier = Modifier.size(ProfileDimensions.dp18)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = dialogFieldColors()
                )

                if (passwordError != null) {
                    Text(passwordError, color = ErrorColor, fontSize = ProfileTextScale.sp12)
                }

                VertoButton(
                    onClick  = { onSavePassword(currentPass, newPass) },
                    enabled  = canSavePass,
                    colors   = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                    shape    = RoundedCornerShape(ProfileDimensions.dp10),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSavingPassword) CircularProgressIndicator(Modifier.size(ProfileDimensions.dp16), color = TextPrimary, strokeWidth = ProfileDimensions.dp2)
                    else Text(androidx.compose.ui.res.stringResource(R.string.ds_77c545c8cf05), fontSize = ProfileTextScale.sp13)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.verto.core.common.R.string.common_action_close), color = TextMuted) }
        }
    )
}
