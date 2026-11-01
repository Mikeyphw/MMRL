package dev.mmrlx.compose.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import dev.mmrlx.terminal.TerminalEmulator

@Composable
fun Terminal(
    modifier: Modifier = Modifier,
    onEmulatorCreated: (TerminalEmulator) -> Unit,
) {
    val emulator = remember { TerminalEmulator() }
    val lines by emulator.lines.collectAsState()
    val listState = rememberLazyListState()
    val contentColor = LocalContentColor.current

    LaunchedEffect(emulator) {
        onEmulatorCreated(emulator)
    }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.scrollToItem(lines.lastIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent),
    ) {
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                itemsIndexed(
                    items = lines,
                    key = { index, _ -> index },
                ) { _, line ->
                    BasicText(
                        text = line.ifEmpty { " " },
                        style = TextStyle(
                            color = contentColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        ),
                    )
                }
            }
        }
    }
}
