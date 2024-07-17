package com.pengmj.learnkotlincoroutine.example01

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * @author MinKin
 * @date 2024/7/17
 * @desc
 */

@Composable
fun Example01ProgramEntry(
    modifier: Modifier = Modifier,
) {
    Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
    ) {
        Button(
                onClick = {
                    GlobalScope.launch {
                        "abc".substring(10)
                    }
                },
        ) {
            Text(text = "click")
        }
    }
}