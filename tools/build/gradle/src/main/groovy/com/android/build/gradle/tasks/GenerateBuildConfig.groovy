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
package com.android.build.gradle.tasks

import com.android.build.gradle.internal.tasks.IncrementalTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory

public class GenerateBuildConfig extends IncrementalTask {

    // ----- PUBLIC TASK API -----

    @OutputDirectory
    File sourceOutputDir

    // ----- PRIVATE TASK API -----

    @Input
    String packageName

    @Input
    boolean debuggable

    @Input
    List<String> javaLines;

    @Override
    protected void doFullTaskAction() {
        // must clear the folder in case the packagename changed, otherwise,
        // there'll be two classes.
        File destinationDir = getSourceOutputDir()
        emptyFolder(destinationDir)

        getBuilder().generateBuildConfig(
                getPackageName(),
                isDebuggable(),
                getJavaLines(),
                getSourceOutputDir().absolutePath);
    }
}
