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

package com.android.build.gradle;

import com.android.build.gradle.internal.test.BaseTest;

import java.io.File;

/**
 * Base class for build tests.
 *
 * This requires an SDK, found through the ANDROID_HOME environment variable or present in the
 * Android Source tree under out/host/<platform>/sdk/... (result of 'make sdk')
 */
abstract class BuildTest extends BaseTest {

    protected File testDir;
    protected File sdkDir;

    @Override
    protected void setUp() throws Exception {
        testDir = getTestDir();
        sdkDir = getSdkDir();
    }

    protected File buildProject(String name, String gradleVersion) {
        return runTasksOnProject(name, gradleVersion, "clean", "assembleDebug");
    }

    protected File runTasksOnProject(String name, String gradleVersion, String... tasks) {
        File project = new File(testDir, name);

        File buildGradle = new File(project, "build.gradle");
        if (!buildGradle.isFile()) {
            return null;
        }

        // build the project
        runGradleTasks(sdkDir, gradleVersion, project, tasks);

        return project;
    }
}
