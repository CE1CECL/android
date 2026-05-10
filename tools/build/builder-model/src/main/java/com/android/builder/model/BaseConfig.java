/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.builder.model;

import com.android.annotations.NonNull;

import java.io.File;
import java.util.List;

/**
 * Base config object for Build Type and Product flavor.
 */
public interface BaseConfig {

    /**
     * List of Build Config lines.
     * @return a non-null list of lines (possibly empty)
     */
    @NonNull
    List<String> getBuildConfig();

    /**
     * Returns the list of proguard rule files.
     *
     * @return a non-null list of files.
     */
    @NonNull
    List<File> getProguardFiles();
}
