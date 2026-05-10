/*
 * Copyright (C) 2013 The Android Open Source Project
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

import java.util.List;
import java.util.Map;

/**
 * Entry point for the model of the Android Projects. This models a single module, whether
 * the module is an app project or a library project.
 */
public interface AndroidProject {

    /**
     * Returns the model version. This is a string in the format X.Y.Z
     *
     * @return a string containing the model version.
     */
    @NonNull
    String getModelVersion();

    /**
     * Returns the name of the module.
     *
     * @return the name of the module.
     */
    @NonNull
    String getName();

    /**
     * Returns whether this is a library.
     * @return true for a library module.
     */
    boolean isLibrary();

    /**
     * Returns the {@link ProductFlavorContainer} for the 'main' default config.
     *
     * @return the product flavor.
     */
    @NonNull
    ProductFlavorContainer getDefaultConfig();

    /**
     * Returns a map of all the {@link BuildType} in their container. The key is the build type
     * name as returned by {@link BuildType#getName()}
     *
     * @return a map of build type containers.
     */
    @NonNull
    Map<String, BuildTypeContainer> getBuildTypes();

    /**
     * Returns a map of all the {@link ProductFlavor} in their container. The key is the product
     * flavor name as returned by {@link ProductFlavor#getName()}
     *
     * @return a map of product flavor containers.
     */
    @NonNull
    Map<String, ProductFlavorContainer> getProductFlavors();

    /**
     * Returns a map of all the variants. The key is the variant name as returned by
     * {@link Variant#getName()}.
     *
     * This does not include test variant. Instead the variant and its component each contribute
     * their test part.
     *
     * @return a map of the variants.
     */
    @NonNull
    Map<String, Variant> getVariants();

    /**
     * Returns the compilation target as a string. This is the full extended target hash string.
     * (see com.android.sdklib.IAndroidTarget#hashString())
     *
     * @return the target hash string
     */
    @NonNull
    String getCompileTarget();

    /**
     * Returns the boot classpath matching the compile target. This is typically android.jar plus
     * other optional libraries.
     *
     * @return a list of jar files.
     */
    @NonNull
    List<String> getBootClasspath();

    /**
     * Returns a map of {@link SigningConfig}. The key is the signing config name as returned by
     * {@link SigningConfig#getName()}
     *
     * @return a map of signing config
     */
    @NonNull
    Map<String, SigningConfig> getSigningConfigs();

    /**
     * Returns the aapt options.
     *
     * @return the aapt options.
     */
    @NonNull
    AaptOptions getAaptOptions();
}
