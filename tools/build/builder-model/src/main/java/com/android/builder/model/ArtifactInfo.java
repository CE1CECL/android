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
import com.android.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * The information for a generated artifact.
 */
public interface ArtifactInfo {

    /**
     * Returns the output file for this artifact. Depending on whether the project is an app
     * or a library project, this could be an apk or an aar file.
     *
     * For test artifact for a library project, this would also be an apk.
     *
     * @return the output file.
     */
    @NonNull
    File getOutputFile();

    /**
     * Returns whether the output file is signed. This is always false for the main artifact
     * of a library project.
     *
     * @return true if the app is signed.
     */
    boolean isSigned();

    /**
     * Returns the name of the {@link SigningConfig} used for the signing. If none are setup or
     * if this is the main artifact of a library project, then this is null.
     *
     * @return the name of the setup signing config.
     */
    @Nullable
    String getSigningConfigName();

    /**
     * Returns the package name of this artifact.
     *
     * @return the package name.
     */
    @NonNull
    String getPackageName();

    /**
     * Returns the name of the task used to generate the source code. The actual value might
     * depend on the build system front end.
     *
     * @return the name of the code generating task.
     */
    @NonNull
    String getSourceGenTaskName();

    /**
     * Returns the name of the task used to generate the artifact.
     *
     * @return the name of the task.
     */
    @NonNull
    String getAssembleTaskName();

    /**
     * Returns all the source folders that are generated. This is typically folders for the R,
     * the aidl classes, and the renderscript classes.
     *
     * @return a list of folders.
     */
    @NonNull
    List<File> getGeneratedSourceFolders();

    /**
     * Returns all the resource folders that are generated. This is typically the renderscript
     * output and the merged resources.
     *
     * @return a list of folder.
     */
    @NonNull
    List<File> getGeneratedResourceFolders();

    /**
     * Returns the folder containing the class files. This is the output of the java compilation.
     *
     * @return a folder.
     */
    @NonNull
    File getClassesFolder();

    /**
     * Returns the resolved dependencies for this artifact. This is a composite of all the
     * dependencies for that artifact: default config + build type + flavor(s).s
     *
     * @return The dependencies.
     */
    @NonNull
    Dependencies getDependencies();
}
