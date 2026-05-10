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

package com.android.builder.internal.incremental;

import com.android.ide.common.res2.FileStatus;
import com.android.testutils.TestUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;

public class FileManagerTest extends TestCase {

    private static FileManager sFileManager = null;
    private static File sFilesFolder = null;

    public void testUntouched() throws Exception {
        FileManager fileManager = getFileManager();
        Map<File, FileStatus> changedFiles = fileManager.getChangedFiles();

        File file = new File(sFilesFolder, "untouched.png");
        FileStatus status = changedFiles.get(file);
        assertNull(status);
    }

    public void testUntouchedDateBefore() throws Exception {
        FileManager fileManager = getFileManager();
        Map<File, FileStatus> changedFiles = fileManager.getChangedFiles();

        File file = new File(sFilesFolder, "untouched_date_before.png");
        FileStatus status = changedFiles.get(file);
        // no change
        assertNull(status);
    }

    public void testUntouchedDateAfter() throws Exception {
        FileManager fileManager = getFileManager();
        Map<File, FileStatus> changedFiles = fileManager.getChangedFiles();

        File file = new File(sFilesFolder, "untouched_date_after.png");
        FileStatus status = changedFiles.get(file);
        // no change
        assertNull(status);
    }

    public void testContentChanged() throws Exception {
        FileManager fileManager = getFileManager();
        Map<File, FileStatus> changedFiles = fileManager.getChangedFiles();

        File file = new File(sFilesFolder, "content_changed.png");
        FileStatus status = changedFiles.get(file);
        assertEquals(FileStatus.CHANGED, status);
    }

    public void testSizeChanged() throws Exception {
        FileManager fileManager = getFileManager();
        Map<File, FileStatus> changedFiles = fileManager.getChangedFiles();

        File file = new File(sFilesFolder, "size_changed.png");
        FileStatus status = changedFiles.get(file);
        assertEquals(FileStatus.CHANGED, status);
    }

    public void testRemoved() throws Exception {
        FileManager fileManager = getFileManager();
        Map<File, FileStatus> changedFiles = fileManager.getChangedFiles();

        File file = new File(sFilesFolder, "removed.png");
        FileStatus status = changedFiles.get(file);
        assertEquals(FileStatus.REMOVED, status);
    }

    public void testNew() throws Exception {
        FileManager fileManager = getFileManager();
        Map<File, FileStatus> changedFiles = fileManager.getChangedFiles();

        File file = new File(sFilesFolder, "new.png");
        FileStatus status = changedFiles.get(file);
        assertEquals(FileStatus.NEW, status);
    }

    private FileManager getFileManager() throws IOException {
        if (sFileManager == null) {
            File root = TestUtils.getCanonicalRoot("changeManager");
            File dataFile = new File(root, "files.data");
            sFilesFolder = new File(root, "files");

            // update the last modified on some of the files.
            Map<String, String> placeHolderMap = Maps.newHashMap();
            String[] files = new String[] { "untouched" };
            for (String filename : files) {
                File file = new File(sFilesFolder, filename + ".png");
                placeHolderMap.put(
                        String.format("\\$lm_%s\\$", filename),
                        String.format("%d", file.lastModified()));
            }

            File trueDataFile = getDataFile(dataFile, sFilesFolder, placeHolderMap);

            sFileManager = new FileManager();
            sFileManager.load(trueDataFile);

            sFileManager.addFile(sFilesFolder);
        }

        return sFileManager;
    }

    /**
     * Returns a data file where the placeholders have been updated with the real folder based
     * on where the tests are run from.
     *
     * @param file the data folder.
     * @param targetFolder the targetFolder
     * @param placeholders additional placeholders and values to replace.
     *
     * @return a new data file that's been updated with the targetFolder
     * @throws IOException
     */
    private static File getDataFile(File file, File targetFolder, Map<String, String> placeholders)
            throws IOException {

        String content = Files.toString(file, Charsets.UTF_8);

        // search and replace $TOP$ with the root and $SEP$ with the platform separator.
        content = content.replaceAll("\\$TOP\\$",  Matcher.quoteReplacement(targetFolder.getAbsolutePath()))
                .replaceAll("\\$SEP\\$", Matcher.quoteReplacement(File.separator));

        // now replace the additional placeholders
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            content = content.replaceAll(entry.getKey(), Matcher.quoteReplacement(entry.getValue()));
        }

        File tmp = File.createTempFile("android", "getDataFile");
        Files.write(content, tmp, Charsets.UTF_8);

        return tmp;
    }
}
