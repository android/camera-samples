/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.ai.samples.geminimultimodal.ui

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices.PHONE
import androidx.compose.ui.tooling.preview.Devices.TABLET
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.ai.samples.geminimultimodal.R
import com.android.ai.theme.AISampleCatalogTheme
import com.android.ai.uicomponent.GenerateButton
import com.android.ai.uicomponent.ImageInput
import com.android.ai.uicomponent.ImageInputType
import com.android.ai.uicomponent.SampleDetailTopAppBar
import com.android.ai.uicomponent.SecondaryButton
import com.android.ai.uicomponent.TextInput

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun GeminiMultimodalScreen(viewModel: GeminiMultimodalViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var imageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val photoPickerLauncher = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        uri?.let {
            imageUri = it
        }
    }

    if (uiState is GeminiMultimodalUiState.Error) {
        val errorMessage = (uiState as GeminiMultimodalUiState.Error).errorMessage
            ?: stringResource(R.string.unknown_error)
        LaunchedEffect(uiState) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.resetError()
        }
    }

    val windowSizeClass = calculateWindowSizeClass(activity = LocalActivity.current as Activity)
    val isExpandedScreen = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded

    GeminiMultimodalScreen(
        isExpandedScreen = isExpandedScreen,
        uiState = uiState,
        imageUri = imageUri,
        snackbarHostState = snackbarHostState,
        onGenerateClick = viewModel::generate,
        onImagePickerClick = {
            photoPickerLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeminiMultimodalScreen(
    isExpandedScreen: Boolean,
    uiState: GeminiMultimodalUiState,
    imageUri: Uri?,
    snackbarHostState: SnackbarHostState,
    onGenerateClick: (Bitmap, String) -> Unit,
    onImagePickerClick: () -> Unit,
) {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SampleDetailTopAppBar(
                sampleName = stringResource(R.string.geminimultimodal_title),
                sampleDescription = stringResource(R.string.geminimultimodal_subtitle),
                sourceCodeUrl = "https://github.com/android/ai-samples/tree/main/samples/gemini-multimodal",
                onBackClick = { backDispatcher?.onBackPressed() },
            )
        },
    ) { innerPadding ->
        if (isExpandedScreen) {
            ExpandedScreen(
                innerPadding,
                uiState,
                imageUri,
                onGenerateClick,
                onImagePickerClick,
            )
        } else {
            CompactScreen(
                innerPadding,
                uiState,
                imageUri,
                onGenerateClick,
                onImagePickerClick,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun CompactScreen(
    innerPadding: PaddingValues,
    uiState: GeminiMultimodalUiState,
    imageUri: Uri?,
    onGenerateClick: (Bitmap, String) -> Unit,
    onTakePictureClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = when {
        imageUri != null && uiState is GeminiMultimodalUiState.Success -> ImageInputType.WithImage.WithText(imageUri, uiState.generatedText)
        imageUri != null && uiState is GeminiMultimodalUiState.Loading -> ImageInputType.WithImage.Analyzing(imageUri)
        imageUri != null -> ImageInputType.WithImage.Image(imageUri)
        else -> ImageInputType.Empty(onAddImage = onTakePictureClick)
    }
    ImageInput(
        type = type,
        modifier = modifier.padding(innerPadding),
    ) {
        val textFieldState = rememberTextFieldState()
        val keyboardController = LocalSoftwareKeyboardController.current
        PromptInput(
            textFieldState,
            uiState,
            imageUri,
            onGenerateClick,
            keyboardController,
            onTakePictureClick,
        )
    }
}

@Composable
private fun ExpandedScreen(
    innerPadding: PaddingValues,
    uiState: GeminiMultimodalUiState,
    imageUri: Uri?,
    onGenerateClick: (Bitmap, String) -> Unit,
    onImagePickerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = when {
        imageUri != null && uiState is GeminiMultimodalUiState.Success -> ImageInputType.WithImage.WithText(imageUri, uiState.generatedText)
        imageUri != null && uiState is GeminiMultimodalUiState.Loading -> ImageInputType.WithImage.Analyzing(imageUri)
        imageUri != null -> ImageInputType.WithImage.Image(imageUri)
        else -> ImageInputType.Empty(onAddImage = onImagePickerClick)
    }
    Row(
        modifier = modifier
            .padding(innerPadding)
            .fillMaxSize(),
    ) {
        ImageInput(
            type = type,
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .padding(horizontal = 16.dp),
        )

        val textFieldState = rememberTextFieldState()
        val keyboardController = LocalSoftwareKeyboardController.current
        PromptInput(
            textFieldState,
            uiState,
            imageUri,
            onGenerateClick,
            keyboardController,
            onImagePickerClick,
            modifier = Modifier
                .weight(1f)
                .align(Alignment.Bottom)
                .imePadding()
                .padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun PromptInput(
    textFieldState: TextFieldState,
    uiState: GeminiMultimodalUiState,
    imageUri: Uri?,
    onGenerateClick: (Bitmap, String) -> Unit,
    keyboardController: SoftwareKeyboardController?,
    onTakePictureClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    TextInput(
        state = textFieldState,
        placeholder = stringResource(R.string.geminimultimodal_prompt_placeholder),
        primaryButton = {
            GenerateButton(
                text = "",
                icon = painterResource(id = com.android.ai.uicomponent.R.drawable.ic_ai_img),
                modifier = Modifier
                    .width(72.dp)
                    .height(55.dp)
                    .padding(4.dp),
                enabled = uiState !is GeminiMultimodalUiState.Loading && imageUri != null,
                onClick = {
                    if (imageUri != null) {
                        val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
//                        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, imageUri))
                        onGenerateClick(bitmap, textFieldState.text.toString())
                    }
                    keyboardController?.hide()
                },
            )
        },
        secondaryButton = {
            if (imageUri != null) {
                SecondaryButton(
                    text = "",
                    enabled = uiState !is GeminiMultimodalUiState.Loading,
                    icon = painterResource(id = com.android.ai.uicomponent.R.drawable.ic_add),
                    onClick = onTakePictureClick,
                    modifier = Modifier
                        .width(48.dp)
                        .height(56.dp)
                        .padding(4.dp),
                )
            }
        },
        modifier = modifier
            .padding(10.dp),
    )
}

@Preview(name = "Phone", device = PHONE)
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun GeminiMultimodalScreenPreview() {
    AISampleCatalogTheme {
        GeminiMultimodalScreen(
            isExpandedScreen = false,
            uiState = GeminiMultimodalUiState.Initial,
            imageUri = null,
            snackbarHostState = remember { SnackbarHostState() },
            onGenerateClick = { _, _ -> },
            onImagePickerClick = {},
        )
    }
}

@Preview(name = "Tablet", device = TABLET)
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun GeminiMultimodalScreenTabletPreview() {
    AISampleCatalogTheme {
        GeminiMultimodalScreen(
            isExpandedScreen = true,
            uiState = GeminiMultimodalUiState.Initial,
            imageUri = null,
            snackbarHostState = remember { SnackbarHostState() },
            onGenerateClick = { _, _ -> },
            onImagePickerClick = {},
        )
    }
}
