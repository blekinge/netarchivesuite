/*
 * #%L
 * Netarchivesuite - archive - test
 * %%
 * Copyright (C) 2005 - 2014 The Royal Danish Library, the Danish State and University Library,
 *             the National Library of France and the Austrian National Library.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */

package dk.netarkivet.archive.bitarchive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import dk.netarkivet.archive.ArchiveSettings;
import dk.netarkivet.archive.bitarchive.distribute.BitarchiveServer;
import dk.netarkivet.common.CommonSettings;
import dk.netarkivet.common.distribute.Channels;
import dk.netarkivet.common.distribute.JMSConnectionMockupMQ;
import dk.netarkivet.common.distribute.TestRemoteFile;
import dk.netarkivet.common.exceptions.IOFailure;
import dk.netarkivet.common.utils.FileUtils;
import dk.netarkivet.common.utils.FilebasedFreeSpaceProvider;
import dk.netarkivet.common.utils.MockFreeSpaceProvider;
import dk.netarkivet.common.utils.Settings;
import dk.netarkivet.common.utils.SlowTest;
import dk.netarkivet.testutils.preconfigured.ReloadSettings;

/** A number of integrity tests for the bitarchive package. */
public class IntegrityTests {
    /**
     * The archive directory to work on.
     */
    private static final File ARCHIVE_DIR = new File("tests/dk/netarkivet/bitarchive/data/upload/working/");
    /**
     * The archive that this test queries.
     */
    private static Bitarchive archive;

    /** The external interface */
    private static BitarchiveServer server;

    /**
     * The directory from where we upload the ARC files.
     */
    private static final File ORIGINALS_DIR = new File("tests/dk/netarkivet/bitarchive/data/upload/originals/");
    /**
     * The files that are uploaded during the tests and that must be removed afterwards.
     */
    private static final List<String> UPLOADED_FILES = Arrays.asList(new String[] {"Upload1.ARC", "Upload2.ARC",
            "Upload3.ARC"});
    ReloadSettings rs = new ReloadSettings();

    /**
     * At start of test, set up an archive we can run against.
     */
    @Before
    public void setUp() {
        rs.setUp();
        FileUtils.removeRecursively(ARCHIVE_DIR);
        Settings.set(CommonSettings.REMOTE_FILE_CLASS, TestRemoteFile.class.getName());
        Settings.set(ArchiveSettings.BITARCHIVE_SERVER_FILEDIR, ARCHIVE_DIR.getAbsolutePath());
        archive = Bitarchive.getInstance();
        JMSConnectionMockupMQ.useJMSConnectionMockupMQ();
        server = BitarchiveServer.getInstance();
    }

    /**
     * At end of test, remove any files we managed to upload.
     */
    @After
    public void tearDown() {
        archive.close();
        FileUtils.removeRecursively(ARCHIVE_DIR);
        if (server != null) {
            server.close();
        }
        rs.tearDown();
        // FileUtils.removeRecursively(new File(ARCHIVE_DIR));
    }

    private void setupBitarchiveWithDirs(final String[] dirpaths) {
        Settings.set(ArchiveSettings.BITARCHIVE_SERVER_FILEDIR, dirpaths);
        // Don't like the archive made in setup, try again:)
        archive.close();
        archive = Bitarchive.getInstance();
    }

    /**
     * Verify that the correct value of free space will be returned, when calling the DefaultFreeSpaceProvider.
     */
    @Category(SlowTest.class)
    @Test
    public void testDefaultFreeSpaceProvider() {
        final File dir1 = new File(ARCHIVE_DIR, "dir1");
        setupBitarchiveWithDirs(new String[] {dir1.getAbsolutePath(),});

        Settings.set(CommonSettings.FREESPACE_PROVIDER_CLASS, "dk.netarkivet.common.utils.DefaultFreeSpaceProvider");
        assertEquals(FileUtils.getBytesFree(new File("/not/existing/dir")), 0);

        Settings.set(CommonSettings.FREESPACE_PROVIDER_CLASS, "dk.netarkivet.common.utils.DefaultFreeSpaceProvider");
        long expectedBytes = dir1.getUsableSpace();

        assertEquals(FileUtils.getBytesFree(dir1), expectedBytes);
    }

    /**
     * Verify that the correct value of free space will be returned, when calling the MockFreeSpaceProvider.
     */
    @Test
    public void testMockFreeSpaceProvider() {
        final File dir1 = new File(ARCHIVE_DIR, "dir1");
        setupBitarchiveWithDirs(new String[] {dir1.getAbsolutePath(),});

        Settings.set(CommonSettings.FREESPACE_PROVIDER_CLASS, "dk.netarkivet.common.utils.MockFreeSpaceProvider");
        assertEquals(FileUtils.getBytesFree(ARCHIVE_DIR), MockFreeSpaceProvider.ONETB);
    }

    /**
     * Verify that the correct value of free space will be returned, when calling the FileSpaceProvider.
     */
    @Test
    public void testFilebasedFreeSpaceProvider1() {
        final File dir1 = new File(ARCHIVE_DIR, "dir1");
        setupBitarchiveWithDirs(new String[] {dir1.getAbsolutePath(),});

        File freeSpaceFile = new File(System.getProperty("java.io.tmpdir"), dir1.getName());

        Writer fw = null;
        String DUMMY_VALUE = "1000";

        try {
            fw = new FileWriter(freeSpaceFile);
            fw.write(DUMMY_VALUE);
        } catch (Exception ex) {
            fail("Exception not expected! + " + ex);
            ;
        } finally {
            if (fw != null) {
                try {
                    fw.close();
                } catch (Exception ex) {
                }
            }
        }

        Settings.set(CommonSettings.FREESPACE_PROVIDER_CLASS, "dk.netarkivet.common.utils.FilebasedFreeSpaceProvider");
        Settings.set(FilebasedFreeSpaceProvider.FREESPACEPROVIDER_DIR_SETTING, System.getProperty("java.io.tmpdir"));

        assertEquals(FileUtils.getBytesFree(dir1), Integer.parseInt(DUMMY_VALUE));

        freeSpaceFile.delete();
    }

    /**
     * Verify that the correct value of free space will be returned, when calling the FileSpaceProvider.
     */
    @Test
    public void testFilebasedFreeSpaceProvider2() {
        final File dir1 = new File(ARCHIVE_DIR, "dir1");
        setupBitarchiveWithDirs(new String[] {dir1.getAbsolutePath(),});

        Settings.set(CommonSettings.FREESPACE_PROVIDER_CLASS, "dk.netarkivet.common.utils.FilebasedFreeSpaceProvider");
        Settings.set(FilebasedFreeSpaceProvider.FREESPACEPROVIDER_DIR_SETTING, "/not/existing/dir");
        assertEquals(FileUtils.getBytesFree(dir1), 0);
    }

    /**
     * Verify that we spill into the next directory This test requires special setup to run.
     */
    @Test
    @Ignore("FIXME")
    // FIXME: test temporarily disabled
    public void testUploadChangesDirectory() {
        final File dir1 = new File(ARCHIVE_DIR, "dir1");
        final File dir2 = new File(ARCHIVE_DIR, "dir2");
        setupBitarchiveWithDirs(new String[] {dir1.getAbsolutePath(), dir2.getAbsolutePath()});
        archive.upload(
                new TestRemoteFile(new File(ORIGINALS_DIR, (String) UPLOADED_FILES.get(0)), false, false, false),
                (String) UPLOADED_FILES.get(0));
        assertTrue("Should place first file in first directory", new File(new File(dir1, "filedir"),
                (String) UPLOADED_FILES.get(0)).exists());
        archive.upload(
                new TestRemoteFile(new File(ORIGINALS_DIR, (String) UPLOADED_FILES.get(2)), false, false, false),
                (String) UPLOADED_FILES.get(2));
        assertTrue("Should place second file in second directory", new File(new File(dir2, "filedir"),
                (String) UPLOADED_FILES.get(2)).exists());
    }

    /**
     * Verify that we get appropriate errors when we don't have enough space. This test requires a special setup before
     * actual out of disk space errors will occur.
     */
    @Test
    @Ignore("FIXME")
    // FIXME: test temporarily disabled
    public void testUploadNoSpace() {
        long freeSpace = FileUtils.getBytesFree(ARCHIVE_DIR);
        final File localFile2 = new File(ORIGINALS_DIR, (String) UPLOADED_FILES.get(2));
        Settings.set(ArchiveSettings.BITARCHIVE_MIN_SPACE_LEFT, "" + (freeSpace - localFile2.length() - 1));
        final File dir1 = new File(ARCHIVE_DIR, "dir1");
        setupBitarchiveWithDirs(new String[] {dir1.getAbsolutePath(),});
        JMSConnectionMockupMQ con = (JMSConnectionMockupMQ) JMSConnectionMockupMQ.getInstance();
        assertEquals("We should listen to ANY_BA at the start", 1, con.getListeners(Channels.getAnyBa()).size());
        // Try big file
        try {
            final File localFile1 = new File(ORIGINALS_DIR, (String) UPLOADED_FILES.get(1));
            archive.upload(new TestRemoteFile(localFile1, false, false, false), localFile1.getName());
            fail("Should have thrown IOFailure when uploading " + localFile1.length() + " bytes");
        } catch (IOFailure e) {
            // Expected
        }
        // Upload small file
        archive.upload(new TestRemoteFile(localFile2, false, false, false), localFile2.getName());
        assertTrue("Should have room for file of " + localFile2 + " bytes in first directory", new File(new File(dir1,
                "filedir"), (String) UPLOADED_FILES.get(2)).exists());
        // Try another small file, big enough to die.
        try {
            final File localFile0 = new File(ORIGINALS_DIR, (String) UPLOADED_FILES.get(0));
            archive.upload(new TestRemoteFile(localFile0, false, false, false), localFile0.getName());
            fail("Should have thrown IOFailure when second file, " + localFile0.length());
        } catch (IOFailure e) {
            // Expected
        }
    }
}
