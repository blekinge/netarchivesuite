/*
 * #%L
 * Netarchivesuite - harvester - test
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
package dk.netarkivet.harvester.harvesting.distribute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.jms.JMSException;
import javax.jms.ObjectMessage;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import dk.netarkivet.common.CommonSettings;
import dk.netarkivet.common.Constants;
import dk.netarkivet.common.distribute.ChannelsTesterHelper;
import dk.netarkivet.common.distribute.JMSConnection;
import dk.netarkivet.common.distribute.JMSConnectionFactory;
import dk.netarkivet.common.distribute.JMSConnectionMockupMQ;
import dk.netarkivet.common.distribute.NetarkivetMessage;
import dk.netarkivet.common.utils.FileUtils;
import dk.netarkivet.common.utils.RememberNotifications;
import dk.netarkivet.common.utils.Settings;
import dk.netarkivet.common.utils.batch.BatchLocalFiles;
import dk.netarkivet.common.utils.cdx.CDXRecord;
import dk.netarkivet.common.utils.cdx.ExtractCDXJob;
import dk.netarkivet.harvester.HarvesterSettings;
import dk.netarkivet.harvester.datamodel.HarvestChannel;
import dk.netarkivet.harvester.datamodel.HarvestDefinitionInfo;
import dk.netarkivet.harvester.datamodel.Job;
import dk.netarkivet.harvester.datamodel.JobStatus;
import dk.netarkivet.harvester.datamodel.JobTest;
import dk.netarkivet.harvester.distribute.HarvesterChannels;
import dk.netarkivet.harvester.harvesting.HarvestController;
import dk.netarkivet.harvester.harvesting.HarvestDocumentation;
import dk.netarkivet.harvester.harvesting.HeritrixFiles;
import dk.netarkivet.harvester.harvesting.IngestableFiles;
import dk.netarkivet.harvester.harvesting.JobInfo;
import dk.netarkivet.harvester.harvesting.JobInfoTestImpl;
import dk.netarkivet.harvester.harvesting.metadata.MetadataEntry;
import dk.netarkivet.testutils.ClassAsserts;
import dk.netarkivet.testutils.GenericMessageListener;
import dk.netarkivet.testutils.LogbackRecorder;
import dk.netarkivet.testutils.ReflectUtils;
import dk.netarkivet.testutils.StringAsserts;
import dk.netarkivet.testutils.TestResourceUtils;
import dk.netarkivet.testutils.preconfigured.ReloadSettings;

@SuppressWarnings("unused")
public class HarvestControllerServerTester {
    @Rule public TestName test = new TestName();
    private File WORKING_DIR;
    private File SERVER_DIR;            
    private File ARCHIVE_DIR;

    /** The message to write to log when starting the server. */
    private static final String START_MESSAGE = "Starting HarvestControllerServer.";

    /** The message to write to log when stopping the server. */
    private static final String CLOSE_MESSAGE = "Closing HarvestControllerServer.";

    private static final HarvestChannel focusedHarvestChannel =
            new HarvestChannel("FOCUSED", false, true, "Channel for focused harvests");

    HarvestControllerServer hcs;
    ReloadSettings rs = new ReloadSettings();

    @Before
    public void setUp() throws Exception {
        WORKING_DIR = new File(TestResourceUtils.OUTPUT_DIR, getClass().getSimpleName() + "/" + test.getMethodName());
        FileUtils.removeRecursively(WORKING_DIR);
        FileUtils.createDir(WORKING_DIR);
        FileUtils.createDir(SERVER_DIR = new File(WORKING_DIR, "serverdir"));
        FileUtils.createDir(ARCHIVE_DIR = new File(WORKING_DIR, "archivedir"));
        FileUtils.copyDirectory(new File("src/test/resources/originals"), WORKING_DIR);
        FileUtils.copyDirectory(new File("src/test/resources/crawldir"), WORKING_DIR);

        rs.setUp();
        JMSConnectionMockupMQ.useJMSConnectionMockupMQ();
        JMSConnectionMockupMQ.clearTestQueues();
        ChannelsTesterHelper.resetChannels();
        // Out commented to avoid reference to archive module from harvester module.
        // Settings.set(JMSArcRepositoryClient.ARCREPOSITORY_STORE_RETRIES, "1");
        Settings.set(CommonSettings.NOTIFICATIONS_CLASS, RememberNotifications.class.getName());
        Settings.set(HarvesterSettings.HARVEST_CONTROLLER_SERVERDIR, WORKING_DIR.getAbsolutePath());
        Settings.set(HarvesterSettings.HARVEST_CONTROLLER_OLDJOBSDIR, WORKING_DIR.getAbsolutePath()
                + "/oldjobs");
        Settings.set(HarvesterSettings.HARVEST_CONTROLLER_CHANNEL, "FOCUSED");
        Settings.set(CommonSettings.ARC_REPOSITORY_CLIENT,
                "dk.netarkivet.common.arcrepository.TrivialArcRepositoryClient");
    }

    @After
    public void tearDown() throws SQLException, IllegalAccessException, NoSuchFieldException {
        if (hcs != null) {
            hcs.close();
        }
        JMSConnectionMockupMQ.clearTestQueues();
        ChannelsTesterHelper.resetChannels();
        rs.tearDown();
    }

    @Test
    public void testIsSingleton() {
        hcs = ClassAsserts.assertSingleton(HarvestControllerServer.class);
    }

    /**
     * Testing that server starts and log-file logs this !
     */
    @Test
    public void testServerStarting() throws IOException {
        LogbackRecorder lr = LogbackRecorder.startRecorder();
        Settings.set(HarvesterSettings.HARVEST_CONTROLLER_SERVERDIR, SERVER_DIR.getAbsolutePath());
        hcs = HarvestControllerServer.getInstance();
        lr.assertLogContains("Log should contain start message.", START_MESSAGE);
        lr.stopRecorder();
    }

    /**
     * Test that if the harvestcontrollerserver cannot start, the HACO listener will not be added
     */
    @Test
    public void testNoListerAddedOnFailure() {
        Settings.set(HarvesterSettings.HARVEST_CONTROLLER_SERVERDIR, "");
        try {
            hcs = HarvestControllerServer.getInstance();
            fail("HarvestControllerServer should have thrown an exception");
        } catch (Exception e) {
            // expected
        }
        assertEquals(
                "Should have no listeners to the HACO queue",
                0,
                ((JMSConnectionMockupMQ) JMSConnectionFactory.getInstance()).getListeners(
                        HarvestControllerServer.HARVEST_CHAN_VALID_RESP_ID).size());
    }

    /**
     * Tests resolution of Bug68 which prevents of creation of server-directory. if it is located more than one level
     * below an existing directory in the hierarchy
     */
    @Test
    public void testCreateServerDir() {
        File server_dir = new File(SERVER_DIR + "/server/server");
        Settings.set(HarvesterSettings.HARVEST_CONTROLLER_SERVERDIR, server_dir.getAbsolutePath());
        hcs = HarvestControllerServer.getInstance();
        assertTrue("Server Directory not created " + server_dir, server_dir.exists());
    }

    /**
     * Check that we receive the expected CrawlStatusMessages when we send a broken job to a HarvestControllerServer.
     * The case of a correctly-functioning job is more-or-less identical and is to be included in the IntegrityTester
     * suite.
     */
    @Test
    public  void testMessagesSentByFailedJob() throws InterruptedException {
        Settings.set(HarvesterSettings.HARVEST_CONTROLLER_SERVERDIR, SERVER_DIR.getAbsolutePath());
        hcs = HarvestControllerServer.getInstance();
        // make a dummy job
        Job j = JobTest.createDefaultJob();
        j.setJobID(1L);
        //
        // Break the job by setting its status to something other than SUBMITTED
        // so
        // that no harvest will actually be started
        //
        j.setStatus(JobStatus.DONE);
        NetarkivetMessage nMsg = new DoOneCrawlMessage(j, TestInfo.SERVER_ID, new HarvestDefinitionInfo("test", "test",
                "test"), TestInfo.emptyMetadata);
        JMSConnectionMockupMQ.updateMsgID(nMsg, "UNIQUE_ID");
        JMSConnectionMockupMQ con = (JMSConnectionMockupMQ) JMSConnectionFactory.getInstance();
        CrawlStatusMessageListener listener = new CrawlStatusMessageListener();
        con.setListener(HarvesterChannels.getTheSched(), listener);
        ObjectMessage msg = JMSConnectionMockupMQ.getObjectMessage(nMsg);
        hcs.onMessage(msg);
        con.waitForConcurrentTasksToFinish();
        //
        // should have received two messages - one with status started and one
        // one with status failed
        //
        assertEquals("Should have received two messages", 2, listener.status_codes.size());
        //
        // Expect to receive two messages, although possibly out of order
        //
        JobStatus status_0 = listener.status_codes.get(0);
        JobStatus status_1 = listener.status_codes.get(1);

        assertTrue("Message statuses are " + status_0 + " and " + status_1,
                (status_0 == JobStatus.STARTED && status_1 == JobStatus.FAILED)
                        || (status_1 == JobStatus.STARTED && status_0 == JobStatus.FAILED));
        //
        // Check that JobIDs are corrects
        //
        assertEquals("JobIDs do not match for first message:", j.getJobID().longValue(),
                (listener.jobids.get(0)).longValue());
        assertEquals("JobIDs do not match for second message:", j.getJobID().longValue(),
                (listener.jobids.get(1)).longValue());
    }

    @Test
    public void testClose() throws IOException {
        LogbackRecorder lr = LogbackRecorder.startRecorder();
        Settings.set(HarvesterSettings.HARVEST_CONTROLLER_SERVERDIR, SERVER_DIR.getAbsolutePath());
        hcs = HarvestControllerServer.getInstance();
        hcs.close();
        hcs = null; // so that tearDown does not try to close again !!
        lr.assertLogContains("HarvestControllerServer not stopped !", CLOSE_MESSAGE);
        lr.stopRecorder();
    }

    /**
     * Tests that sending a doOneCrawlMessage with a value other than submitted results in a job-failed message being
     * sent back.
     */
    @Test
    public void testJobFailedOnBadMessage() throws JMSException {
        GenericMessageListener listener = new GenericMessageListener();
        JMSConnection con = JMSConnectionFactory.getInstance();
        con.setListener(HarvesterChannels.getTheSched(), listener);
        hcs = HarvestControllerServer.getInstance();
        Job theJob = JobTest.createDefaultJob();
        theJob.setStatus(JobStatus.DONE);
        theJob.setJobID(Long.valueOf(42L));
        String channel = Settings.get(HarvesterSettings.HARVEST_CONTROLLER_CHANNEL);
        NetarkivetMessage naMsg = new DoOneCrawlMessage(theJob,
                HarvesterChannels.getHarvestJobChannelId(new HarvestChannel("FOCUSED", false, true, "")),
                new HarvestDefinitionInfo("test", "test", "test"), TestInfo.emptyMetadata);
        JMSConnectionMockupMQ.updateMsgID(naMsg, "id1");
        ObjectMessage oMsg = JMSConnectionMockupMQ.getObjectMessage(naMsg);
        hcs.onMessage(oMsg);
        ((JMSConnectionMockupMQ) con).waitForConcurrentTasksToFinish();
        // Should send job-started and job-failed messages
        assertEquals("Should have received two messages", 2, listener.messagesReceived.size());
        JobStatus code0 = ((CrawlStatusMessage) listener.messagesReceived.get(0)).getStatusCode();
        JobStatus code1 = ((CrawlStatusMessage) listener.messagesReceived.get(1)).getStatusCode();
        assertTrue("Should have sent a STATUS_FAILED message", code0 == JobStatus.FAILED || code1 == JobStatus.FAILED);
    }

    /**
     * Test that starts (and stops) the HarvestControllerServer and verifies that found "old jobs" are treated as
     * expected. Thus, an "indirect" test of method processHarvestInfoFile().
     *
     * @param crawlDir the location of the crawldir
     * @param numberOfStoreMessagesExpected The number of stored messages expected. Usually number of files in dir + 1
     * for metadata arc file.
     * @param storeFailFile If not null, simulate failure on upload of this file
     * @return The CrawlStatusMessage returned by the HarvestControllerServer for the found job.
     */
    public CrawlStatusMessage testProcessingOfLeftoverJobs(File crawlDir, int numberOfStoreMessagesExpected,
            String storeFailFile) {
        final JMSConnectionMockupMQ con = (JMSConnectionMockupMQ) JMSConnectionFactory.getInstance();
        Settings.set(HarvesterSettings.HARVEST_CONTROLLER_SERVERDIR, crawlDir.getParentFile().getAbsolutePath());

        // Scheduler stub to check for crawl status messages
        GenericMessageListener sched = new GenericMessageListener();
        con.setListener(HarvesterChannels.getTheSched(), sched);
        con.waitForConcurrentTasksToFinish();
        assertEquals("Should not have received any messages yet", 0, sched.messagesReceived.size());
        // Start and close HCS, thus attempting to upload all ARC files found in arcsDir
        File dir1 = new File(WORKING_DIR, "dir1");
        File dir2 = new File(WORKING_DIR, "dir2");
        Settings.set("settings.common.arcrepositoryClient.fileDir", new File(ARCHIVE_DIR, "TestArchive")
                .getAbsolutePath(), new File(ARCHIVE_DIR, "TestArchive").getAbsolutePath());
        Settings.set(CommonSettings.ARC_REPOSITORY_CLIENT,
                "dk.netarkivet.common.distribute.arcrepository.LocalArcRepositoryClient");
        HarvestControllerServer hcs = HarvestControllerServer.getInstance();
        con.waitForConcurrentTasksToFinish();
        hcs.close();
        con.removeListener(HarvesterChannels.getTheSched(), sched);

        /*
         * The test serverDirs always contain exactly one job with one or more ARC files. Therefore, starting up the HCS
         * should generate exactly one FAILED status msg.
         */
        assertEquals("Should have received one crawl status message", 1, sched.messagesReceived.size());
        assertEquals("Job status should be FAILED", JobStatus.FAILED,
                ((CrawlStatusMessage) sched.messagesReceived.get(0)).getStatusCode());
        // The HCS should move found crawlDir to oldjobsdir
        assertFalse("Crawl directory should have been moved", crawlDir.exists());
        File expected_new_crawl_dir = new File(Settings.get(HarvesterSettings.HARVEST_CONTROLLER_OLDJOBSDIR),
                crawlDir.getName());
        File expected_new_arcs_dir = new File(expected_new_crawl_dir, "arcs");
        assertTrue("Should find crawl directory moved to " + expected_new_crawl_dir, expected_new_crawl_dir.exists());
        // The moved dir should only contain ARC files that couldn't be uploaded.
        int filesInCrawlDirAfterUpload = ("".equals(storeFailFile) ? 0 : 1);
        /*
         * ToDO Failing assert assertEquals("The moved dir should only contain ARC files that couldn't be uploaded.",
         * filesInCrawlDirAfterUpload, expected_new_arcs_dir.listFiles(FileUtils.ARCS_FILTER).length);
         */
        // Return the CrawlStatusMessage for further analysis.
        return (CrawlStatusMessage) sched.messagesReceived.get(0);
    }

    /**
     * Tests processing of leftover jobs in the case where all uploads go well.
     */
    @Test
    @Ignore("Failing because relevant crawl dirs are missing. Refactor to avoind having to include lots of test dirs")
    public void testProcessHarvestInfoFile() {
        CrawlStatusMessage message = testProcessingOfLeftoverJobs(
                TestInfo.LEFTOVER_CRAWLDIR_1,
                TestInfo.FILES_IN_LEFTOVER_JOB_DIR_1 + 1, "");
        assertEquals("Message should be for right job", 42L, message.getJobID());
    }

    /**
     * Tests processing of leftover jobs in the case where some uploads fail.
     */
    @Test
    @Ignore("IOFailure: The harvestInfoFile is version 0.3")
    public void fallingTestProcessHarvestInfoFileFails() {
        CrawlStatusMessage crawlStatusMessage = testProcessingOfLeftoverJobs(TestInfo.LEFTOVER_CRAWLDIR_2,
                TestInfo.FILES_IN_LEFTOVER_JOB_DIR_2.length + 1, TestInfo.FILES_IN_LEFTOVER_JOB_DIR_2[1]);
        assertEquals("Job upload message should detail number of failures",
                "No hosts report found, 1 files failed to upload", crawlStatusMessage.getUploadErrors());
        StringAsserts.assertStringMatches("Detailed upload message should declare which files failed",
                "Error uploading.*" + TestInfo.LEFTOVER_JOB_DIR_2_SOME_FILE_PATTERN,
                crawlStatusMessage.getUploadErrorDetails());
        StringAsserts.assertStringContains("Harvest should seem interrupted", "Crawl probably interrupted",
                crawlStatusMessage.getHarvestErrors());
        StringAsserts.assertStringMatches("Harvest should seem interrupted",
                "Crawl probably interrupted.*HarvestControllerServer", crawlStatusMessage.getHarvestErrorDetails());
        assertTrue("Failed CrawlStatusMessage should also be OK", crawlStatusMessage.isOk());

        String oldjobsdir = Settings.get(HarvesterSettings.HARVEST_CONTROLLER_OLDJOBSDIR);
        FileUtils.removeRecursively(new File(oldjobsdir));

        crawlStatusMessage = testProcessingOfLeftoverJobs(TestInfo.LEFTOVER_CRAWLDIR_3, 0, null);
        assertTrue("Failed CrawlStatusMessage should also be OK", crawlStatusMessage.isOk());

        assertTrue("Crawl.log must not have been deleted on error", new File(new File(oldjobsdir,
                TestInfo.LEFTOVER_CRAWLDIR_3.getName()), "logs/crawl.log").exists());

        assertTrue("Progress-statistics log must not have been deleted on error", new File(new File(oldjobsdir,
                TestInfo.LEFTOVER_CRAWLDIR_3.getName()), "logs/progress-statistics.log").exists());
    }

    /**
     * Test bug 852. the system property org.archive.crawler.frontier.AbstractFrontier.queue-assignment-policy must be
     * set by the HarvestControllerServer and include dk.netarkivet.harvester.harvesting.DomainnameQueueAssignmentPolicy
     * Also tests, that heritrix.version is set to Constants.getHeritrixVersion()
     */
    @Test
    public void testBug852() {
        hcs = HarvestControllerServer.getInstance();
        if (!System.getProperties()
                .containsKey("org.archive.crawler.frontier.AbstractFrontier.queue-assignment-policy")) {
            fail("org.archive.crawler.frontier.AbstractFrontier.queue-assignment-policy is not defined!!");
        }
        String assignmentPolicyList = System.getProperties().getProperty(
                "org.archive.crawler.frontier.AbstractFrontier.queue-assignment-policy");
        if (!assignmentPolicyList.contains("dk.netarkivet.harvester.harvesting.DomainnameQueueAssignmentPolicy")) {
            fail("NetarchiveSuite assignment policy not included in queue-assignment-policy");
        }
        if (!System.getProperties().containsKey("heritrix.version")) {
            fail("heritrix.version is not set");
        }
        String heritrixVersion = System.getProperties().getProperty("heritrix.version");
        if (!heritrixVersion.equals(Constants.getHeritrixVersionString())) {
            fail("The 'heritrix.version' property is not set to: " + Constants.getHeritrixVersionString());
        }

    }

    /**
     * Verify that preharvest metadata is found in the final metadata file. See also bug #738.
     * <p>
     * FIXME Fails in Hudson
     */
    @Test
    @Ignore("AssertionError: documentHarvest() should have generated final metadata")
    public void failingTestCopyPreharvestMetadata() throws NoSuchMethodException, IllegalAccessException,
            InvocationTargetException {
        // Set up harvest controller, a job some metadata and a crawlDir
        hcs = HarvestControllerServer.getInstance();
        Job job = JobTest.createDefaultJob();
        long jobId = 42L;
        job.setJobID(jobId);
        List<MetadataEntry> meta = new ArrayList<MetadataEntry>();
        meta.add(TestInfo.sampleEntry);
        File crawlDir = WORKING_DIR;
        File arcsDir = new File(crawlDir, Constants.ARCDIRECTORY_NAME);
        arcsDir.mkdir();
        // Write preharvest metadata file
        final HarvestController hc = HarvestController.getInstance();
        Method writePreharvestMetadata = ReflectUtils.getPrivateMethod(hc.getClass(), "writePreharvestMetadata",
                Job.class, List.class, File.class);
        writePreharvestMetadata.invoke(hc, job, meta, crawlDir);
        // Write final metadata file - should copy the preharvest metadata
        JobInfo jobInfo = new JobInfoTestImpl(jobId, job.getOrigHarvestDefinitionID());
        // FIXME hardwired to H1 HeritrixFiles
        HeritrixFiles files = HeritrixFiles.getH1HeritrixFilesWithDefaultJmxFiles(
        		crawlDir, jobInfo);
        IngestableFiles inf = new IngestableFiles(files);
        HarvestDocumentation.documentHarvest(inf);
        // Verify that metadata file has been generated
        // IngestableFiles inf = new IngestableFiles(files);
        assertTrue("documentHarvest() should have generated final metadata", inf.isMetadataReady());
        assertEquals("Expected just one metadata arc file", 1, inf.getMetadataArcFiles().size());
        File mf = inf.getMetadataArcFiles().get(0);
        // Verify that no surprises were found in the final metadata
        List<CDXRecord> mfContent = getCdx(mf);
        // After implementation of C.2.2 (Write harvest details)
        // we now get 3 records instead of just one:
        // the last 2 are records for the order.xml and seeds.txt
        assertEquals("Expected no records except our 3 metadata samples", 3, mfContent.size());
        // Verify that sampleEntry is in the final metadata
        CDXRecord rec = mfContent.get(0);
        assertEquals("The first record should be our metadata example record", TestInfo.sampleEntry.getURL(),
                rec.getURL());
        assertEquals("The first record should be our metadata example record", TestInfo.sampleEntry.getMimeType(),
                rec.getMimetype());
        assertEquals("The first record should be our metadata example record", TestInfo.sampleEntry.getData().length,
                rec.getLength());
    }

    /**
     * Runs an ExtractCDXJob on the given, local arc-file and formats the output. Everything stored in RAM - don't use
     * on large files!
     *
     * @param arcFile An arc-file present on the local system.
     * @return The full CDX index as List of CDXRecords.
     */
    private List<CDXRecord> getCdx(File arcFile) {
        List<CDXRecord> result = new ArrayList<CDXRecord>();
        ByteArrayOutputStream cdxBaos = new ByteArrayOutputStream();
        BatchLocalFiles batchRunner = new BatchLocalFiles(new File[] {arcFile});
        batchRunner.run(new ExtractCDXJob(), cdxBaos);
        for (String cdxLine : cdxBaos.toString().split("\n")) {
            result.add(new CDXRecord(cdxLine.split("\\s+")));
        }
        return result;
    }

}
