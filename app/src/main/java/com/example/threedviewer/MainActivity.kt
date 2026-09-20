package com.example.threedviewer

import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled._3dRotation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.romainguy.kotlin.math.Float3
import kotlin.math.roundToInt
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.utils.worldToScreen

@Parcelize
private data class ViewerModel(
    val id: Long,
    val file: String,
    val title: String,
    val x: Float,
    val y: Float,
    val size: Float,
    val rotateMode: Boolean,
    val labels: Boolean,
    val rotationY: Float,
    val scale: Float
) : Parcelable

private val files =
    listOf("solarsystem.glb", "Bulb.glb", "Fiagena.glb", "Lungs.glb", "Microscope.glb")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThreeDModelViewerTheme {
                ViewerApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerApp() {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    var models by rememberSaveable() { mutableStateOf(listOf<ViewerModel>()) }
    var nextId by rememberSaveable() { mutableLongStateOf(1L) }
    Scaffold(topBar = { TopAppBar(title = { Text("3D Model Viewer") }) }, floatingActionButton = {
        if (models.size < files.size) {
            FloatingActionButton(onClick = {
                val file = files[models.size]
                val stagger = models.size % 5
                models = models + ViewerModel(
                    id = nextId++,
                    file = file,
                    title = file.removeSuffix(".glb"),
                    x = 20f + stagger * 40f,
                    y = 80f + stagger * 40f,
                    size = 220f,
                    rotateMode = false,
                    labels = false,
                    rotationY = 0f,
                    scale = 1f
                )
            }) {
                Icon(Icons.Default.Add, "Add model")
            }
        }
    }) { padding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val constraintsMaxWidth = this.maxWidth
            val constraintsMaxHeight = this.maxHeight
            models.forEach { model ->
                key(model.id) {
                    ModelCard(
                        model = model,
                        engine = engine,
                        modelLoader = modelLoader,
                        containerMaxWidth = constraintsMaxWidth,
                        containerMaxHeight = constraintsMaxHeight,
                        onChange = { changed ->
                            models = models.map { if (it.id == changed.id) changed else it }
                        },
                        onClose = { models = models.filterNot { it.id == model.id } },
                        onBringToFront = {
                            if (models.lastOrNull()?.id != model.id) {
                                models = models.filterNot { it.id == model.id } + model
                            }
                        })
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: ViewerModel,
    engine: com.google.android.filament.Engine,
    modelLoader: io.github.sceneview.loaders.ModelLoader,
    containerMaxWidth: androidx.compose.ui.unit.Dp,
    containerMaxHeight: androidx.compose.ui.unit.Dp,
    onChange: (ViewerModel) -> Unit,
    onClose: () -> Unit,
    onBringToFront: () -> Unit
) {
    val nodes = rememberNodes()
    var rootNode by remember { mutableStateOf<ModelNode?>(null) }

    val density = androidx.compose.ui.platform.LocalDensity.current
    var localX by rememberSaveable(model.id) {
        mutableFloatStateOf(with(density) { model.x.dp.toPx() })
    }
    var localY by rememberSaveable(model.id) {
        mutableFloatStateOf(with(density) { model.y.dp.toPx() })
    }
    var localSize by rememberSaveable(model.id) { mutableFloatStateOf(model.size) }
    var localRotationY by rememberSaveable(model.id) { mutableFloatStateOf(model.rotationY) }
    var localScale by rememberSaveable(model.id) { mutableFloatStateOf(model.scale) }

    LaunchedEffect(model.x, model.y, model.size, model.rotationY, model.scale) {
        localX = with(density) { model.x.dp.toPx() }
        localY = with(density) { model.y.dp.toPx() }
        localSize = model.size
        localRotationY = model.rotationY
        localScale = model.scale
    }

    LaunchedEffect(model.file) {
        rootNode?.let { nodes.remove(it) }
        val instance: ModelInstance = modelLoader.createModelInstance(model.file)
        rootNode = ModelNode(
            modelInstance = instance,
            scaleToUnits = 1.0f,
            centerOrigin = Position(x = 0.0f, y = 0.0f, z = 0.0f)
        ).also { nodes += it }
    }

    fun updateNodeTransform(node: ModelNode, rotY: Float, zoom: Float) {
        node.rotation = Float3(0f, rotY, 0f)
        // Decouple model scale from container size to prevent "scale-to-fit" behavior.
        // The model now maintains a constant base scale (1.0f) regardless of container resizing.
        node.scaleToUnitCube(1.0f)
        val baseScale = node.scale
        node.scale = baseScale * zoom
        node.position = -(node.quaternion * (node.center * node.scale))
    }

    LaunchedEffect(rootNode, localSize, localRotationY, localScale) {
        rootNode?.let { updateNodeTransform(it, localRotationY, localScale) }
    }

    var labeledNodes by remember { mutableStateOf(listOf<Pair<io.github.sceneview.node.Node, String>>()) }
    val screenPositions = remember { mutableStateMapOf<io.github.sceneview.node.Node, Offset>() }
    val cameraNode = io.github.sceneview.rememberCameraNode(engine)

    LaunchedEffect(rootNode) {
        val nodesList = rootNode?.nodes ?: emptyList()
        val pairs = mutableListOf<Pair<io.github.sceneview.node.Node, String>>()
        for (node in nodesList) {
            if (node is ModelNode.ChildNode) {
                val extrasStr = node.extras
                if (extrasStr != null) {
                    val propValue = try {
                        val regex = "\"prop\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                        regex.find(extrasStr)?.groupValues?.get(1)
                    } catch (_: Exception) {
                        null
                    }

                    if (propValue != null) {
                        pairs.add(node to propValue)
                    }
                }
            }
        }
        labeledNodes = pairs
    }

    DisposableEffect(model.id) {
        onDispose {
            rootNode?.let {
                nodes.remove(it)
                it.destroy()
            }
        }
    }

    val currentModel by rememberUpdatedState(model)
    val currentOnChange by rememberUpdatedState(onChange)

    Box(Modifier
        .offset {
            IntOffset(localX.roundToInt(), localY.roundToInt())
        }
        .size(localSize.dp)
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
        .clip(RoundedCornerShape(8.dp))
        .pointerInput(model.id) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                onBringToFront()
            }
        }) {
        Column(Modifier.fillMaxSize()) {
            // Header area placeholder to prevent 3D content from overlapping buttons
            Spacer(Modifier.height(56.dp))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                Scene(
                    engine = engine,
                    modelLoader = modelLoader,
                    cameraNode = cameraNode,
                    cameraManipulator = if (model.rotateMode) io.github.sceneview.rememberCameraManipulator() else null,
                    onGestureListener = if (model.rotateMode) io.github.sceneview.rememberOnGestureListener() else null,
                    childNodes = nodes,
                    modifier = Modifier.fillMaxSize(),
                    onViewCreated = {
                        setZOrderMediaOverlay(true)
                    },
                    onFrame = { _ ->
                        if (model.labels) {
                            labeledNodes.forEach { (node, _) ->
                                val wp = node.worldPosition
                                cameraNode.view?.worldToScreen(Float3(wp.x, wp.y, wp.z))?.let { screenPt ->
                                    screenPositions[node] = Offset(screenPt.x, screenPt.y)
                                }
                            }
                        }
                    })

                if (model.labels) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        labeledNodes.forEach { (node, _) ->
                            val pos = screenPositions[node]
                            // Boundary check in local pixels
                            if (pos != null && pos.x >= 0 && pos.y >= 0 && pos.x <= size.width && pos.y <= size.height) {
                                drawCircle(color = primaryColor, radius = 4.dp.toPx(), center = pos)
                                drawLine(
                                    color = primaryColor,
                                    start = pos,
                                    end = Offset(pos.x + 20.dp.toPx(), pos.y - 12.dp.toPx()),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                            }
                        }
                    }

                    labeledNodes.forEach { (node, labelText) ->
                        val pos = screenPositions[node]
                        if (pos != null && pos.x >= 0 && pos.y >= 0) {
                            Box(modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (pos.x + 20.dp.toPx()).toInt(), (pos.y - 24.dp.toPx()).toInt()
                                    )
                                }
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp)) {
                                Text(labelText, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                            }
                        }
                    }
                }

                if (model.labels) Text(
                    model.title,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(4.dp)
                )
            }
        }

        // Gesture Overlay - Captures all touch events reliably in Normal Mode
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(model.id, model.rotateMode) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (model.rotateMode) {
                            localRotationY += pan.x * .5f
                            localScale = (localScale * zoom).coerceIn(.3f, 4f)
                            rootNode?.let {
                                updateNodeTransform(
                                    it, localRotationY, localScale
                                )
                            }
                        } else {
                            val cardSizePx = localSize.dp.toPx()
                            val maxXPx = containerMaxWidth.toPx() - cardSizePx
                            val maxYPx = containerMaxHeight.toPx() - cardSizePx

                            localX = (localX + pan.x).coerceIn(0f, maxXPx)
                            localY = (localY + pan.y).coerceIn(0f, maxYPx)

                            val newSize = (localSize * zoom).coerceIn(160f, 420f)
                            val maxAllowedSize = minOf(
                                containerMaxWidth.value - (localX / density.density),
                                containerMaxHeight.value - (localY / density.density)
                            )
                            localSize = newSize.coerceAtMost(maxAllowedSize)

                            rootNode?.let {
                                updateNodeTransform(
                                    it, localRotationY, localScale
                                )
                            }
                        }
                    }
                    currentOnChange(
                        currentModel.copy(
                            x = localX / density.density,
                            y = localY / density.density,
                            size = localSize,
                            rotationY = localRotationY,
                            scale = localScale
                        )
                    )
                })

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(onClick = { onChange(model.copy(rotateMode = !model.rotateMode)) }) {
                Icon(
                    Icons.Default._3dRotation,
                    "Toggle interaction",
                    tint = if (model.rotateMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { onChange(model.copy(labels = !model.labels)) }) {
                Icon(
                    Icons.AutoMirrored.Filled.Label,
                    "Toggle labels",
                    tint = if (model.labels) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun ThreeDModelViewerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFFD0BCFF),
            secondary = Color(0xFFCCC2DC),
            tertiary = Color(0xFFEFB8C8),
            background = Color(0xFF1C1B1F),
            surface = Color(0xFF1C1B1F),
            onPrimary = Color(0xFF381E72),
            onSecondary = Color(0xFF332D41),
            onTertiary = Color(0xFF492532),
            onBackground = Color(0xFFE6E1E5),
            onSurface = Color(0xFFE6E1E5),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF6750A4),
            secondary = Color(0xFF625B71),
            tertiary = Color(0xFF7D5260),
            background = Color(0xFFFFFBFE),
            surface = Color(0xFFFFFBFE),
            onPrimary = Color(0xFFFFFFFF),
            onSecondary = Color(0xFFFFFFFF),
            onTertiary = Color(0xFFFFFFFF),
            onBackground = Color(0xFF1C1B1F),
            onSurface = Color(0xFF1C1B1F),
        )
    }

    MaterialTheme(
        colorScheme = colorScheme, content = content
    )
}
