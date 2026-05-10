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
import com.android.annotations.Nullable;
import com.android.builder.model.ArtifactInfo;
import com.android.builder.model.Dependencies;

import java.io.File;
import java.io.Serializable;
import java.util.List;

/**
 * Implementation of ArtifactInfo that is serializable
 */
public class ArtifactInfoImpl implements ArtifactInfo, Serializable {

    @NonNull
    private final File outputFile;
    private final boolean isSigned;
    @Nullable
    private final String signingConfigName;
    @NonNull
    private final String assembleTaskName;
    @NonNull
    private final String packageName;
    @NonNull
    private final List<File> generatedSourceFolders;
    @NonNull
    private final List<File> generatedResourceFolders;
    @NonNull
    private final File classesFolder;
    @NonNull
    private final Dependencies dependencies;


    ArtifactInfoImpl(@NonNull  String assembleTaskName,
                     @NonNull  File outputFile,
                               boolean isSigned,
                     @Nullable String signingConfigName,
                     @NonNull  String packageName,
                     @NonNull  List<File> generatedSourceFolders,
                     @NonNull  List<File> generatedResourceFolders,
                     @NonNull  File classesFolder,
                     @NonNull  Dependencies dependencies) {
        this.assembleTaskName = assembleTaskName;
        this.outputFile = outputFile;
        this.isSigned = isSigned;
        this.signingConfigName = signingConfigName;
        this.packageName = packageName;
        this.generatedSourceFolders = generatedSourceFolders;
        this.generatedResourceFolders = generatedResourceFolders;
        this.classesFolder = classesFolder;
        this.dependencies = dependencies;
    }

    @NonNull
    @Override
    public File getOutputFile() {
        return outputFile;
    }

    @Override
    public boolean isSigned() {
        return isSigned;
    }

    @Nullable
    @Override
    public String getSigningConfigName() {
        return signingConfigName;
    }

    @NonNull
    @Override
    public String getPackageName() {
        return packageName;
    }

    @NonNull
    @Override
    public String getSourceGenTaskName() {
        return "TODO";
    }

    @NonNull
    @Override
    public String getAssembleTaskName() {
        return assembleTaskName;
    }

    @NonNull
    @Override
    public List<File> getGeneratedSourceFolders() {
        return generatedSourceFolders;
    }

    @NonNull
    @Override
    public List<File> getGeneratedResourceFolders() {
        return generatedResourceFolders;
    }

    @NonNull
    @Override
    public File getClassesFolder() {
        return classesFolder;
    }

    @NonNull
    @Override
    public Dependencies getDependencies() {
        return dependencies;
    }
}
