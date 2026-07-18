package com.harding.feeds.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harding.feeds.ui.DAY_FORMAT
import com.harding.feeds.ui.home.shareInviteCode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun OnboardingScreen(vm: OnboardingViewModel) {
    val group by vm.group.collectAsStateWithLifecycle()
    val inviteCode = vm.inviteCode

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            inviteCode != null -> InviteCodeStep(inviteCode, onContinue = vm::dismissInviteCode)
            group == null -> ChooseGroupStep(vm)
            else -> BabySetupStep(vm)
        }

        if (vm.busy) {
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator()
        }
        vm.error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ChooseGroupStep(vm: OnboardingViewModel) {
    var joinCode by remember { mutableStateOf("") }

    Text("Set up your group", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        "Feeds are shared with everyone in your group.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(32.dp))

    Button(
        onClick = vm::createGroup,
        enabled = !vm.busy,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Create a new group") }

    Spacer(Modifier.height(24.dp))
    HorizontalDivider()
    Spacer(Modifier.height(24.dp))

    OutlinedTextField(
        value = joinCode,
        onValueChange = { joinCode = it },
        label = { Text("Invite code") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = { vm.joinGroup(joinCode) },
        enabled = !vm.busy && joinCode.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Join with code") }
}

@Composable
private fun InviteCodeStep(code: String, onContinue: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Text("Group created", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))
    Text(
        "Share this code with your partner. They sign in with Google and enter it to join.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    Text(
        code,
        style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Text("Copy") }
        OutlinedButton(onClick = { context.shareInviteCode(code) }) { Text("Share") }
    }
    Spacer(Modifier.height(24.dp))
    Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BabySetupStep(vm: OnboardingViewModel) {
    var name by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    Text("About your baby", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(32.dp))

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
        Text(dateOfBirth?.format(DAY_FORMAT) ?: "Date of birth (optional)")
    }
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = { vm.createBaby(name, dateOfBirth) },
        enabled = !vm.busy && name.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Save") }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (dateOfBirth ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        dateOfBirth = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}
