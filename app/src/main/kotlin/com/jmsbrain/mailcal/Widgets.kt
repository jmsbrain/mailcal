package com.jmsbrain.mailcal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

@Composable
fun TextButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    BasicText(
        label,
        style = buttonStyle().copy(color = if (enabled) LightStyle.content else LightStyle.secondary),
        modifier = Modifier
            .flatClickable { if (enabled) onClick() }
            .padding(vertical = designDp(6f)),
    )
}

/** Top row of buttons, left and right. */
@Composable
fun TopBar(left: @Composable () -> Unit, right: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = designDp(24f), vertical = designDp(12f))) {
        left()
        Spacer(Modifier.weight(1f))
        right()
    }
}

@Composable
fun Gap(px: Float) = Spacer(Modifier.height(designDp(px)))

@Composable
fun HGap(px: Float) = Spacer(Modifier.width(designDp(px)))

/** Labelled, underlined text field. */
@Composable
fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    singleLine: Boolean = true,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    val style = paragraphStyle()
    Column(Modifier.fillMaxWidth().padding(vertical = designDp(8f))) {
        BasicText(label, style = labelStyle())
        Gap(4f)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = style,
            cursorBrush = SolidColor(LightStyle.content),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                capitalization = if (password) KeyboardCapitalization.None else capitalization,
                keyboardType = if (password) KeyboardType.Password else keyboardType,
                autoCorrectEnabled = !password && keyboardType == KeyboardType.Text,
            ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) BasicText(placeholder, style = style.copy(color = LightStyle.secondary))
                    inner()
                }
            },
        )
        Gap(4f)
        Box(Modifier.fillMaxWidth().height(1.dp).background(LightStyle.secondary))
    }
}

/** A label over a tappable value, e.g. STARTS / Tue, Oct 14. */
@Composable
fun ValueRow(label: String, value: String, onClick: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.flatClickable(onClick) else it }
            .padding(vertical = designDp(8f)),
    ) {
        BasicText(label, style = labelStyle())
        Gap(2f)
        BasicText(value, style = paragraphStyle())
    }
}

/** Text button with a dropdown menu. */
@Composable
fun MenuButton(label: String, items: List<Pair<String, () -> Unit>>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        val focus = LocalFocusManager.current
        TextButton(label) {
            // Keeps the keyboard from coming back when the menu closes.
            focus.clearFocus()
            expanded = true
        }
        if (expanded) {
            Popup(
                popupPositionProvider = DropdownPosition(alignEnd = true),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                MenuList(items.map { (text, action) -> text to { expanded = false; action() } })
            }
        }
    }
}

/** Menu below its anchor, or above if it doesn't fit. */
class DropdownPosition(private val alignEnd: Boolean) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val left = if (alignEnd) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val x = left.coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
        val fitsBelow = anchorBounds.bottom + popupContentSize.height <= windowSize.height
        val y = if (fitsBelow) anchorBounds.bottom else maxOf(0, anchorBounds.top - popupContentSize.height)
        return IntOffset(x, y)
    }
}

@Composable
fun MenuList(items: List<Pair<String, () -> Unit>>) {
    Column(
        Modifier
            .background(LightStyle.background)
            .border(1.dp, LightStyle.content)
            .heightIn(max = designDp(440f))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = designDp(16f), vertical = designDp(6f)),
    ) {
        items.forEach { (text, onClick) ->
            BasicText(
                text,
                style = buttonStyle(),
                modifier = Modifier.flatClickable(onClick).padding(vertical = designDp(8f)),
            )
        }
    }
}

@Composable
fun Note(text: String) = BasicText(text, style = paragraphStyle().copy(color = LightStyle.secondary))
