package kr.com.chappiet.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kr.com.chappiet.R
import kr.com.chappiet.ui.theme.Typography
import kr.com.chappiet.ui.theme.bg_warning_color
import kr.com.chappiet.ui.theme.bg_white
import kr.com.chappiet.ui.theme.gray_100
import kr.com.chappiet.ui.theme.primary
import kr.com.chappiet.ui.theme.sign_caption
import kr.com.chappiet.ui.theme.sign_primary
import kr.com.chappiet.ui.theme.sign_tertiary
import kr.com.chappiet.ui.theme.warning
import kr.com.chappiet.ui.theme.white
import kotlin.Int as Int1


@Composable
fun BasicText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int1 = Int1.MAX_VALUE,
    minLines: Int1 = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = Typography.bodyMedium
) {
    Text(text,
        modifier,
        color,
        fontSize,
        fontStyle,
        fontWeight,
        fontFamily,
        letterSpacing,
        textDecoration,
        textAlign,
        lineHeight,
        overflow,
        softWrap,
        maxLines,
        minLines,
        onTextLayout,
        style)
}

@Composable
fun BasicText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int1 = Int1.MAX_VALUE,
    minLines: Int1 = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: ((TextLayoutResult) -> Unit) = { },
    style: TextStyle = Typography.bodyMedium
) {
    Text(text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style,
        inlineContent = inlineContent)
}


/**
 * Custom BasicTextField for modify Border Thickness
 * reference
 * @see OutlinedTextField
 *
 * @param info 텍스트 필드 아래 정보 입력
 * @param placeholder 텍스트 필드 내부 힌트 텍스트
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTextField(
    modifier: Modifier = Modifier,
    columnModifier: Modifier = Modifier,
    value: String,
    info: String? = null,
    showCount: Boolean = false,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int1 = if (singleLine) 1 else Int1.MAX_VALUE,
    minLines: Int1 = 1,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused = remember { mutableStateOf(false) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is FocusInteraction.Focus -> {
                    if(!readOnly) isFocused.value = true
                }
                is FocusInteraction.Unfocus -> {
                    isFocused.value = false
                }
            }
        }
    }
    val colors = if (isFocused.value) {
        OutlinedTextFieldDefaults.colors(
            focusedLabelColor = sign_tertiary,
            unfocusedLabelColor = sign_tertiary,
            focusedBorderColor = sign_primary,
            unfocusedBorderColor = Color.Unspecified,
            unfocusedPlaceholderColor = sign_caption,
            focusedPlaceholderColor = sign_caption,
            errorPlaceholderColor = sign_caption,
            disabledPlaceholderColor = sign_caption
        )
    } else {
        OutlinedTextFieldDefaults.colors(
            focusedLabelColor = sign_tertiary,
            unfocusedLabelColor = sign_tertiary,
            focusedBorderColor = Color.Unspecified,
            unfocusedBorderColor = Color.Unspecified,
            errorBorderColor = warning,
            errorTrailingIconColor = warning,
            unfocusedPlaceholderColor = sign_caption,
            focusedPlaceholderColor = sign_caption,
            errorPlaceholderColor = sign_caption,
            disabledPlaceholderColor = sign_caption
        )
    }
    Column (modifier = columnModifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = if (label != null) {
                Modifier
                    // Merge semantics at the beginning of the modifier chain to ensure padding is
                    // considered part of the text field.
                    .semantics(mergeDescendants = true) {}
                    .padding(top = 8.dp)
            } else {
                Modifier
            }
                .defaultMinSize(
                    minWidth = OutlinedTextFieldDefaults.MinWidth,
                    minHeight = OutlinedTextFieldDefaults.MinHeight
                )
                .then(
                    if (isError) {
                        Modifier.background(bg_warning_color, shape)
                    } else if (isFocused.value) {
                        Modifier.background(bg_white, shape)
                    } else {
                        Modifier.background(gray_100, shape)
                    }
                ).then(modifier),
            cursorBrush = SolidColor(primary),
            enabled = enabled,
            readOnly = readOnly,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
        ) {
                innerTextField -> OutlinedTextFieldDefaults.DecorationBox(
            value = value,
            innerTextField = innerTextField,
            label = if(isError){ null } else if(isFocused.value) { label } else { null },
            leadingIcon = leadingIcon,
            trailingIcon = if(isError){
                {
                    IconButton(onClick = {
                        onValueChange("")
                    }){
                        Icon(imageVector = ImageVector.vectorResource(id = R.drawable.warning),
                            tint = warning,
                            contentDescription = stringResource(id = R.string.warning))
                    }
                }
            }else if(isFocused.value) {
                {
                    IconButton(onClick = {
                        onValueChange("")
                    }){
                        Icon(imageVector = ImageVector.vectorResource(id = R.drawable.backspace),
                            tint = sign_tertiary,
                            contentDescription = stringResource(id = R.string.cancel))
                    }
                }
            } else { null },
            prefix = prefix,
            suffix = suffix,
            supportingText = null,
            enabled = true,
            singleLine = true,
            isError = isError,
            interactionSource = interactionSource,
            visualTransformation = VisualTransformation.None,
            placeholder = placeholder,
            colors = colors,
            container = {
                OutlinedTextFieldDefaults.ContainerBox(
                    enabled = true,
                    isError = isError,
                    interactionSource = interactionSource,
                    colors = colors,
                    shape = shape,
                    focusedBorderThickness = 1.dp,
                    unfocusedBorderThickness = 1.dp
                )
            })
        }
        Row {
            info?.let{
                BasicText(
                    modifier = Modifier.padding(start = 4.dp),
                    text = info,
                    color = if(isError) { warning } else { sign_caption },
                    style = Typography.labelMedium)
            }
        }
    }
}


@Preview
@Composable
fun TextComponentPreview() {
    Column (modifier = Modifier
        .background(white)
        .fillMaxSize()){
        CustomTextField(
            value = "default",
            label = {BasicText(text = "label")},
            info = "caption",
            onValueChange = {}
        )
        CustomTextField(
            value = "error",
            label = {BasicText(text = "label")},
            onValueChange = {},
            info = "caption",
            isError = true
        )
        CustomTextField(
            value = "readOnly",
            label = {BasicText(text = "label")},
            onValueChange = {},
            readOnly = true
        )
        CustomTextField(
            value = "",
            label = {BasicText(text = "label")},
            onValueChange = {},
            placeholder = { BasicText(text = "place holder")}
        )
    }
}