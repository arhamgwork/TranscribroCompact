/*
 * Transcribro Compact - a personal modification of Transcribro.
 *
 * Original work: Transcribro, Copyright (c) 2024-2025 soupslurpr, ISC License
 * (full license text in LICENSE.txt at the root of this repository).
 *
 * Modified: the voice input keyboard is a single compact strip of buttons instead of
 * a panel covering 45% (portrait) / 65% (landscape) of the screen.
 */

package dev.soupslurpr.transcribro.ui.voiceinput

import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.SpeechRecognizer.createSpeechRecognizer
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration.getKeyRepeatDelay
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowRightAlt
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.soupslurpr.transcribro.R
import dev.soupslurpr.transcribro.dataStore
import dev.soupslurpr.transcribro.preferences.PreferencesViewModel
import dev.soupslurpr.transcribro.recognitionservice.MainRecognitionService
import dev.soupslurpr.transcribro.ui.reusablecomposables.ScreenLazyColumn
import dev.soupslurpr.transcribro.ui.reusablecomposables.longPressableKey
import dev.soupslurpr.transcribro.ui.theme.TranscribroTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private var speechRecognizer: MutableState<SpeechRecognizer?> = mutableStateOf(null)

private var isRecognizing by mutableStateOf(false)

private var showInsufficientPermissionsError by mutableStateOf(false)

private var isSpeaking by mutableStateOf(false)

class VoiceInput : InputMethodService() {
    private val voiceInputLifecycleOwner = VoiceInputLifecycleOwner()

    override fun onCreate() {
        super.onCreate()
        voiceInputLifecycleOwner.onCreate()
    }

    @OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
    override fun onCreateInputView(): View {
        voiceInputLifecycleOwner.attachToDecorView(window?.window?.decorView)

        val view = ComposeView(this)

        view.setContent {
            val context = LocalContext.current

            val audioManager = context.getSystemService(AudioManager::class.java)

            val startedRecognitionMediaPlayer = MediaPlayer.create(context, R.raw.started_recognition)

            val stoppedRecognitionMediaPlayer = MediaPlayer.create(context, R.raw.stopped_recognition)

            val preferencesViewModel: PreferencesViewModel = viewModel(
                factory = PreferencesViewModel.PreferencesViewModelFactory(dataStore)
            )

            val preferencesUiState by preferencesViewModel.uiState.collectAsState()

            val acceptedPrivacyPolicyAndLicense = preferencesUiState.acceptedPrivacyPolicyAndLicense.second.value

            val autoStopRecognition by preferencesUiState.autoStopRecognition.second

            val autoStartRecognition by preferencesUiState.autoStartRecognition.second

            val snackbarHostState = remember { SnackbarHostState() }

            val snackbarCoroutine = rememberCoroutineScope()

            var snackbarJob: Job? by remember {
                mutableStateOf(null)
            }

            fun cancelSnackbarJobAndLaunch(
                message: String,
                actionLabel: String? = null,
                withDismissAction: Boolean = false,
                duration: SnackbarDuration =
                    if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Indefinite
            ) {
                snackbarJob?.cancel()
                snackbarJob = snackbarCoroutine.launch {
                    snackbarHostState.showSnackbar(
                        message,
                        actionLabel,
                        withDismissAction,
                        duration
                    )
                }
            }

            val isPortrait = LocalConfiguration.current.orientation == ORIENTATION_PORTRAIT

            // Phones held upright are too narrow for one row of buttons, so they get two rows.
            val useTwoRows = isPortrait &&
                    LocalConfiguration.current.screenWidthDp < CompactKeyboardSize.twoRowMaxScreenWidthDp

            // Tablets (shortest side 600dp or more) get slightly bigger buttons.
            val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= CompactKeyboardSize.twoRowMaxScreenWidthDp
            val maxStripWidth = if (isTablet) CompactKeyboardSize.tabletMaxStripWidth else CompactKeyboardSize.maxStripWidth

            // Messages (accept privacy policy / grant microphone) need more room than the button strip.
            val panelHeight = when {
                !acceptedPrivacyPolicyAndLicense || showInsufficientPermissionsError ->
                    CompactKeyboardSize.messagePanelHeight

                useTwoRows -> CompactKeyboardSize.twoRowStripHeight
                isTablet && isPortrait -> CompactKeyboardSize.tabletStripHeightPortrait
                isTablet -> CompactKeyboardSize.tabletStripHeightLandscape
                isPortrait -> CompactKeyboardSize.stripHeightPortrait
                else -> CompactKeyboardSize.stripHeightLandscape
            }

            val buttonShape = RoundedCornerShape(10.dp)

            TranscribroTheme(preferencesViewModel = preferencesViewModel) {
                Surface(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Keep clear of Android's navigation area and its "hide / switch keyboard" buttons.
                            .windowInsetsPadding(WindowInsets.navigationBars union WindowInsets(bottom = CompactKeyboardSize.minBottomSpace))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(panelHeight)
                                .padding(horizontal = 8.dp, vertical = CompactKeyboardSize.verticalPadding)
                        ) {
                            if (!acceptedPrivacyPolicyAndLicense) {
                                ScreenLazyColumn {
                                    item {
                                        Text("Please accept the privacy policy and license first!")
                                    }
                                    item {
                                        Button(
                                            onClick = {
                                                startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName))
                                            }
                                        ) {
                                            Text("Open Transcribro")
                                        }
                                    }
                                }
                            } else if (showInsufficientPermissionsError) {
                                ScreenLazyColumn {
                                    item {
                                        Text(
                                            "Please grant \"Allow only while using the app\" microphone permission in " +
                                                    "settings to continue."
                                        )
                                    }
                                    item {
                                        Button(
                                            onClick = {
                                                val intent = Intent(
                                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    Uri.fromParts("package", context.packageName, null)
                                                )

                                                intent.addCategory(Intent.CATEGORY_DEFAULT)

                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                                                startActivity(intent)

                                                showInsufficientPermissionsError = false
                                            }
                                        ) {
                                            Text("Open App Settings")
                                        }
                                    }
                                }
                            } else {
                                LaunchedEffect(true) {
                                    if (!isRecognizing) {
                                        if (speechRecognizer.value == null) {
                                            speechRecognizer.value = createSpeechRecognizer(
                                                applicationContext,
                                                ComponentName(applicationContext, MainRecognitionService::class.java)
                                            )

                                            speechRecognizer.value!!.setRecognitionListener(object :
                                                RecognitionListener {
                                                override fun onReadyForSpeech(params: Bundle?) {
                                                    isRecognizing = true

                                                    if (audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                                                        startedRecognitionMediaPlayer.start()
                                                    }
                                                }

                                                override fun onBeginningOfSpeech() {
                                                    isSpeaking = true
                                                }

                                                override fun onRmsChanged(rmsdB: Float) {
//                TODO("Not yet implemented")
                                                }

                                                override fun onBufferReceived(buffer: ByteArray?) {
//                TODO("Not yet implemented")
                                                }

                                                override fun onEndOfSpeech() {
                                                    isSpeaking = false
                                                }

                                                override fun onError(error: Int) {
                                                    when (error) {
                                                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                                            showInsufficientPermissionsError = true
                                                        }

                                                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                                                            cancelSnackbarJobAndLaunch(
                                                                "Recognition is finishing, please wait or cancel.",
                                                                withDismissAction = true,
                                                                duration = SnackbarDuration.Short
                                                            )
                                                        }

                                                        else -> {
//                                                            println(error)
                                                        }
                                                    }
                                                }

                                                override fun onResults(results: Bundle?) {
                                                    isRecognizing = false

                                                    if (audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                                                        stoppedRecognitionMediaPlayer.start()
                                                    }

                                                    if (preferencesUiState.autoSwitchToPreviousInputMethod.second.value) {
                                                        this@VoiceInput.switchToPreviousInputMethod()
                                                    }
                                                }

                                                override fun onPartialResults(partialResults: Bundle?) {
                                                    currentInputConnection.also { ic: InputConnection ->
                                                        if (partialResults != null) {
                                                            val transcription = partialResults.getStringArrayList(
                                                                SpeechRecognizer.RESULTS_RECOGNITION
                                                            )?.get(0) ?: ""

                                                            if (transcription.isNotEmpty()) {
                                                                var textToCommit = if ((ic.getTextBeforeCursor(
                                                                        2,
                                                                        0
                                                                    ) == "") || (ic.getTextBeforeCursor(1, 0) == "\n")
                                                                    || (ic.getTextBeforeCursor(1, 0) == " ")
                                                                ) {
                                                                    transcription.removePrefix(" ")
                                                                } else {
                                                                    transcription
                                                                }

                                                                val selectedText = ic.getSelectedText(0)

                                                                val readdUppercase =
                                                                    textToCommit.filter { it.isLetter() }.firstOrNull {
                                                                        !it.isUpperCase()
                                                                    } == null

                                                                if (!selectedText.isNullOrEmpty()) {
                                                                    val removeSuffixPoint =
                                                                        textToCommit.trim().toList().withIndex()
                                                                            .reversed().firstOrNull {
                                                                                it.value.isLetterOrDigit()
                                                                            }

                                                                    if (removeSuffixPoint != null) {
                                                                        textToCommit = textToCommit.trim()
                                                                            .substring(0..removeSuffixPoint.index)
                                                                    }

                                                                    val removePrefixPoint =
                                                                        textToCommit.trim().toList().withIndex()
                                                                            .firstOrNull {
                                                                                it.value.isLetterOrDigit()
                                                                            }

                                                                    if (removePrefixPoint != null) {
                                                                        textToCommit = textToCommit.trim()
                                                                            .substring(removePrefixPoint.index..textToCommit.trim().lastIndex)
                                                                    }

                                                                    val selectedEndOfWordPunctuation =
                                                                        selectedText.trim().toList().withIndex()
                                                                            .reversed().firstOrNull {
                                                                                it.value.isLetterOrDigit()
                                                                            }

                                                                    val firstSelectedNonWhitespaceCharacter =
                                                                        selectedText.trim().firstOrNull()

                                                                    if (firstSelectedNonWhitespaceCharacter != null) {
                                                                        textToCommit =
                                                                            if (textToCommit.firstOrNull { !it.isUpperCase() } == null) {
                                                                                textToCommit
                                                                            } else if (firstSelectedNonWhitespaceCharacter.isUpperCase()) {
                                                                                textToCommit[0].uppercaseChar() + textToCommit.substring(
                                                                                    1..textToCommit.lastIndex
                                                                                )
                                                                            } else if (firstSelectedNonWhitespaceCharacter.isLowerCase()) {
                                                                                textToCommit[0].lowercaseChar() + textToCommit.substring(
                                                                                    1..textToCommit.lastIndex
                                                                                )
                                                                            } else if (selectedText.firstOrNull { it.isLetterOrDigit() || it.isWhitespace() } == null) {
                                                                                textToCommit[0].lowercaseChar() + textToCommit.substring(
                                                                                    1..textToCommit.lastIndex
                                                                                )
                                                                            } else {
                                                                                textToCommit
                                                                            }
                                                                    }

                                                                    if (selectedEndOfWordPunctuation != null) {
                                                                        textToCommit += selectedText.trim()
                                                                            .substring(selectedEndOfWordPunctuation.index + 1..selectedText.trim().lastIndex)
                                                                    }

                                                                    if (selectedText.firstOrNull { it.isLetterOrDigit() } == null) {
                                                                        textToCommit = " $textToCommit"

                                                                        if (textToCommit.last().isLetterOrDigit()) {
                                                                            textToCommit = "$textToCommit$selectedText"
                                                                        }
                                                                    }
                                                                } else {
                                                                    val twoCharactersBeforeCursor =
                                                                        ic.getTextBeforeCursor(2, 0)
                                                                    val firstLetterOrDigit = textToCommit.withIndex()
                                                                        .firstOrNull { it.value.isLetterOrDigit() }

                                                                    if ((twoCharactersBeforeCursor != null) && (twoCharactersBeforeCursor.firstOrNull { it.isLetterOrDigit() } != null)) {
                                                                        if (twoCharactersBeforeCursor[0].isLetterOrDigit() && (twoCharactersBeforeCursor[1].isLetterOrDigit() || twoCharactersBeforeCursor[1].isWhitespace())) {
                                                                            if (firstLetterOrDigit != null) {
                                                                                val chars = textToCommit.toCharArray()

                                                                                chars[firstLetterOrDigit.index] =
                                                                                    firstLetterOrDigit.value.lowercaseChar()

                                                                                textToCommit = chars.concatToString()
                                                                            }
                                                                        }
                                                                    }
                                                                }

                                                                if (readdUppercase) {
                                                                    textToCommit = textToCommit.uppercase()
                                                                }

                                                                ic.commitText(
                                                                    textToCommit,
                                                                    1
                                                                )

                                                                if (preferencesUiState.autoSendTranscription.second.value) {
                                                                    ic.performEditorAction(EditorInfo.IME_ACTION_SEND)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                override fun onEvent(eventType: Int, params: Bundle?) {
//            TODO("Not yet implemented")
                                                }
                                            })

                                            if (autoStartRecognition) {
                                                speechRecognizer.value!!.startListening(
                                                    getStartListeningIntent(
                                                        autoStopRecognition
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }

                                CompositionLocalProvider(
                                    // Lets the buttons be shorter than Material's default 48dp touch size,
                                    // so the strip heights in CompactKeyboardSize are respected.
                                    LocalMinimumInteractiveComponentSize provides 0.dp
                                ) {
                                    // Settings, switch keyboard, cancel and mic (first row on phones held upright)
                                    val firstRowButtons: @Composable RowScope.() -> Unit = {
                                        val buttonModifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()

                                        // Settings
                                        OutlinedIconButton(
                                            onClick = {
                                                speechRecognizer.value?.cancel()
                                                isRecognizing = false

                                                val intent = context.packageManager
                                                    .getLaunchIntentForPackage(context.packageName)!!
                                                    .apply {
                                                        action = (Intent.ACTION_APPLICATION_PREFERENCES)
                                                    }

                                                startActivity(intent)
                                            },
                                            modifier = buttonModifier,
                                            shape = buttonShape
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Settings,
                                                contentDescription = "open Transcribro's settings"
                                            )
                                        }

                                        // Switch back to the previous keyboard
                                        FilledTonalIconButton(
                                            onClick = {
                                                speechRecognizer.value?.cancel()
                                                isRecognizing = false
                                                switchToPreviousInputMethod()
                                            },
                                            modifier = buttonModifier,
                                            shape = buttonShape
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Keyboard,
                                                contentDescription = "Cancel recognition and switch to the previous input method"
                                            )
                                        }

                                        // Cancel recognition (only active while listening)
                                        OutlinedIconButton(
                                            onClick = {
                                                if (isRecognizing) {
                                                    speechRecognizer.value?.cancel()
                                                    isRecognizing = false
                                                }
                                            },
                                            modifier = buttonModifier,
                                            enabled = isRecognizing,
                                            shape = buttonShape
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Close,
                                                contentDescription = "Cancel Recognition"
                                            )
                                        }

                                        // Microphone: start / stop listening
                                        FilledIconToggleButton(
                                            checked = isRecognizing,
                                            onCheckedChange = {
                                                if (isRecognizing) {
                                                    speechRecognizer.value?.stopListening()
                                                } else {
                                                    speechRecognizer.value?.startListening(
                                                        getStartListeningIntent(
                                                            autoStopRecognition
                                                        )
                                                    )
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(CompactKeyboardSize.micButtonWeight)
                                                .fillMaxHeight(),
                                            shape = buttonShape
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Mic,
                                                contentDescription = if (isRecognizing) {
                                                    "Speech recognition active"
                                                } else {
                                                    "Speech recognition inactive"
                                                },
                                                modifier = Modifier.size(CompactKeyboardSize.micIconSize)
                                            )
                                        }
                                    }

                                    // Editing buttons (second row on phones held upright)
                                    val secondRowButtons: @Composable RowScope.() -> Unit = {
                                        val buttonModifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()

                                        // Clear all text that isn't selected
                                        FilledTonalIconButton(
                                            onClick = { clearUnselectedText() },
                                            modifier = buttonModifier,
                                            shape = buttonShape
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteSweep,
                                                contentDescription = "Clear Unselected"
                                            )
                                        }

                                        // Undo (hold to repeat)
                                        val undoInteractionSource = remember { MutableInteractionSource() }
                                        FilledTonalIconButton(
                                            onClick = { sendUndoOrRedo(redo = false) },
                                            modifier = buttonModifier.longPressableKey(
                                                interactionSource = undoInteractionSource,
                                                onLongPress = {
                                                    while (isActive) {
                                                        sendUndoOrRedo(redo = false)
                                                        delay(getKeyRepeatDelay().milliseconds)
                                                    }
                                                },
                                            ),
                                            shape = buttonShape,
                                            interactionSource = undoInteractionSource,
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.Undo,
                                                contentDescription = "Undo"
                                            )
                                        }

                                        // Redo (hold to repeat)
                                        val redoInteractionSource = remember { MutableInteractionSource() }
                                        FilledTonalIconButton(
                                            onClick = { sendUndoOrRedo(redo = true) },
                                            modifier = buttonModifier.longPressableKey(
                                                interactionSource = redoInteractionSource,
                                                onLongPress = {
                                                    while (isActive) {
                                                        sendUndoOrRedo(redo = true)
                                                        delay(getKeyRepeatDelay().milliseconds)
                                                    }
                                                },
                                            ),
                                            shape = buttonShape,
                                            interactionSource = redoInteractionSource,
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.Redo,
                                                contentDescription = "Redo"
                                            )
                                        }

                                        // Backspace (hold to repeat)
                                        val backspaceInteractionSource = remember { MutableInteractionSource() }
                                        FilledTonalIconButton(
                                            onClick = { backspaceOnce() },
                                            modifier = buttonModifier.longPressableKey(
                                                interactionSource = backspaceInteractionSource,
                                                onLongPress = {
                                                    while (isActive) {
                                                        backspaceOnce()
                                                        delay(getKeyRepeatDelay().milliseconds)
                                                    }
                                                },
                                            ),
                                            shape = buttonShape,
                                            interactionSource = backspaceInteractionSource,
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.Backspace,
                                                contentDescription = "Backspace"
                                            )
                                        }

                                        // Editor action (Send / Search / Next / ...)
                                        // TODO: why is the actionId seemingly always 0???
                                        val (actionKeyIcon, actionKeyContentDescription) = when (currentInputEditorInfo.actionId) {
                                            EditorInfo.IME_ACTION_NEXT -> Pair(
                                                Icons.AutoMirrored.Outlined.NavigateNext,
                                                "Next"
                                            )

                                            EditorInfo.IME_ACTION_GO -> Pair(
                                                Icons.AutoMirrored.Outlined.ArrowRightAlt,
                                                "Go"
                                            )

                                            EditorInfo.IME_ACTION_SEND -> Pair(
                                                Icons.AutoMirrored.Outlined.Send,
                                                "Send"
                                            )

                                            EditorInfo.IME_ACTION_DONE -> Pair(Icons.Outlined.Done, "Done")
                                            EditorInfo.IME_ACTION_NONE -> Pair(Icons.Outlined.Block, "None")
                                            EditorInfo.IME_ACTION_PREVIOUS -> Pair(
                                                Icons.AutoMirrored.Outlined.NavigateBefore,
                                                "Previous"
                                            )

                                            EditorInfo.IME_ACTION_SEARCH -> Pair(
                                                Icons.Outlined.Search,
                                                "Search"
                                            )

                                            else -> Pair(Icons.AutoMirrored.Outlined.Send, "Send")
                                        }

                                        FilledTonalIconButton(
                                            onClick = {
                                                if (actionKeyContentDescription == "Send") {
                                                    currentInputConnection.performEditorAction(EditorInfo.IME_ACTION_SEND)
                                                } else {
                                                    currentInputConnection.performEditorAction(
                                                        currentInputEditorInfo.actionId
                                                    )
                                                }
                                            },
                                            modifier = buttonModifier,
                                            shape = buttonShape
                                        ) {
                                            Icon(
                                                imageVector = actionKeyIcon,
                                                contentDescription = actionKeyContentDescription
                                            )
                                        }

                                        // Return / new line
                                        FilledTonalIconButton(
                                            onClick = {
                                                currentInputConnection.sendKeyEvent(
                                                    KeyEvent(
                                                        KeyEvent.ACTION_DOWN,
                                                        KeyEvent.KEYCODE_ENTER
                                                    )
                                                )
                                                currentInputConnection.sendKeyEvent(
                                                    KeyEvent(
                                                        KeyEvent.ACTION_UP,
                                                        KeyEvent.KEYCODE_ENTER
                                                    )
                                                )
                                            },
                                            modifier = buttonModifier,
                                            shape = buttonShape
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.KeyboardReturn,
                                                contentDescription = "Return"
                                            )
                                        }
                                    }

                                    if (useTwoRows) {
                                        // Phone held upright: two rows of bigger buttons.
                                        Column(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .fillMaxWidth()
                                                .fillMaxHeight(),
                                            verticalArrangement = Arrangement.spacedBy(CompactKeyboardSize.buttonSpacing)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(CompactKeyboardSize.buttonSpacing),
                                                content = firstRowButtons
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(CompactKeyboardSize.buttonSpacing),
                                                content = secondRowButtons
                                            )
                                        }
                                    } else {
                                        // Tablet, or phone held sideways: one thin row.
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .widthIn(max = maxStripWidth)
                                                .fillMaxWidth()
                                                .fillMaxHeight(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(CompactKeyboardSize.buttonSpacing)
                                        ) {
                                            firstRowButtons()
                                            secondRowButtons()
                                        }
                                    }
                                }
                            }
                        }
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }

        return view
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        voiceInputLifecycleOwner.onResume()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        voiceInputLifecycleOwner.onPause()
    }

    override fun onFinishInput() {
        speechRecognizer.value?.cancel()
        isRecognizing = false
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceInputLifecycleOwner.onDestroy()

        speechRecognizer.value?.cancel()
        speechRecognizer.value?.destroy()
        speechRecognizer.value = null
    }

    private fun getStartListeningIntent(longForm: Boolean): Intent {
        return Intent().apply {
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(MainRecognitionService.EXTRA_AUTO_STOP, longForm)
        }
    }

    /** Sends Ctrl+Z (undo) or Ctrl+Shift+Z (redo) to the text field. */
    private fun sendUndoOrRedo(redo: Boolean) {
        val downMetaState = if (redo) {
            KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON
        } else {
            KeyEvent.META_CTRL_ON
        }
        val upMetaState = 0

        currentInputConnection.sendKeyEvent(
            KeyEvent(
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_Z,
                0,
                downMetaState
            )
        )
        currentInputConnection.sendKeyEvent(
            KeyEvent(
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_Z,
                0,
                upMetaState
            )
        )
    }

    /** Deletes the selection, or one character before the cursor if nothing is selected. */
    private fun backspaceOnce() {
        val selectedText = currentInputConnection.getSelectedText(0)
        if (selectedText.isNullOrEmpty()) {
            currentInputConnection.deleteSurroundingText(1, 0)
        } else {
            currentInputConnection.commitText("", 1)
        }
    }

    /** Deletes all text in the field except what is selected. */
    private fun clearUnselectedText() {
        val extractedText = currentInputConnection.getExtractedText(
            ExtractedTextRequest(),
            0
        )?.text ?: return
        val beforeCursorText = currentInputConnection.getTextBeforeCursor(
            extractedText.length,
            0
        )
        val afterCursorText = currentInputConnection.getTextAfterCursor(
            extractedText.length,
            0
        )

        if (beforeCursorText != null) {
            if (afterCursorText != null) {
                currentInputConnection.deleteSurroundingText(
                    beforeCursorText.length,
                    afterCursorText.length
                )
            }
        }
    }
}

/**
 * Sizes for the compact voice keyboard. Change these numbers to make it bigger or smaller.
 * (dp = density-independent pixels; 48dp is roughly the height of a standard Android button.)
 */
private object CompactKeyboardSize {
    /** Height of the button strip when the device is held upright (portrait). */
    val stripHeightPortrait: Dp = 56.dp

    /** Height of the button strip when the device is sideways (landscape). */
    val stripHeightLandscape: Dp = 48.dp

    /**
     * Phones held upright are too narrow for one row, so they get two rows of bigger buttons.
     * This is the total height of both rows together.
     */
    val twoRowStripHeight: Dp = 120.dp

    /** Upright screens narrower than this (in dp) use two rows. Phones are under 600, tablets 600+. */
    const val twoRowMaxScreenWidthDp: Int = 600

    /** Tablet strip height when upright (portrait). */
    val tabletStripHeightPortrait: Dp = 60.dp

    /** Tablet strip height when sideways (landscape). */
    val tabletStripHeightLandscape: Dp = 52.dp

    /**
     * Minimum empty space kept under the buttons, so they don't cover Android's own
     * "hide keyboard" and "switch keyboard" buttons that it draws at the bottom of the screen.
     */
    val minBottomSpace: Dp = 48.dp

    /** Height used only when the keyboard has to show a message (accept policy / grant microphone). */
    val messagePanelHeight: Dp = 200.dp

    /** Empty space above and below the buttons. */
    val verticalPadding: Dp = 4.dp

    /** Gap between buttons. */
    val buttonSpacing: Dp = 6.dp

    /** On wide screens the strip stops growing past this width and stays centered. */
    val maxStripWidth: Dp = 720.dp

    /** The same limit on tablets (a little wider, so tablet buttons are bigger). */
    val tabletMaxStripWidth: Dp = 780.dp

    /** How many times wider the mic button is than the other buttons. */
    const val micButtonWeight: Float = 2.5f

    /** Size of the microphone icon. */
    val micIconSize: Dp = 28.dp
}

class VoiceInputLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry: LifecycleRegistry =
        LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    override val viewModelStore: ViewModelStore = ViewModelStore()

    private val savedStateRegistryController =
        SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry =
        savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onResume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onPause() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }

    fun attachToDecorView(decorView: View?) {
        if (decorView == null) return

        decorView.setViewTreeLifecycleOwner(this)
        decorView.setViewTreeViewModelStoreOwner(this)
        decorView.setViewTreeSavedStateRegistryOwner(this)
    }
}
