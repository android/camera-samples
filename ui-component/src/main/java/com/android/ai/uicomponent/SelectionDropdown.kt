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
package com.android.ai.uicomponent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.ai.theme.AISampleCatalogTheme

interface SelectableItem<T> {
    val itemLabel: String
    val itemData: T
}

@Composable
fun <T> SelectionDropdown(
    selectedItem: SelectableItem<T>?,
    isDropdownExpanded: Boolean,
    itemList: List<SelectableItem<T>>,
    selectPlaceHolder: String = stringResource(R.string.select_placeholder),
    onItemSelected: (SelectableItem<T>) -> Unit,
    onDropdownExpanded: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)

    Box {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(shape)
                .height(40.dp)
                .width(250.dp)
                .clickable { onDropdownExpanded(!isDropdownExpanded) }
                .background(color = MaterialTheme.colorScheme.onSurface)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline,
                    shape = shape,
                ),
        ) {
            Text(
                text = selectedItem?.itemLabel ?: selectPlaceHolder,
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.inverseOnSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,

            )

            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                tint = MaterialTheme.colorScheme.onSurface,
                contentDescription = stringResource(R.string.dropdown_content_description),
                modifier = Modifier
                    .clickable { onDropdownExpanded(!isDropdownExpanded) }
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = CircleShape,
                    )
                    .width(40.dp)
                    .height(40.dp),
            )
        }

        DropdownMenu(
            expanded = isDropdownExpanded,
            onDismissRequest = { onDropdownExpanded(false) },
            modifier = Modifier
                .wrapContentWidth(),
        ) {
            itemList.forEach { it ->
                DropdownMenuItem(
                    text = { Text(it.itemLabel) },
                    onClick = {
                        onItemSelected(it)
                        onDropdownExpanded(false)
                    },
//                    colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                )
            }
        }
    }
}

class PreviewSelectableItem(
    override val itemLabel: String,
    override val itemData: String,
) : SelectableItem<String>
val previewListOfItems = listOf<SelectableItem<String>>(
    PreviewSelectableItem("Item 1", "item_1"),
    PreviewSelectableItem("Item 2", "item_2"),
    PreviewSelectableItem("Item 3", "item_3"),
    PreviewSelectableItem("Item 4", "item_4"),
)

@Preview
@Composable
private fun SelectionDropdownPreviewCollapsed() {

    var isExpanded by remember { mutableStateOf<Boolean>(false) }
    var selectedItem by remember { mutableStateOf(previewListOfItems[0]) }

    AISampleCatalogTheme {
        SelectionDropdown(
            selectedItem = selectedItem,
            isDropdownExpanded = isExpanded,
            itemList = previewListOfItems,
            selectPlaceHolder = "",
            onItemSelected = { selectedItem = it },
            onDropdownExpanded = { isExpanded = it },
        )
    }
}

@Preview
@Composable
private fun SelectionDropdownPreviewExpanded() {

    var isExpanded by remember { mutableStateOf<Boolean>(true) }
    var selectedItem by remember { mutableStateOf(previewListOfItems[0]) }

    AISampleCatalogTheme {
        SelectionDropdown(
            selectedItem = selectedItem,
            isDropdownExpanded = isExpanded,
            itemList = previewListOfItems,
            selectPlaceHolder = "",
            onItemSelected = { selectedItem = it },
            onDropdownExpanded = { isExpanded = it },
        )
    }
}
