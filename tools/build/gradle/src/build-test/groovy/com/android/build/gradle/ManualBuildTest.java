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

package com.android.build.gradle;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Some manual tests for building projects.
 *
 * This requires an SDK, found through the ANDROID_HOME environment variable or present in the
 * Android Source tree under out/host/<platform>/sdk/... (result of 'make sdk')
 */
public class ManualBuildTest extends BuildTest {


    public void testOverlay1Content() throws Exception {
        File project = buildProject("overlay1", BasePlugin.GRADLE_MIN_VERSION);
        File drawableOutput = new File(project, "build/res/all/debug/drawable");

        checkImageColor(drawableOutput, "no_overlay.png", (int) 0xFF00FF00);
        checkImageColor(drawableOutput, "type_overlay.png", (int) 0xFF00FF00);
    }

    public void testOverlay2Content() throws Exception {
        File project = buildProject("overlay2", BasePlugin.GRADLE_MIN_VERSION);
        File drawableOutput = new File(project, "build/res/all/one/debug/drawable");

        checkImageColor(drawableOutput, "no_overlay.png", (int) 0xFF00FF00);
        checkImageColor(drawableOutput, "type_overlay.png", (int) 0xFF00FF00);
        checkImageColor(drawableOutput, "flavor_overlay.png", (int) 0xFF00FF00);
        checkImageColor(drawableOutput, "type_flavor_overlay.png", (int) 0xFF00FF00);
    }

    public void testRepo() {
        File repo = new File(testDir, "repo");

        try {
            runGradleTasks(sdkDir, BasePlugin.GRADLE_MIN_VERSION,
                    new File(repo, "util"), "clean", "uploadArchives");
            runGradleTasks(sdkDir, BasePlugin.GRADLE_MIN_VERSION,
                    new File(repo, "baseLibrary"), "clean", "uploadArchives");
            runGradleTasks(sdkDir, BasePlugin.GRADLE_MIN_VERSION,
                    new File(repo, "library"), "clean", "uploadArchives");
            runGradleTasks(sdkDir, BasePlugin.GRADLE_MIN_VERSION,
                    new File(repo, "app"), "clean", "assemble");
        } finally {
            // clean up the test repository.
            File testRepo = new File(repo, "testrepo");
            deleteFolder(testRepo);
        }
    }

    public void test3rdPartyTests() throws Exception {
        // custom because we want to run deviceCheck even without devices, since we use
        // a fake DeviceProvider that doesn't use a device, but only record the calls made
        // to the DeviceProvider and the DeviceConnector.
        runGradleTasks(sdkDir, BasePlugin.GRADLE_MIN_VERSION,
                new File(testDir, "3rdPartyTests"), "clean", "deviceCheck");
    }

    private static void checkImageColor(File folder, String fileName, int expectedColor)
            throws IOException {
        File f = new File(folder, fileName);
        assertTrue("File '" + f.getAbsolutePath() + "' does not exist.", f.isFile());

        BufferedImage image = ImageIO.read(f);
        int rgb = image.getRGB(0, 0);
        assertEquals(String.format("Expected: 0x%08X, actual: 0x%08X for file %s",
                expectedColor, rgb, f),
                expectedColor, rgb);
    }
}
