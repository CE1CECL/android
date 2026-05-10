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
package com.android.build.gradle.internal.variant;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.StringHelper;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.tasks.PrepareDependenciesTask;
import com.android.build.gradle.tasks.AidlCompile;
import com.android.build.gradle.tasks.GenerateBuildConfig;
import com.android.build.gradle.tasks.MergeAssets;
import com.android.build.gradle.tasks.MergeResources;
import com.android.build.gradle.tasks.ProcessAndroidResources;
import com.android.build.gradle.tasks.ProcessManifest;
import com.android.build.gradle.tasks.RenderscriptCompile;
import com.android.builder.BuilderConstants;
import com.android.builder.DefaultProductFlavor;
import com.android.builder.VariantConfiguration;
import groovy.lang.Closure;
import org.gradle.api.Task;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;

/**
 * Base data about a variant.
 */
public abstract class BaseVariantData {

    private String name;
    private final VariantConfiguration variantConfiguration;
    private VariantDependencies variantDependency;

    public PrepareDependenciesTask prepareDependenciesTask;

    public ProcessManifest processManifestTask;
    public RenderscriptCompile renderscriptCompileTask;
    public AidlCompile aidlCompileTask;
    public MergeResources mergeResourcesTask;
    public MergeAssets mergeAssetsTask;
    public ProcessAndroidResources processResourcesTask;
    public GenerateBuildConfig generateBuildConfigTask;

    public JavaCompile javaCompileTask;
    public Copy processJavaResources;

    private Object outputFile;

    public Task assembleTask;

    public BaseVariantData(@NonNull VariantConfiguration variantConfiguration) {
        this.variantConfiguration = variantConfiguration;
        this.name = computeName();
    }

    @NonNull
    protected abstract String computeName();

    @NonNull
    public VariantConfiguration getVariantConfiguration() {
        return variantConfiguration;
    }

    public void setVariantDependency(@NonNull VariantDependencies variantDependency) {
        this.variantDependency = variantDependency;
    }

    @NonNull
    public VariantDependencies getVariantDependency() {
        return variantDependency;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public abstract String getDescription();

    @NonNull
    public abstract String getDirName();

    @NonNull
    public String getFlavorDirName() {
        if (variantConfiguration.hasFlavors()) {
            return getFlavoredName(false);
        } else {
            return "";
        }
    }

    @NonNull
    public String getFlavorName() {
        if (variantConfiguration.hasFlavors()) {
            return getFlavoredName(true);
        } else {
            return StringHelper.capitalize(BuilderConstants.MAIN);
        }
    }

    @NonNull
    public abstract String getBaseName();

    @Nullable
    public String getPackageName() {
        return variantConfiguration.getPackageName();
    }

    @NonNull
    protected String getFlavoredName(boolean capitalized) {
        StringBuilder builder = new StringBuilder();
        for (DefaultProductFlavor flavor : variantConfiguration.getFlavorConfigs()) {
            String name = flavor.getName();
            builder.append(capitalized ? StringHelper.capitalize(name) : name);
        }

        return builder.toString();
    }

    @NonNull
    protected String getCapitalizedBuildTypeName() {
        return StringHelper.capitalize(variantConfiguration.getBuildType().getName());
    }

    public boolean getRunProguard() {
        return false;
    }

    public void setOutputFile(Object file) {
        outputFile = file;
    }

    public File getOutputFile() {
        if (outputFile instanceof File) {
            return (File) outputFile;
        } else if (outputFile instanceof Closure) {
            Closure c = (Closure) outputFile;
            return (File) c.call();
        }

        assert false;
        return null;
    }
}
