/*
 * Copyright 2022 The Android Open Source Project
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

package com.example.android.cameraxextensions.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.android.cameraxextensions.R

/**
 * Adapter used to display CameraExtensionItems in a RecyclerView.
 */
class CameraExtensionsSelectorAdapter(private val onItemClick: (view: View) -> Unit) :
    ListAdapter<CameraExtensionItem, CameraExtensionItemViewHolder>(ItemCallback()) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): CameraExtensionItemViewHolder =
        CameraExtensionItemViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.view_extension_type, parent, false) as TextView,
            onItemClick
        )

    override fun onBindViewHolder(holder: CameraExtensionItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    internal class ItemCallback : DiffUtil.ItemCallback<CameraExtensionItem>() {
        override fun areItemsTheSame(
            oldItem: CameraExtensionItem,
            newItem: CameraExtensionItem
        ): Boolean = oldItem.extensionMode == newItem.extensionMode

        override fun areContentsTheSame(
            oldItem: CameraExtensionItem,
            newItem: CameraExtensionItem
        ): Boolean = oldItem.selected == newItem.selected
    }
}

class CameraExtensionItemViewHolder internal constructor(
    private val extensionView: TextView,
    private val onItemClick: (view: View) -> Unit
) :
    RecyclerView.ViewHolder(extensionView) {

    init {
        extensionView.setOnClickListener { onItemClick(it) }
    }

    internal fun bind(extensionModel: CameraExtensionItem) {
        extensionView.text = extensionModel.name
        if (extensionModel.selected) {
            extensionView.setBackgroundResource(R.drawable.pill_selected_background)
            extensionView.setTextColor(Color.BLACK)
        } else {
            extensionView.setBackgroundResource(R.drawable.pill_unselected_background)
            extensionView.setTextColor(Color.WHITE)
        }
    }
}