/*
 * SPDX-FileCopyrightText: 2026 AlphaDroid
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.axion.compose.preferences

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.axion.compose.R
import kotlin.math.roundToInt

/**
 * A Compose equivalent of [CustomSeekBarPreference] from AlphaSettings, designed to be
 * consumed by both AxThemePicker and AlphaVisuals without any android.preference dependencies.
 *
 * Features ported from the Java original:
 *  - Signed / unit-suffixed value label via [formatValue]
 *  - Optional [defaultValue] with reset button (short-press = toast hint, long-press = reset)
 *  - [interval] stepping: thumb snaps in [interval]-unit increments
 *  - −/+ step buttons that dim at their respective bounds
 *  - Long-press −/+ jumps to midpoint or extreme, matching the Java logic
 *  - Live tracking display while dragging (value shown in brackets)
 *  - [continuousUpdates] — when true, [onValueChange] fires on every drag frame;
 *    when false, only fires on release (matches [mContinuousUpdates])
 *  - [enabled] propagated to all interactive children
 *  - Optional [position] for [PreferenceGroup] rounded-corner shaping
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CustomSeekBar(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    max: Int,
    modifier: Modifier = Modifier,
    interval: Int = 1,
    defaultValue: Int? = null,
    enabled: Boolean = true,
    continuousUpdates: Boolean = false,
    formatValue: (Int) -> String = { it.toString() },
    position: PreferencePosition = LocalPreferencePosition.current,
) {
    require(max >= min) { "max ($max) must be >= min ($min)" }
    require(interval > 0) { "interval must be positive" }

    val shape = preferenceShape(position)
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val contentAlpha = if (enabled) 1f else 0.38f

    // Steps for Compose Slider: number of discrete ticks between min and max exclusive.
    // Formula: total intervals - 1 = (max - min) / interval - 1
    val steps = ((max - min) / interval - 1).coerceAtLeast(0)

    // Internal slider state (Float for smooth Slider API)
    var sliderValue by remember(value) { mutableStateOf(value.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    // Snap the raw slider float to the nearest interval-aligned int
    fun snapped(raw: Float): Int =
        (((raw - min) / interval).roundToInt() * interval + min).coerceIn(min, max)

    val displayedValue = if (isDragging && !continuousUpdates) {
        "[${formatValue(snapped(sliderValue))}]"
    } else {
        formatValue(snapped(sliderValue))
    }

    val canDecrement = enabled && snapped(sliderValue) > min
    val canIncrement = enabled && snapped(sliderValue) < max
    val showReset = enabled && defaultValue != null && snapped(sliderValue) != defaultValue

    val resetHint = stringResource(R.string.long_press_to_reset)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceBright),
    ) {
        // ── Header row: title · value label · reset icon ─────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .alpha(contentAlpha)
                .padding(start = 16.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            )

            Text(
                text = displayedValue,
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 56.dp),
            )

            // Reset button — visible only when value differs from defaultValue
            if (defaultValue != null) {
                val resetAlpha by animateColorAsState(
                    targetValue = if (showReset)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0f),
                    label = "resetAlpha",
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            enabled = showReset,
                            onClick = {
                                Toast
                                    .makeText(context, resetHint, Toast.LENGTH_SHORT)
                                    .show()
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                sliderValue = defaultValue.toFloat()
                                onValueChange(defaultValue)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = resetAlpha,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // ── Slider + step buttons ─────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Minus button: short-press = step down; long-press = jump to min (or midpoint)
            IconButton(
                onClick = {
                    val next = snapped(sliderValue) - interval
                    sliderValue = next.coerceAtLeast(min).toFloat()
                    onValueChange(snapped(sliderValue))
                },
                enabled = canDecrement,
                modifier = Modifier
                    .size(36.dp)
                    .combinedClickable(
                        enabled = canDecrement,
                        onClick = {
                            val next = snapped(sliderValue) - interval
                            sliderValue = next.coerceAtLeast(min).toFloat()
                            onValueChange(snapped(sliderValue))
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            // Mirror Java: jump to midpoint if current is above it, else to min
                            val mid = (max + min) / 2
                            val target = if (max - min > interval * 2 && max + min < snapped(sliderValue) * 2)
                                mid else min
                            sliderValue = target.toFloat()
                            onValueChange(target)
                        },
                    ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = null,
                    tint = if (canDecrement)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.size(18.dp),
                )
            }

            Slider(
                value = sliderValue,
                onValueChange = { raw ->
                    sliderValue = raw
                    isDragging = true
                    if (continuousUpdates) onValueChange(snapped(raw))
                },
                onValueChangeFinished = {
                    isDragging = false
                    onValueChange(snapped(sliderValue))
                },
                valueRange = min.toFloat()..max.toFloat(),
                steps = steps,
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                modifier = Modifier
                    .weight(1f)
                    .alpha(contentAlpha),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )

            // Plus button: short-press = step up; long-press = jump to max (or midpoint)
            IconButton(
                onClick = {
                    val next = snapped(sliderValue) + interval
                    sliderValue = next.coerceAtMost(max).toFloat()
                    onValueChange(snapped(sliderValue))
                },
                enabled = canIncrement,
                modifier = Modifier
                    .size(36.dp)
                    .combinedClickable(
                        enabled = canIncrement,
                        onClick = {
                            val next = snapped(sliderValue) + interval
                            sliderValue = next.coerceAtMost(max).toFloat()
                            onValueChange(snapped(sliderValue))
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            // Mirror Java: jump to midpoint if current is below it, else to max
                            val mid = -1 * (-(max + min) / 2)
                            val target = if (max - min > interval * 2 && max + min > snapped(sliderValue) * 2)
                                mid else max
                            sliderValue = target.toFloat()
                            onValueChange(target)
                        },
                    ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = if (canIncrement)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
