/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.hdrviewfinder;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;

public class MessageDialogFragment extends AppCompatDialogFragment {

    private static final String ARG_MESSAGE_INT = "message_int";
    private static final String ARG_MESSAGE_STRING = "message_string";

    public static MessageDialogFragment newInstance(int message) {
        MessageDialogFragment fragment = new MessageDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_MESSAGE_INT, message);
        fragment.setArguments(args);
        return fragment;
    }

    public static MessageDialogFragment newInstance(String message) {
        MessageDialogFragment fragment = new MessageDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MESSAGE_STRING, message);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setPositiveButton(android.R.string.ok, null);
        Bundle args = getArguments();
        if (args.containsKey(ARG_MESSAGE_INT)) {
            builder.setMessage(args.getInt(ARG_MESSAGE_INT));
        } else if (args.containsKey(ARG_MESSAGE_STRING)) {
            builder.setMessage(args.getString(ARG_MESSAGE_STRING));
        }
        return builder.create();
    }

}
