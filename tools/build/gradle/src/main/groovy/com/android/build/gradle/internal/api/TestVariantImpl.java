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
import com.android.build.gradle.api.BaseVariant;
import com.android.build.gradle.api.TestVariant;
import com.android.build.gradle.internal.variant.TestVariantData;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.Dex;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.MergeAssets;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.PackageApplication;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.build.gradle.tasks.ProcessManifest;
import com.android.build.gradle.tasks.RenderscriptCompile;
import com.android.build.gradle.tasks.ZipAlign;
import com.android.builder.DefaultBuildType;
import com.android.builder.DefaultProductFlavor;
import com.android.builder.model.SigningConfig;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.util.List;

/**
 * implementation of the {@link TestVariant} interface around an {@link TestVariantData} object.
 */
public class TestVariantImpl implements TestVariant {

    @NonNull
    private final TestVariantData variantData;
    @NonNull
    private BaseVariant testedVariant;

    public TestVariantImpl(@NonNull TestVariantData variantData) {
        this.variantData = variantData;
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

    @Override
    @NonNull
    public List<DefaultProductFlavor> getProductFlavors() {
        return variantData.getVariantConfiguration().getFlavorConfigs();
    }

    @Override
    @NonNull
    public DefaultProductFlavor getMergedFlavor() {
        return variantData.getVariantConfiguration().getMergedFlavor();
    }

    @Override
    @NonNull
    public File getOutputFile() {
        return variantData.getOutputFile();
    }

    @Override
    public void setOutputFile(@NonNull File outputFile) {
        if (variantData.zipAlignTask != null) {
            variantData.zipAlignTask.setOutputFile(outputFile);
        } else {
            variantData.packageApplicationTask.setOutputFile(outputFile);
        }
    }

    @Override
    @NonNull
    public BaseVariant getTestedVariant() {
        return testedVariant;
    }

    public void setTestedVariant(@NonNull BaseVariant testedVariant) {
        this.testedVariant = testedVariant;
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
    public Dex getDex() {
        return variantData.dexTask;
    }

    @Override
    public PackageApplication getPackageApplication() {
        return variantData.packageApplicationTask;
    }

    @Override
    public ZipAlign getZipAlign() {
        return variantData.zipAlignTask;
    }

    @Override
    public Task getAssemble() {
        return variantData.assembleTask;
    }

    @Override
    public DefaultTask getInstall() {
        return variantData.installTask;
    }

    @Override
    public DefaultTask getUninstall() {
        return variantData.uninstallTask;
    }

    @Override
    public DefaultTask getConnectedInstrumentTest() {
        return variantData.connectedTestTask;
    }

    @NonNull
    @Override
    public List<? extends DefaultTask> getProviderInstrumentTests() {
        return variantData.providerTestTaskList;
    }

    @Override
    public SigningConfig getSigningConfig() {
        return variantData.getVariantConfiguration().getSigningConfig();
    }

    @Override
    public boolean isSigningReady() {
        return variantData.isSigned();
    }
}
