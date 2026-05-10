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

package com.android.build.gradle.internal.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.api.LibraryVariant;
import com.android.build.gradle.api.TestVariant;
import com.android.build.gradle.internal.variant.LibraryVariantData;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.MergeAssets;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.build.gradle.tasks.ProcessManifest;
import com.android.build.gradle.tasks.RenderscriptCompile;
import com.android.builder.DefaultBuildType;
import com.android.builder.DefaultProductFlavor;
import org.gradle.api.Task;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;

/**
 * implementation of the {@link LibraryVariant} interface around a
 * {@link LibraryVariantData} object.
 */
public class LibraryVariantImpl implements LibraryVariant {

    @NonNull
    private final LibraryVariantData variantData;
    @Nullable
    private TestVariant testVariant = null;

    public LibraryVariantImpl(@NonNull LibraryVariantData variantData) {
        this.variantData = variantData;
    }

    public void setTestVariant(@Nullable TestVariant testVariant) {
        this.testVariant = testVariant;
    }

    @Override
    @NonNull
    public String getName() {
        return variantData.getName();
    }

    @Override
    @NonNull
    public String getDescription() {
        return variantData.getDescription();
    }

    @Override
    @NonNull
    public String getDirName() {
        return variantData.getDirName();
    }

    @Override
    @NonNull
    public String getBaseName() {
        return variantData.getBaseName();
    }

    @Override
    @NonNull
    public DefaultBuildType getBuildType() {
        return variantData.getVariantConfiguration().getBuildType();
    }

    @NonNull
    @Override
    public DefaultProductFlavor getConfig() {
        return variantData.getVariantConfiguration().getDefaultConfig();
    }

    @Override
    @NonNull
    public File getOutputFile() {
        return variantData.packageLibTask.getArchivePath();
    }

    @Override
    public void setOutputFile(@NonNull File outputFile) {
        variantData.packageLibTask.setDestinationDir(outputFile.getParentFile());
        variantData.packageLibTask.setArchiveName(outputFile.getName());
    }

    @Override
    @Nullable
    public TestVariant getTestVariant() {
        return testVariant;
    }

    @Override
    @NonNull
    public ProcessManifest getProcessManifest() {
        return variantData.processManifestTask;
    }

    @Override
    @NonNull
    public AidlCompile getAidlCompile() {
        return variantData.aidlCompileTask;
    }

    @Override
    @NonNull
    public RenderscriptCompile getRenderscriptCompile() {
        return variantData.renderscriptCompileTask;
    }

    @Override
    public MergeResources getMergeResources() {
        return variantData.mergeResourcesTask;
    }

    @Override
    public MergeAssets getMergeAssets() {
        return variantData.mergeAssetsTask;
    }

    @Override
    @NonNull
    public ProcessAndroidResources getProcessResources() {
        return variantData.processResourcesTask;
    }

    @Override
    public GenerateBuildConfig getGenerateBuildConfig() {
        return variantData.generateBuildConfigTask;
    }

    @Override
    @NonNull
    public JavaCompile getJavaCompile() {
        return variantData.javaCompileTask;
    }

    @Override
    @NonNull
    public Copy getProcessJavaResources() {
        return variantData.processJavaResources;
    }

    @Override
    public Zip getPackageLibrary() {
        return variantData.packageLibTask;
    }

    @Override
    public Task getAssemble() {
        return variantData.assembleTask;
    }
}
