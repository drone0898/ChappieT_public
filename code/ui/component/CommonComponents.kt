package kr.com.chappiet.ui.component

import androidx.annotation.IntRange
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartlook.android.core.api.extension.smartlook
import kr.com.chappiet.R
import kr.com.chappiet.ui.theme.Typography
import kr.com.chappiet.ui.theme.bg_brand_color
import kr.com.chappiet.ui.theme.bg_button
import kr.com.chappiet.ui.theme.bg_sub
import kr.com.chappiet.ui.theme.bg_tertiary
import kr.com.chappiet.ui.theme.primary
import kr.com.chappiet.ui.theme.sign_caption
import kr.com.chappiet.ui.theme.sign_primary
import kr.com.chappiet.ui.theme.sign_quaternary
import kr.com.chappiet.ui.theme.sign_secondary
import kr.com.chappiet.ui.theme.sign_tertiary
import kr.com.chappiet.ui.theme.white
import kr.com.chappiet.util.toFirstDecimalPlace

@Composable
fun HorizontalDivider1(padding:Int = 0, color:Color = bg_tertiary) {
    HorizontalDivider(modifier = Modifier.padding(vertical = padding.dp),
        thickness = 1.dp, color = color)
}

@Composable
fun HorizontalDivider8(padding:Int = 0, color:Color = bg_tertiary) {
    HorizontalDivider(modifier = Modifier.padding(vertical = padding.dp),
        thickness = 8.dp, color = color)
}

@Composable
fun VerticalDivider1(modifier:Modifier = Modifier, padding:Int = 0, color: Color = bg_tertiary) {
    VerticalDivider(modifier = modifier.padding(vertical = padding.dp),
        thickness = 1.dp, color = color)
}

@Composable
fun BasicCheckBox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors(
        checkedColor = primary,
        uncheckedColor = sign_primary,
        checkmarkColor = white
    ),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    Checkbox(checked, onCheckedChange, modifier, enabled, colors, interactionSource)
}

@Composable
fun SliderWithLabel(
    modifier: Modifier = Modifier,
    value: Float = 3f,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 3f..15f,
    @IntRange(from = 0)
    steps: Int = 1,
    labelText: String = stringResource(id = R.string.max_voice_active_length),
    labelUnit: String = stringResource(id = R.string.second)
    ) {
    Column (modifier = Modifier
        .fillMaxWidth()
        .then(modifier)) {
        Row (modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween){
            BasicText(
                text = labelText,
                style = Typography.bodyMedium,
                color = sign_secondary
            )
            BasicText(text = "${value.toFirstDecimalPlace()}$labelUnit",
                style = Typography.labelMedium,
                color = primary
            )
        }
        CustomSlider(value = value,
            onValueChange = {
                onValueChange(it)
            },
            valueRange = valueRange,
            steps = steps)
    }
}

@Composable
fun CustomSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    @IntRange(from = 0)
    steps: Int = 0,
) {
    Slider(value = value,
        valueRange = valueRange,
        steps = steps,
        onValueChange = onValueChange,
        colors = SliderDefaults.colors(
            thumbColor = primary,
            activeTrackColor = primary,
            activeTickColor = Color.Transparent,
            inactiveTrackColor = bg_tertiary,
            inactiveTickColor = bg_tertiary,
            disabledActiveTrackColor = sign_caption,
            disabledThumbColor = sign_caption,
            disabledInactiveTrackColor = bg_tertiary
        ),)
}

@Composable
fun StepIndicator(
    modifier: Modifier = Modifier,
    step: Int,
    maxStep: Int,
    selectedColor: Color = primary,
    defaultColor: Color = sign_caption,
    defaultRadius: Dp = 8.dp,
    selectedLength: Dp = 40.dp,
    space: Dp = 8.dp,
    animationDurationInMillis: Int = 300,
    ) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space),
        modifier = modifier,
    ) {
        for (i in 1 .. maxStep) {
            val isSelected = i == step
            StepIndicatorCanvas(
                isSelected = isSelected,
                selectedColor = selectedColor,
                defaultColor = defaultColor,
                defaultRadius = defaultRadius,
                selectedLength = selectedLength,
                animationDurationInMillis = animationDurationInMillis,
            )
        }
    }
}

@Composable
internal fun StepIndicatorCanvas(
    isSelected: Boolean,
    selectedColor: Color,
    defaultColor: Color,
    defaultRadius: Dp,
    selectedLength: Dp,
    animationDurationInMillis: Int,
    modifier: Modifier = Modifier,
) {
    val color: Color by animateColorAsState(
        targetValue = if (isSelected) {
            selectedColor
        } else {
            defaultColor
        },
        animationSpec = tween(
            durationMillis = animationDurationInMillis,
        ), label = ""
    )
    val width: Dp by animateDpAsState(
        targetValue = if (isSelected) {
            selectedLength
        } else {
            defaultRadius
        },
        animationSpec = tween(
            durationMillis = animationDurationInMillis,
        ), label = ""
    )

    Canvas(
        modifier = modifier
            .size(
                width = width,
                height = defaultRadius,
            ),
    ) {
        drawRoundRect(
            color = color,
            topLeft = Offset.Zero,
            size = Size(
                width = width.toPx(),
                height = defaultRadius.toPx(),
            ),
            cornerRadius = CornerRadius(
                x = defaultRadius.toPx(),
                y = defaultRadius.toPx(),
            ),
        )
    }
}

@Composable
fun AlertCard(
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    icon: ImageVector = ImageVector.vectorResource(R.drawable.warning_off),
    iconTint: Color = sign_quaternary,
    text: String
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier.weight(1f))
        Icon(
            modifier = Modifier.padding(8.dp),
            imageVector = icon,
            contentDescription = text,
            tint = iconTint
        )
        BasicText(
            modifier = Modifier.padding(8.dp),
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = sign_primary
        )
        Spacer(modifier.weight(1f))
    }
}

@Composable
fun ApiKeyCard(
    modifier:Modifier = Modifier,
    name: String,
    key: String,
    type: String,
    enabled:Boolean = false,
    onCheckedChange: ((Boolean) -> Unit),
    onClickModify: () -> Unit = {},
    creationDate: String? = null,
    useDate: String? = null,
) {
    val backgroundColor = if(enabled) {
        bg_brand_color
    } else {
        bg_sub
    }
    val contentColor = if(enabled) {
        primary
    } else {
        sign_quaternary
    }
    Card (modifier = modifier
        .fillMaxWidth()
        .height(200.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)) {
        Column (modifier.padding(horizontal = 24.dp, vertical = 16.dp)){
            Row (Modifier.padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SwitchComponent(checked = enabled, onCheckedChange = onCheckedChange)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onClickModify) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.settings_dot),
                        tint = sign_tertiary,
                        contentDescription = stringResource(id = R.string.modify))
                }
            }
            Row {
                BasicText(text = name,
                    style = Typography.titleLarge)
            }
            BasicText(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .smartlook(
                        isSensitive = true
                    ),
                text = maskLastCharacters(key,8),
                color = sign_tertiary,
                style = Typography.bodyMedium,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
                )
            Spacer(modifier = modifier.weight(1f))
            HorizontalDivider1(padding = 4)
            Spacer(modifier = modifier.weight(1f))
            Row (verticalAlignment = Alignment.CenterVertically){
                Column {
                    BasicText(text = stringResource(id = R.string.creation_date),
                        color = sign_quaternary,
                        style = Typography.labelMedium)
                    BasicText(text = creationDate ?: "",
                        color = sign_quaternary,
                        style = Typography.labelMedium)
                }
                Spacer(modifier = Modifier.weight(1f))
                Column {
                    BasicText(text = stringResource(id = R.string.usage_date),
                        color = sign_quaternary,
                        style = Typography.labelMedium)
                    BasicText(text = useDate ?: "",
                        color = contentColor,
                        style = Typography.labelMedium)
                }
                Spacer(modifier = Modifier.weight(1f))
                Column {
                    BasicText(text = stringResource(id = R.string.api_type),
                        color = sign_quaternary,
                        style = Typography.labelMedium)
                    BasicText(text = type,
                        color = sign_quaternary)
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun PromotionCard(
    modifier:Modifier = Modifier,
    name: String,
    content: String,
    expiredDate: String,
    useDate: String? = null,
    enabled:Boolean = true,
    onClickUse: () -> Unit = {},
) {
    val buttonText = if(enabled) {
        stringResource(id = R.string.use)
    } else if(useDate==null){
        stringResource(id = R.string.disabled)
    } else {
        "$useDate ${stringResource(id = R.string.just_use)}"
    }
    Card (modifier = modifier
        .fillMaxWidth()
        .height(200.dp),
        colors = CardDefaults.cardColors(containerColor = bg_sub)) {
        Column {
            Column (modifier.padding(horizontal = 16.dp, vertical = 16.dp)){
                BasicText(modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth(),
                    text = name, style = Typography.headlineMedium, color = if(enabled) {
                    primary
                } else {
                    sign_caption
                },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                BasicText(modifier = Modifier.padding(bottom = 8.dp),
                    text = content, style = Typography.titleSmall, color = if(enabled) {
                    sign_secondary
                } else {
                    sign_caption
                })
                Row (modifier = Modifier.fillMaxWidth()){
                    BasicText(
                        modifier = Modifier.padding(end = 4.dp),
                        text = stringResource(id = R.string.expired_date), style = Typography.labelMedium, color = if(enabled) {
                        sign_quaternary
                    } else {
                        sign_caption
                    })
                    BasicText(text = expiredDate, style = Typography.labelMedium, color = if(enabled) {
                        sign_tertiary
                    } else {
                        sign_caption
                    })
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            ButtonSecondary(modifier = Modifier.fillMaxWidth(),
                accent = enabled,
                enabled = enabled,
                disabledColor = bg_button,
                onClick = onClickUse,
                text = buttonText)
        }
    }
}

@Composable
internal fun maskLastCharacters(input: String, mask:Int): String {
    val len = input.length
    return if (len <= mask) {
        "*".repeat(len)
    } else {
        input.substring(0, len - mask) + "*".repeat(mask)
    }
}

@Preview
@Composable
fun ApiCardPreview() {
    Column (Modifier.background(white)) {
//        ApiKeyCard(name = "API KEY NAME",
//            key = "sk-abcdeflaHqn2lPfFZFzIRT3BlbkFJHMT9FFqp2j4BhNdsmfgeabcdef",
//            type = "DeepL",
//            onCheckedChange = {})
//        ApiKeyCard(name = "API KEY NAME",
//            enabled = true,
//            key = "sk-abcdeflaHqn2lPfFZFzIRT3BlbkFJHMT9FFqp2j4BhNdsmfgeabcdef",
//            type = "DeepL",
//            onCheckedChange = {})
//        AlertCard(text = "등록된 API Key가 없어요")
//        StepIndicator(step = 1, maxStep = 3)
        PromotionCard(name = "API 등록", content = "API 등록", expiredDate = "2023/03/21")
        PromotionCard(name = "API 등록", content = "API 등록", expiredDate = "2023/03/21",
            enabled = false)
        PromotionCard(name = "API 등록", content = "API 등록", expiredDate = "2023/03/21",
            enabled = false, useDate = "23.04.12")
    }
}
