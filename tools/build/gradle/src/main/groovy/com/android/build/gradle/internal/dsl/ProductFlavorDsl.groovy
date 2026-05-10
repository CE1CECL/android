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

package com.android.build.gradle.internal.dsl
import com.android.annotations.NonNull
import com.android.builder.DefaultProductFlavor
import org.gradle.api.internal.file.FileResolver

/**
 * DSL overlay to make methods that accept String... work.
 */
class ProductFlavorDsl extends DefaultProductFlavor {
    private static final long serialVersionUID = 1L

    @NonNull
    private final FileResolver fileResolver

    ProductFlavorDsl(String name, @NonNull FileResolver fileResolver) {
        super(name)
        this.fileResolver = fileResolver
    }

    // -- DSL Methods. TODO remove once the instantiator does what I expect it to do.

    public void buildConfig(String... lines) {
        setBuildConfig(lines)
    }

    public void buildConfig(String line) {
        setBuildConfig(line)
    }

    @NonNull
    public ProductFlavorDsl proguardFile(Object proguardFile) {
        proguardFiles.add(fileResolver.resolve(proguardFile));
        return this;
    }

    @NonNull
    public ProductFlavorDsl proguardFiles(Object... proguardFileArray) {
        proguardFiles.addAll(fileResolver.resolveFiles(proguardFileArray).files);
        return this;
    }

    @NonNull
    public ProductFlavorDsl setProguardFiles(Iterable<?> proguardFileIterable) {
        proguardFiles.clear();
        for (Object proguardFile : proguardFileIterable) {
            proguardFiles.add(fileResolver.resolve(proguardFile));
        }
        return this;
    }
}
