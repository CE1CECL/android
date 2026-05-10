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

package com.android.build.gradle.internal.model;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.Variant;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.SigningConfig;
import com.google.common.collect.Maps;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the AndroidProject model object.
 */
class DefaultAndroidProject implements AndroidProject, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final String modelVersion;
    @NonNull
    private final String name;
    @NonNull
    private final String compileTarget;
    @NonNull
    private final List<String> bootClasspath;
    @NonNull
    private final Map<String, SigningConfig> signingConfigs;
    private final boolean isLibrary;

    private final Map<String, BuildTypeContainer> buildTypes = Maps.newHashMap();
    private final Map<String, ProductFlavorContainer> productFlavors = Maps.newHashMap();
    private final Map<String, Variant> variants = Maps.newHashMap();

    private ProductFlavorContainer defaultConfig;

    DefaultAndroidProject(@NonNull String modelVersion,
                          @NonNull String name, @NonNull String compileTarget,
                          @NonNull List<String> bootClasspath,
                          @NonNull Map<String, SigningConfig> signingConfigs,
                          boolean isLibrary) {
        this.modelVersion = modelVersion;
        this.name = name;
        this.compileTarget = compileTarget;
        this.bootClasspath = bootClasspath;
        this.signingConfigs = signingConfigs;
        this.isLibrary = isLibrary;
    }

    @NonNull
    DefaultAndroidProject setDefaultConfig(@NonNull ProductFlavorContainer defaultConfigContainer) {
        defaultConfig = defaultConfigContainer;
        return this;
    }

    @NonNull
    DefaultAndroidProject addBuildType(@NonNull BuildTypeContainer buildTypeContainer) {
        buildTypes.put(buildTypeContainer.getBuildType().getName(), buildTypeContainer);
        return this;
    }

    @NonNull
    DefaultAndroidProject addProductFlavors(
            @NonNull ProductFlavorContainer productFlavorContainer) {
        productFlavors.put(productFlavorContainer.getProductFlavor().getName(),
                productFlavorContainer);
        return this;
    }

    @NonNull
    DefaultAndroidProject addVariant(@NonNull VariantImpl variant) {
        variants.put(variant.getName(), variant);
        return this;
    }

    @NonNull
    @Override
    public String getModelVersion() {
        return modelVersion;
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public ProductFlavorContainer getDefaultConfig() {
        return defaultConfig;
    }

    @NonNull
    @Override
    public Map<String, BuildTypeContainer> getBuildTypes() {
        return buildTypes;
    }

    @NonNull
    @Override
    public Map<String, ProductFlavorContainer> getProductFlavors() {
        return productFlavors;
    }

    @NonNull
    @Override
    public Map<String, Variant> getVariants() {
        return variants;
    }

    @Override
    public boolean isLibrary() {
        return isLibrary;
    }

    @NonNull
    @Override
    public String getCompileTarget() {
        return compileTarget;
    }

    @NonNull
    @Override
    public List<String> getBootClasspath() {
        return bootClasspath;
    }

    @NonNull
    @Override
    public Map<String,SigningConfig> getSigningConfigs() {
        return signingConfigs;
    }

    @NonNull
    @Override
    public AaptOptions getAaptOptions() {
        return null;
    }
}
