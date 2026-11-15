package io.github.szlynn.scanmyweight.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.szlynn.scanmyweight.R
import io.github.szlynn.scanmyweight.data.SheetsRepository
import io.github.szlynn.scanmyweight.ocr.MlKitTextAnalyzer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    sheetsRepository: SheetsRepository,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ScanViewModel = viewModel(factory = ScanViewModel.Factory(sheetsRepository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
    }
    val permissionPrefs = remember(context) {
        context.getSharedPreferences("camera_permission", Context.MODE_PRIVATE)
    }
    var permanentlyDenied by remember { mutableStateOf(false) }
    fun refreshPermission() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        viewModel.onCameraPermissionResult(granted)
        permanentlyDenied = !granted && permissionPrefs.getBoolean("requested", false) &&
            activity?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } == true
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {
            permissionPrefs.edit().putBoolean("requested", true).apply()
            refreshPermission()
        },
    )

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refreshPermission()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        if (!uiState.hasCameraPermission && !permissionPrefs.getBoolean("requested", false)) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(uiState.cameraError) {
        uiState.cameraError?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.navigate_to_settings),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (uiState.hasCameraPermission) {
                val analyzer = remember(viewModel) { MlKitTextAnalyzer(viewModel::onParseResult) }
                CameraPreview(
                    imageAnalyzer = analyzer,
                    onError = viewModel::onCameraError,
                    modifier = Modifier.fillMaxSize(),
                )
                RoiOverlay(modifier = Modifier.fillMaxSize())
            } else {
                NoCameraPermissionContent(
                    onRequestPermission = {
                        if (permanentlyDenied) {
                            context.startActivity(Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ))
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    permanentlyDenied = permanentlyDenied,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            MetricIndicatorRow(
                uiState = uiState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )

            when (uiState.sessionState) {
                ScanSessionState.COMPLETE -> ScanResultOverlay(
                    onReset = viewModel::resetScan,
                    modifier = Modifier.align(Alignment.Center),
                )
                ScanSessionState.UPLOAD_ERROR -> UploadErrorOverlay(
                    message = uiState.uploadErrorMessage,
                    onRetry = viewModel::retryUpload,
                    onReset = viewModel::resetScan,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> Unit
            }
        }
    }
}

@Composable
private fun MetricIndicatorRow(
    uiState: ScanUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        MetricIndicator(label = stringResource(R.string.metric_weight), state = uiState.weightState)
        MetricIndicator(label = stringResource(R.string.metric_bf), state = uiState.bfState)
        MetricIndicator(label = stringResource(R.string.metric_bw), state = uiState.bwState)
        MetricIndicator(label = stringResource(R.string.metric_bm), state = uiState.bmState)
    }
}

@Composable
private fun MetricIndicator(
    label: String,
    state: MetricCaptureState,
    modifier: Modifier = Modifier,
) {
    val captureDescription = stringResource(
        if (state == MetricCaptureState.LOCKED) R.string.metric_state_locked else R.string.metric_state_waiting,
    )
    val color = if (state == MetricCaptureState.LOCKED) Color(0xFF4CAF50) else Color.Gray
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.semantics(mergeDescendants = true) {
            stateDescription = captureDescription
        },
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color = color, shape = CircleShape),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

@Composable
private fun NoCameraPermissionContent(
    onRequestPermission: () -> Unit,
    permanentlyDenied: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(32.dp),
    ) {
        Text(
            text = stringResource(
                if (permanentlyDenied) R.string.scan_camera_permission_denied
                else R.string.scan_camera_permission_rationale,
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequestPermission) {
            Text(stringResource(
                if (permanentlyDenied) R.string.scan_open_app_settings_button
                else R.string.scan_grant_permission_button,
            ))
        }
    }
}

@Composable
private fun ScanResultOverlay(
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.75f),
                shape = MaterialTheme.shapes.medium,
            )
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.scan_complete),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onReset) {
            Text(stringResource(R.string.scan_reset_button))
        }
    }
}

@Composable
private fun UploadErrorOverlay(
    message: String?,
    onRetry: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.75f),
                shape = MaterialTheme.shapes.medium,
            )
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.scan_upload_error),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        if (message != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRetry) {
                Text(stringResource(R.string.scan_retry_upload_button))
            }
            Button(onClick = onReset) {
                Text(stringResource(R.string.scan_reset_button))
            }
        }
    }
}
