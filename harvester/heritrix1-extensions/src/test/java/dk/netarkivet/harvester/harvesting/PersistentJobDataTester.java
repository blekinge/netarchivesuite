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
package dk.netarkivet.harvester.harvesting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Date;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import dk.netarkivet.common.exceptions.ArgumentNotValid;
import dk.netarkivet.common.utils.FileUtils;
import dk.netarkivet.harvester.datamodel.HarvestDefinitionInfo;
import dk.netarkivet.harvester.datamodel.Job;
import dk.netarkivet.testutils.preconfigured.MoveTestFiles;

/**
 * Test class PersistentJobData.
 */
public class PersistentJobDataTester {
    MoveTestFiles mtf = new MoveTestFiles(TestInfo.ORIGINALS_DIR, TestInfo.WORKING_DIR);
    private File crawldir;

    @Before
    public void setUp() throws Exception {
        mtf.setUp();
        crawldir = new File(TestInfo.WORKING_DIR, "my-crawldir");
        assertTrue("Unable to create crawldir '" + crawldir.getAbsolutePath() + "'", crawldir.mkdir());
    }

    @After
    public void tearDown() throws Exception {
        mtf.tearDown();
    }

    /**
     * Test constructor for PersistentJobData. 1. Throws ArgumentNotValid, if file argument null or file does not exist.
     * 2. accepts existing directory as argument
     */
    @Test
    public void testConstructor() {
        try {
            new PersistentJobData(null);
            fail("PersistentJobData should have thrown an exception when given null-argument");
        } catch (ArgumentNotValid e) {
            // expected
        }

        try {
            new PersistentJobData(new File("nonExistingDir"));
            fail("PersistentJobData should have thrown an exception when given " + " non existingdir as argument");
        } catch (ArgumentNotValid e) {
            // expected
        }

        // Check that an existing dir doesn't throw an exception.
        new PersistentJobData(TestInfo.TEST_CRAWL_DIR);
    }

    /**
     * Test that the write(Job) method persists all necessary information about the current harvest-job.
     *
     * @throws Exception If failure to persist the information or unable to access DB
     */
    @Test
    public void testWrite() throws Exception {
        PersistentJobData pjd = new PersistentJobData(crawldir);
        Job testJob = TestInfo.getJob();
        testJob.setJobID(42L);
        testJob.setSubmittedDate(new Date());
        testJob.setHarvestAudience("Default Audience");
        pjd.write(testJob, new HarvestDefinitionInfo("test", "test", "test"));

        PersistentJobData pjdNew = new PersistentJobData(crawldir);

        assertEquals("retrieved jobID is not the same as original jobID", testJob.getJobID(), pjdNew.getJobID());
        assertEquals("retrieved jobpriority is not the same as original job priority", testJob.getChannel(),
                pjdNew.getChannel());
        assertEquals("retrived maxBytesPerDomain is not the same as original job maxBytesPerDomain",
                testJob.getMaxBytesPerDomain(), pjdNew.getMaxBytesPerDomain());
        assertEquals("retrived maxObjectsPerDomain is not the same as original job maxObjectsPerDomain",
                testJob.getMaxObjectsPerDomain(), pjdNew.getMaxObjectsPerDomain());
        assertEquals("retrived harvestNum is not the same as original job harvestNum", testJob.getHarvestNum(),
                pjdNew.getJobHarvestNum());
        assertEquals("retrived orderXMlName is not the same as original job orderXMLName", testJob.getOrderXMLName(),
                pjdNew.getOrderXMLName());
        assertEquals("retrived origHarvestDefinitionID is not the same as original ID",
                testJob.getOrigHarvestDefinitionID(), pjdNew.getOrigHarvestDefinitionID());
        assertEquals("The value of the performer should be set", pjdNew.getPerformer(), null);

        // cleanup after this unit-test.
        FileUtils.removeRecursively(crawldir);
    }

    /** Test reading the version 0.5 harvestInfo.xml. The newest */
    @Test
    public void testReadVersion0_5() {
        File hiVersion03 = new File(TestInfo.DATA_DIR, "harvestInfo-0.5.xml");
        FileUtils.copyFile(hiVersion03, new File(crawldir, TestInfo.HarvestInfofilename));
        PersistentJobData pjd = new PersistentJobData(crawldir);
        pjd.getVersion();
    }

    /** Test reading the 0.4 harvestInfo.xml. */
    @Test
    public void testReadVersion0_4() {
        File hiVersion03 = new File(TestInfo.DATA_DIR, "harvestInfo-0.4.xml");
        FileUtils.copyFile(hiVersion03, new File(crawldir, TestInfo.HarvestInfofilename));
        PersistentJobData pjd = new PersistentJobData(crawldir);
        pjd.getVersion();
    }

}