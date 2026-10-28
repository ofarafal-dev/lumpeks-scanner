package dev.ofarafal.lumpeksscanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ofarafal.lumpeksscanner.ui.CameraScreen
import dev.ofarafal.lumpeksscanner.ui.ResultScreen
import dev.ofarafal.lumpeksscanner.ui.UpdateDialog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    LumpeksApp()
                }
            }
        }
    }
}

@Composable
fun LumpeksApp(vm: ScannerViewModel = viewModel()) {
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCamera = granted }

    LaunchedEffect(Unit) {
        if (!hasCamera) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.updateMessage) {
        vm.updateMessage?.let { snackbar.showSnackbar(it) }
    }

    BackHandler(enabled = vm.state !is UiState.Camera) { vm.backToCamera() }

    vm.availableUpdate?.let { update ->
        UpdateDialog(
            update = update,
            state = vm.downloadState,
            onDownload = vm::downloadAndInstall,
            onDismiss = vm::dismissUpdate,
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val state = vm.state) {
                UiState.Camera ->
                    if (hasCamera) {
                        CameraScreen(onCaptured = vm::onPhoto)
                    } else {
                        CenteredMessage(
                            text = "Aplikacja potrzebuje dostępu do aparatu, żeby czytać metki.",
                            button = "Zezwól na aparat",
                        ) { permissionLauncher.launch(Manifest.permission.CAMERA) }
                    }

                UiState.Processing ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                is UiState.Result ->
                    ResultScreen(
                        scan = state.scan,
                        allBrands = vm.database.brands,
                        onCorrectBrand = vm::correctBrand,
                        onScanAgain = vm::backToCamera,
                    )

                is UiState.Error ->
                    CenteredMessage(text = state.message, button = "Spróbuj ponownie", onClick = vm::backToCamera)
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String, button: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Button(onClick = onClick) { Text(button) }
    }
}
