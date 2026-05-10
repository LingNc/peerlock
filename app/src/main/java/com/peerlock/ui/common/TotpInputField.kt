package com.peerlock.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TotpInputField(
    onCodeComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val digits = remember { mutableStateListOf("", "", "", "", "", "") }
    val focusRequesters = remember { List(6) { FocusRequester() } }

    LaunchedEffect(Unit) {
        focusRequesters[0].requestFocus()
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        repeat(6) { index ->
            OutlinedTextField(
                value = digits[index],
                onValueChange = { value ->
                    if (value.length <= 1 && value.all { it.isDigit() }) {
                        digits[index] = value
                        if (value.isNotEmpty() && index < 5) {
                            focusRequesters[index + 1].requestFocus()
                        }
                        if (digits.all { it.isNotEmpty() }) {
                            onCodeComplete(digits.joinToString(""))
                        }
                    } else if (value.length > 1) {
                        val pasted = value.filter { it.isDigit() }.take(6)
                        pasted.forEachIndexed { i, c ->
                            if (index + i < 6) digits[index + i] = c.toString()
                        }
                        val nextEmpty = digits.indexOfFirst { it.isEmpty() }
                        if (nextEmpty >= 0) focusRequesters[nextEmpty].requestFocus()
                        else if (digits.all { it.isNotEmpty() }) {
                            onCodeComplete(digits.joinToString(""))
                        }
                    }
                },
                modifier = Modifier
                    .width(48.dp)
                    .focusRequester(focusRequesters[index]),
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    fontSize = 24.sp,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        }
    }
}
