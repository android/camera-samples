/*
 * Copyright 2019 Google LLC
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

package com.example.android.camera2.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/** Type helper used for the callback triggered once our view has been bound */
typealias BindCallback<T> = (view: View, data: T, position: Int) -> Unit

/** List adapter for generic types, intended used for small-medium lists of data */
class GenericListAdapter<T>(
        private val dataset: List<T>,
        private val itemLayoutId: Int,
        private val header: View? = null,
        private val onBind: BindCallback<T>
) : RecyclerView.Adapter<GenericListAdapter.GenericListViewHolder>() {

    class GenericListViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GenericListViewHolder {
        val view = if (header != null && viewType == TYPE_HEADER) {
            header
        } else {
            LayoutInflater.from(parent.context).inflate(itemLayoutId, parent, false)
        }
        return GenericListViewHolder(view)
    }

    override fun getItemViewType(position: Int) =
            if (header != null && position == 0) TYPE_HEADER else TYPE_ITEM

    override fun onBindViewHolder(holder: GenericListViewHolder, position: Int) {
        val truePosition = if (header == null) position else position - 1
        if (truePosition < 0 || truePosition > dataset.size) return
        onBind(holder.view, dataset[truePosition], truePosition)
    }

    override fun getItemCount() = if (header == null) dataset.size else dataset.size + 1

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }
}