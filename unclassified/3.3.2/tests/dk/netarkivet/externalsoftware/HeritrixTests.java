/*$Id$
* $Revision$
* $Date$
* $Author$
*
* The Netarchive Suite - Software to harvest and preserve websites
* Copyright 2004-2007 Det Kongelige Bibliotek and Statsbiblioteket, Denmark
*
* This library is free software; you can redistribute it and/or
* modify it under the terms of the GNU Lesser General Public
* License as published by the Free Software Foundation; either
* version 2.1 of the License, or (at your option) any later version.
*
* This library is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this library; if not, write to the Free Software
* Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
*/
package dk.netarkivet.externalsoftware;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import is.hi.bok.deduplicator.DeDuplicator;
import junit.framework.TestCase;
import org.apache.commons.httpclient.URIException;
import org.archive.net.UURI;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.dom4j.util.XMLErrorHandler;
import org.xml.sax.SAXException;

import dk.netarkivet.archive.indexserver.CrawlLogIndexCache;
import dk.netarkivet.common.exceptions.IOFailure;
import dk.netarkivet.common.utils.FileUtils;
import dk.netarkivet.common.utils.XmlUtils;
import dk.netarkivet.common.utils.cdx.ExtractCDX;
import dk.netarkivet.harvester.datamodel.Domain;
import dk.netarkivet.harvester.datamodel.StopReason;
import dk.netarkivet.harvester.harvesting.FixedUURI;
import dk.netarkivet.harvester.harvesting.HeritrixDomainHarvestReport;
import dk.netarkivet.harvester.harvesting.HeritrixFiles;
import dk.netarkivet.harvester.harvesting.HeritrixLauncher;
import dk.netarkivet.harvester.harvesting.distribute.DomainHarvestReport;
import dk.netarkivet.testutils.FileAsserts;
import dk.netarkivet.testutils.LuceneUtils;
import dk.netarkivet.testutils.ReflectUtils;
import dk.netarkivet.testutils.StringAsserts;
import dk.netarkivet.testutils.TestUtils;
import dk.netarkivet.testutils.TestFileUtils;
import dk.netarkivet.testutils.preconfigured.MoveTestFiles;


/**
 * Tests various aspects of launching Heritrix and Heritrix' capabilities.
 * Note that some of these tests require much heap space, so JVM parameter
 * -Xmx512M may be required.
 * Created by IntelliJ IDEA.
 * User: larsrc
 * Date: Dec 2, 2004
 * Time: 11:55:51 AM
 * To change this template use File | Settings | File Templates.
 */
public class HeritrixTests extends TestCase {

    protected final static String WRITE_PROCESSORS_XPATH = "/crawl-order/controller/map[@name='write-processors']";
    protected final static String DEDUPLICATOR_XPATH = WRITE_PROCESSORS_XPATH
        + "/newObject[@name='DeDuplicator']";

    protected final static String DEDUPLICATOR_INDEX_LOCATION_XPATH  = DEDUPLICATOR_XPATH
    + "/string[@name='index-location']";
    protected final static String DEDUPLICATOR_MATCHING_METHOD_XPATH  = DEDUPLICATOR_XPATH
    + "/string[@name='matching-method']";

    protected final static String DEDUPLICATOR_ORIGIN_HANDLING_XPATH = DEDUPLICATOR_XPATH
    + "/string[@name='origin-handling']";

    private HeritrixLauncher hl;
    private MoveTestFiles mtf;

    public HeritrixTests() {
        mtf = new MoveTestFiles(TestInfo.ORIGINALS_DIR, TestInfo.WORKING_DIR);
    }


    public void setUp() throws Exception{
        FileUtils.removeRecursively(TestInfo.WORKING_DIR);

        long endtime = System.currentTimeMillis() + 1500;
        while (System.currentTimeMillis() < endtime) {
        }
        mtf.setUp();
        //TestInfo.WORKING_DIR.mkdirs();

        // Check, that dk.netarkivet.harvester.harvesting.DomainnameQueueAssignmentPolicy
        // is recognized as a valid QueueAssignmentPolicy

        // This setting should set either directly by -Dorg.archive.crawler.frontier.AbstractFrontier.queue-assignment-policy=
        //  org.archive.crawler.frontier.HostnameQueueAssignmentPolicy,org.archive.crawler.frontier.IPQueueAssignmentPolicy,
        //  org.archive.crawler.frontier.BucketQueueAssignmentPolicy,
        //  org.archive.crawler.frontier.SurtAuthorityQueueAssignmentPolicy,
        //  dk.netarkivet.harvester.harvesting.DomainnameQueueAssignmentPolicy

        if (!System.getProperties().containsKey("org.archive.crawler.frontier.AbstractFrontier.queue-assignment-policy")) {
            fail ("org.archive.crawler.frontier.AbstractFrontier.queue-assignment-policy is not defined!!");
        }
     }

    public void tearDown() throws InterruptedException {
        // it takes a little while for heritrix to close completely (including all threads)
        // so we have to wait a little while here !
        Thread.sleep(5000);
        mtf.tearDown();
        Thread.sleep(5000);
    }

    /**
     * Centralized place for tests to construct a HeritrixLauncher.
     *  - Constructs the given crawlDir.
     *  - Copies the given order.xml to the proper place in the given crawlDir.
     *  - Copies the given seeds.txt to the proper place in the given crawlDir.
     *  - Copies the given indexDir to the proper place in the given crawlDir (index)
     *  - Constructs a HeritrixLauncher and returns it
     *  @param origOrderXml the given order.xml
     *  @param origSeedsFile the given seeds file
     *  @param origIndexDir the given index directory
     *  @return a HeritrixLauncher object
     *
     *  @throws IOException
     *
     */
    private HeritrixLauncher getHeritrixLauncher(File origOrderXml,
                                                 File origSeedsFile,
                                                 File origIndexDir)
            throws IOException {
        if (!origOrderXml.exists()){
            fail ("order-File does not exist: " + origOrderXml.getAbsolutePath());
        }
        if (!origSeedsFile.exists()){
            fail ("seeds-File does not exist: " + origSeedsFile.getAbsolutePath());
        }
        if (!origIndexDir.exists()){
            fail ("Index dir does not exist: " + origIndexDir.getAbsolutePath());
        }
        if (!origIndexDir.isDirectory()){
            fail ("Index dir is not a directory: " + origIndexDir.getAbsolutePath());
        }

        //File origSeeds = TestInfo.SEEDS_FILE;
        File crawlDir = TestInfo.HERITRIX_TEMP_DIR;
        crawlDir.mkdirs();
        File orderXml = new File(crawlDir, "order.xml");

        File seedsTxt = new File(crawlDir, "seeds.txt");

        FileUtils.copyFile(origOrderXml,orderXml);
        if (!orderXml.exists()){
            fail ("order-File does not exist: " + orderXml.getAbsolutePath());
        }
        FileUtils.copyFile(origSeedsFile,seedsTxt);
        HeritrixFiles files = new HeritrixFiles(crawlDir,
                TestInfo.JOBID, TestInfo.HARVESTID);

        /*
        File tempDir = mtf.newTmpDir();
        LuceneUtils.makeDummyIndex(tempDir);
        */
        File[] gzippableFiles = origIndexDir.listFiles();
        for (File f : gzippableFiles) {
            GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream(
                    new File(f.getAbsolutePath() + ".gz")));
            FileUtils.writeFileToStream(f, out);
            out.close();
            FileUtils.remove(f);
        }
        files.writeIndex(origIndexDir);

        //TestFileUtils.copyDirectoryNonCVS(origIndexDir, indexDir);

        return HeritrixLauncher.getInstance(files);
    }

    /**
     * Run heritrix with the given order, seeds file and index.
     *
     * @param order An order.xml file as per Heritrix specs
     * @param seeds A file with seeds, one per line
     * @param index the lucene-index
     * @throws IOException
     */
    protected void runHeritrix(File order, File seeds, File index) throws IOException {

        hl = getHeritrixLauncher(order, seeds, index);
        hl.doCrawl();
    }

    /**
     * Check that all urls in the given array are listed in the crawl log.
     * Calls fail() at the first url that is not found or if the crawl log is not
     * found.
     *
     * @param urls An array of url strings
     * @throws IOException If TestInfo.HERITRIX_CRAWL_LOG_FILE is not found or is unreadable
     */
    protected void assertAllUrlsInCrawlLog(String[] urls) throws IOException {
        String crawlLog = "";
        crawlLog = FileUtils.readFile(TestInfo.HERITRIX_CRAWL_LOG_FILE);

        for (String s1 : Arrays.asList(urls)) {
            String s = s1;
            if (crawlLog.indexOf(s) == -1) {
                System.out.println("Crawl log: ");
                System.out.println(crawlLog);
                fail("URL " + s + " not found in crawl log");
            }
        }
    }

    /**
     * Check that no urls in the given array are listed in the crawl log.
     * Calls fail() at the first url that is found or if the crawl log is not
     * found.
     *
     * @param urls An array of url strings
     * @throws IOException If TestInfo.HERITRIX_CRAWL_LOG_FILE is not found or is unreadable
     */
    protected void assertNoUrlsInCrawlLog(String[] urls) throws IOException {
        String crawlLog = "";
        crawlLog = FileUtils.readFile(TestInfo.HERITRIX_CRAWL_LOG_FILE);

        for (String s1 : Arrays.asList(urls)) {
            String s = s1;
            if (crawlLog.indexOf(s) != -1) {
                System.out.println("Crawl log: ");
                System.out.println(crawlLog);
                fail("URL " + s + " found in crawl log at " + crawlLog.indexOf(
                        s));
            }
        }
    }

    /**
     * Test that the launcher handles an empty order file correctly.
     * @throws IOException
     */
    public void testStartEmptyFile() throws IOException {
        File tempDir = mtf.newTmpDir();
        LuceneUtils.makeDummyIndex(tempDir);
        hl = getHeritrixLauncher(TestInfo.EMPTY_ORDER_FILE, TestInfo.SEEDS_FILE, tempDir);

        try {
            hl.doCrawl();
            fail("An exception should have been caught when launching with an empty order.xml file !");
        } catch (IOFailure e) {
            // Expected case
        }
    }



    /**
     * Test that the launcher actually launches Heritrix and generates at least one arcfile
     * @throws IOException
     */
    public void testLaunch() throws IOException {
        validateOrder(TestInfo.ORDER_FILE);
        File tempDir = mtf.newTmpDir();
        LuceneUtils.makeDummyIndex(tempDir);
        runHeritrix(TestInfo.ORDER_FILE, TestInfo.SEEDS_FILE, tempDir);

        String progressLog = "";
        progressLog = FileUtils.readFile(TestInfo.HERITRIX_PROGRESS_LOG_FILE);

        // test that crawl.log has registered a known URL
        assertAllUrlsInCrawlLog(new String[]{TestInfo.SEARCH_FOR_THIS_URL});

        // test that progress-statistics.log has reported CRAWL ENDED
        StringAsserts.assertStringContains(
                "progress-statistics.log skal melde hostningen fardig",
                "CRAWL ENDED", progressLog);

        // test that both the heritrix-temp-dir and the bitarchive has at least one file - and has the same file !!
        File[] files = TestInfo.HERITRIX_ARCS_DIR.listFiles(FileUtils.ARCS_FILTER);
        File first_arcfile = files[0];
        if (first_arcfile == null) {
            fail("'" + TestInfo.HERITRIX_ARCS_DIR.getAbsolutePath() + "' har ingen arcfiler !");
        }
    }

    /**
     * Test that the launcher actually launches Heritrix and fetches at least 50 objects from
     * different hosts on tv2.dk (sporten.tv2.dk, nyheder.tv2.dk.....) and netarkivet.dk
     * by parsing the hosts-report.txt
     * This number includes the dns-lookups for each host in these domains.
     * @throws IOException
     */
    public void testLaunchWithMaxObjectsPrDomain() throws IOException {
        validateOrder(TestInfo.ORDER_FILE_MAX_OBJECTS);
        File tempDir = mtf.newTmpDir();
        LuceneUtils.makeDummyIndex(tempDir);
        runHeritrix(TestInfo.ORDER_FILE_MAX_OBJECTS, TestInfo.SEEDS_FILE_MAX_OBJECTS, tempDir);

        File hostReportFile = new File(TestInfo.HERITRIX_TEMP_DIR, "logs/crawl.log");
        DomainHarvestReport hhr = new HeritrixDomainHarvestReport(hostReportFile, StopReason.DOWNLOAD_COMPLETE);
        Long tv2_objects = hhr.getObjectCount("tv2.dk");
        Long netarkivet_objects = hhr.getObjectCount("netarkivet.dk");
        //int netarkivetHosts = GetHostsForDomain(hostReportFile, "netarkivet.dk");
        assertTrue("Number of objects from tv2.dk should be at least 50, NOT: "
                + tv2_objects.longValue()
                + "\nNumbers generated from the following host-report.txt: "
                + FileUtils.readFile(hostReportFile) + "\n",
                tv2_objects.longValue() >= 50);
        assertTrue("Number of objects from netarkivet.dk should be at least 50, NOT: "
                + netarkivet_objects.longValue()
                + "\nNumbers generated from the following host-report.txt: "
                + FileUtils.readFile(hostReportFile) + "\n",
                netarkivet_objects >= 50);
    }

    /**
     * Test that the main method works and generates output from known working crawl
     * @throws IOException
     */
    public void testLaunchMain() throws IOException {
        validateOrder(TestInfo.ORDER_FILE);
        File tempDir = mtf.newTmpDir();
        LuceneUtils.makeDummyIndex(tempDir);
        hl = getHeritrixLauncher(TestInfo.ORDER_FILE, TestInfo.SEEDS_FILE, tempDir);
        hl.doCrawl();

        String progressLog = "";
        String crawlLog = "";
        progressLog = FileUtils.readFile(TestInfo.HERITRIX_PROGRESS_LOG_FILE);
        crawlLog = FileUtils.readFile(TestInfo.HERITRIX_CRAWL_LOG_FILE);

        // test that crawl.log has registered a known URL
        StringAsserts.assertStringContains(
                "crawl.log skulle have registreret URL'en: "
                        + TestInfo.SEARCH_FOR_THIS_URL,
                TestInfo.SEARCH_FOR_THIS_URL, crawlLog);

        // test that progress-statistics.log has reported CRAWL ENDED
        StringAsserts.assertStringContains(
                "progress-statistics.log skulle have meldt hostningen fardig",
                "CRAWL ENDED", progressLog);

        // test that both the heritrix-temp-dir has at least one file
        assertTrue("'" + TestInfo.HERITRIX_ARCS_DIR.getAbsolutePath()
                + "' har ingen arcfiler !",
                TestInfo.HERITRIX_ARCS_DIR.listFiles(FileUtils.ARCS_FILTER).length >= 1);

    }

    /**
     * Test that Heritrix can use a URL seed list to define a harvest.
     * This tests requirement #1.
     * @throws IOException
     */
    public void testUrlSeedList() throws IOException {
        validateOrder(TestInfo.ORDER_FILE);
        File tempDir = mtf.newTmpDir();
        LuceneUtils.makeDummyIndex(tempDir);
        runHeritrix(TestInfo.ORDER_FILE, TestInfo.SEEDS_FILE2, tempDir);

        assertAllUrlsInCrawlLog(new String[]{
            "http://netarkivet.dk/website/sources/JavaArcUtils-0.1.tar.gz",
            "http://netarkivet.dk/website/press/Bryllup-20040706.pdf",
            "http://netarkivet.dk/proj/pilot_juli2001.pdf"
        });

        // Check that the unintended ones didn't get caught:
        // URLs that have links to the specified files.
        assertNoUrlsInCrawlLog(new String[] {
            "http://netarkivet.dk/website/sources/index-da.htm",
            "http://netarkivet.dk/website/sources/index-en.htm",
            "http://netarkivet.dk/website/press/index-da.htm",
            "http://netarkivet.dk/website/press/index-en.htm",
            "http://netarkivet.dk/pilot-index-da.htm",
            "http://netarkivet.dk/pilot-index-en.htm"
        });
    }

    /**
     * Test that Heritrix can limit the number of objects harvested pr. domain
     * This tests requirement #7.
     * @throws IOException
     */
    public void testRestrictNumObjectsPrDomain() throws IOException {
        validateOrder(TestInfo.MAX_OBJECTS_ORDER_FILE);
        File tempDir = mtf.newTmpDir();
        LuceneUtils.makeDummyIndex(tempDir);
        runHeritrix(TestInfo.MAX_OBJECTS_ORDER_FILE, TestInfo.SEEDS_FILE, tempDir);

        int num_harvested = 0;
        BufferedReader in = new BufferedReader(new FileReader(TestInfo.HERITRIX_CRAWL_LOG_FILE));
        while (in.readLine() != null) {
            num_harvested++;
        }

        // we must harvest at max MAX_OBJECTS + 1 (the harvester some times stops at MAX_OBJECTS + 1)
        assertTrue("Number of objects harvested", num_harvested < TestInfo.MAX_OBJECTS + 2);
    }

    /**
     * Test that Heritrix can limit the number of objects pr. harvest
     * This tests requirement #7.
     * @throws IOException
     */
    public void testRestrictNumObjectsHarvested() throws IOException {
        validateOrder(TestInfo.MAX_OBJECTS_ORDER_FILE);
        File tempDir = mtf.newTmpDir();
        LuceneUtils.makeDummyIndex(tempDir);
        runHeritrix(TestInfo.MAX_OBJECTS_ORDER_FILE, TestInfo.SEEDS_FILE, tempDir);

        String progressStatistics = "";
        progressStatistics = FileUtils.readFile(TestInfo.HERITRIX_PROGRESS_LOG_FILE);
        StringAsserts.assertStringContains(
                "Must end by hitting max number of objects",
                "CRAWL ENDED - Finished - Maximum number of documents limit hit",
                progressStatistics);

        int num_harvested = 0;
        BufferedReader in = new BufferedReader(new FileReader(TestInfo.HERITRIX_CRAWL_LOG_FILE));
        while (in.readLine() != null) {
            num_harvested++;
        }

        // we must harvest at max MAX_OBJECTS + 1 (the harvester some times stops at MAX_OBJECTS + 1)
        assertTrue("Number of objects harvested", num_harvested < TestInfo.MAX_OBJECTS + 2);
    }

    /**
     * Test that Heritrix can handle cookies - setting and changing
     * This tests requirement #28.
     * @throws IOException
     */
    public void testCookiesSupport() throws IOException {
        validateOrder(TestInfo.COOKIES_ORDER_FILE);
        File tempDir = mtf.newTmpDir();
        LuceneUtils.makeDummyIndex(tempDir);
        runHeritrix(TestInfo.COOKIES_ORDER_FILE, TestInfo.COOKIE_SEEDS_FILE, tempDir);

        // test that both the heritrix-temp-dir and the bitarchive has at least one file - and has the same file !!
        File[] files = TestInfo.HERITRIX_ARCS_DIR.listFiles(FileUtils.ARCS_FILTER);
        assertNotNull("Files array should be non-null", files);
        assertEquals("Should be exactly one file in " + TestInfo.HERITRIX_ARCS_DIR.getAbsolutePath(),
                1, files.length);
        File first_arcfile = files[0];
        assertNotNull("Should be ARC files in " + TestInfo.HERITRIX_ARCS_DIR.getAbsolutePath(),
                first_arcfile);
        String arcfile = FileUtils.readFile(files[0]);
        // Testing that cookie1 exists because cookie0 will be there
        // with 404.
        StringAsserts.assertStringContains("Must find the web pages", "http://netarkivet.dk/website/testsite/cookie1.php", arcfile);
        StringAsserts.assertStringContains("No cookie1 must be set before", "cookie0.php: cookie1 is not set", arcfile);
        StringAsserts.assertStringContains("No cookie2 must be set before", "cookie0.php: cookie2 is not set", arcfile);
        StringAsserts.assertStringContains("No cookie3 must be set before", "cookie0.php: cookie3 is not set", arcfile);
        StringAsserts.assertStringContains("Cookie-setting page must be found", "This sets cookie testcookie1", arcfile);
        StringAsserts.assertStringContains("No cookie1 must be set on the page that sets the cookie", "cookie1.php: cookie1 is not set", arcfile);
        StringAsserts.assertStringContains("No cookie2 must be set on the page that sets the cookie", "cookie1.php: cookie2 is not set", arcfile);
        StringAsserts.assertStringContains("No cookie3 must be set on the page that sets the cookie", "cookie1.php: cookie3 is not set", arcfile);
        StringAsserts.assertStringContains("Cookie1 must be found after setting", "cookie2.php: cookie1 value is test1", arcfile);
        StringAsserts.assertStringContains("Cookie2 must not be found after setting", "cookie2.php: cookie2 is not set", arcfile);
        StringAsserts.assertStringContains("Cookie3 must be found after setting", "cookie2.php: cookie3 value is test3", arcfile);
        StringAsserts.assertStringContains("Cookie-changing page must be found", "This changes cookie testcookie1", arcfile);
        StringAsserts.assertStringContains("Cookie1 must be changed after changing", "cookie5.php: cookie1 value is test2", arcfile);
        StringAsserts.assertStringContains("Cookie2 must not be found after changing cookie1", "cookie5.php: cookie2 is not set", arcfile);
        StringAsserts.assertStringContains("Cookie3 must not be changed after changing", "cookie5.php: cookie3 value is test3", arcfile);
    }

    /**
     * Test that Heritrix can use a regular expression to limit a harvest
     * Tests with regular expression .*(ArcUtils\.[0-9]\.[0-1]|-da.htm).*
     * which takes the danish index pages and two of three ArcUtil sources.
     * <p/>
     * This tests requirement #29.
     * @throws IOException
     */
    public void testUrlExpressionRestriction() throws IOException {
        validateOrder(TestInfo.RESTRICTED_URL_ORDER_FILE);
        File tempDir = mtf.newTmpDir();
        LuceneUtils.makeDummyIndex(tempDir);
        runHeritrix(TestInfo.RESTRICTED_URL_ORDER_FILE, TestInfo.SEEDS_FILE, tempDir);

        // Check that we got a bunch of the expected ones
        assertAllUrlsInCrawlLog(new String[]{
            "http://netarkivet.dk/index-da.php",
            "http://netarkivet.dk/kildetekster/index-da.php",
            "http://netarkivet.dk/kildetekster/ProxyViewer-0.1.tar.gz",
        });
        // Check that the unintended ones didn't get caught:
        // English index files, 0.2 tarball, gifs, javascript
        assertNoUrlsInCrawlLog(new String[]{
            "http://netarkivet.dk/index-en.php",
            "http://netarkivet.dk/kildetekster/index-en.php",
            "http://netarkivet.dk/kildetekster/JavaArcUtils-0.3.tar.gz",
            "http://netarkivet.dk/netarkivet_alm/billeder/spacer.gif"
        });
    }
    /**
     * Test that the Maxbytes feature is handled correctly by the
     * the current harvester.
     * Sets maxbytes limit to 500000 bytes.
     * @throws DocumentException
     * @throws IOException
     * @throws IOFailure
     */
    public void testMaxBytes() throws IOException, IOFailure, DocumentException {
        File MaxbytesOrderFile = new File(TestInfo.WORKING_DIR, "maxBytesOrderxml.xml");
        FileUtils.copyFile(TestInfo.DEFAULT_ORDERXML_FILE, MaxbytesOrderFile);
        FileUtils.copyFile(TestInfo.HERITRIX_SETTINGS_SCHEMA_FILE,
                new File(TestInfo.WORKING_DIR, TestInfo.HERITRIX_SETTINGS_SCHEMA_FILE.getName()));
        Document orderDocument = XmlUtils.getXmlDoc(MaxbytesOrderFile);
        // Not sure what the bytelimit should be to be consistent with what we presently expect Heritrix
        // to do
        long byteLimit = 500000;
        String xpath =
            "/crawl-order/controller/map[@name='pre-fetch-processors']"
            + "/newObject[@name='QuotaEnforcer']"
            //+ "/long[@name='group-max-success-kb']";
            + "/long[@name='group-max-all-kb']";
       Node groupMaxSuccessKbNode = orderDocument.selectSingleNode(xpath);
        if (groupMaxSuccessKbNode != null) {
            // Divide by 1024 since Heritrix uses KB rather than bytes,
            // and add 1 to avoid to low limit due to rounding.
            groupMaxSuccessKbNode.setText(
                    Long.toString((byteLimit / 1024)
                                  + 1)
            );
        } else {
            fail ("QuotaEnforcer node not found in order.xml");
        }
        OutputStream os = new FileOutputStream(MaxbytesOrderFile);
        XMLWriter writer = new XMLWriter(os);
        writer.write(orderDocument);

        validateOrder(MaxbytesOrderFile);
        File tempDir = mtf.newTmpDir();
        LuceneUtils.makeDummyIndex(tempDir);
        runHeritrix(MaxbytesOrderFile, TestInfo.SEEDS_DEFAULT, tempDir);
        File hostReportFile = new File(TestInfo.HERITRIX_TEMP_DIR, "logs/crawl.log");
        DomainHarvestReport hhr = new HeritrixDomainHarvestReport(hostReportFile, StopReason.DOWNLOAD_COMPLETE);
        Long netarkivet_bytes = hhr.getByteCount("netarkivet.dk");
        long lastNetarkivetBytes = getLastFetchedBytesForDomain("netarkivet.dk");
        //System.out.println("last netarkivet bytes: " + lastNetarkivetBytes);
        //System.out.println(FileUtils.readFile(hostReportFile));
        if (!(netarkivet_bytes.longValue() - lastNetarkivetBytes < byteLimit)) {
            fail ("byteLimit (" + netarkivet_bytes.longValue() + ") exceeded");
        }
    }

    /**
     * Tests, whether org.archive.io.RecoverableIOException 
     * from the ARCReader can be serialized (bug 755)
     * @throws IOException
     */
    public void testArcReaderBug755() throws IOException {
        try {
            throw new org.archive.io.RecoverableIOException(
                    "Forced exception: Hit EOF before header EOL");
        } catch (Exception e) {
           // Serialize exception
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream ous = new ObjectOutputStream(baos);
            ous.writeObject(e);
            ous.close();
            baos.close();
        }
    }


    private long getLastFetchedBytesForDomain (String domainName) {

        List<String> crawlLogLines = FileUtils.readListFromFile(TestInfo.HERITRIX_CRAWL_LOG_FILE);
        for (int i = crawlLogLines.size() -1; i > -1; i--) {
            String[] lineparts = crawlLogLines.get(i).split(" ");
            // Remove superfluous spaces between parts to allow proper parsing of crawl-log
            StringBuffer sb = new StringBuffer();
            for (String linepart : lineparts) {
                if (!linepart.trim().equals("")) {
                    sb.append(linepart.trim() + " ");
                }
            }
            lineparts = (sb.toString().trim()).split(" ");
            //System.out.println("line " + i + " contains " + lineparts.length +" elements");
            //System.out.println("line " + i + ": " + crawlLogLines.get(i));
            if (lineparts[1].equals("-5003")) {
                // Ignore these lines; These urls are skipped as the maxbytes limit has been passed
            } else {
                int bytesIndex = 2;
                int urlIndex = 3;
                try {
                    URL url = new URL(lineparts[urlIndex]);
                    //System.out.println("URL (" + lineparts[urlIndex] + ") has domain: "
                    //        + Domain.domainNameFromHostname(url.getHost()));
                    if (Domain.domainNameFromHostname(url.getHost()).equals(domainName)) {
                        return Long.parseLong(lineparts[bytesIndex]);
                    }
                }
                catch (MalformedURLException e) {
                    System.out.println("line caused an MalformedURLException: "
                            + crawlLogLines.get(i));
                    e.printStackTrace();
                }
            }

        }
        return 0L;
    }

    /**
     * Test that our default order.xml can be validated against the latest heritrix_settings.xsd.
     * The latest version: http://cvs.sourceforge.net/viewcvs.py/archive-crawler/ArchiveOpenCrawler/src/webapps/admin/
     * This file is also found in the src distributions in the src/webapps/admin/ directory.
     * Note: This heritrix_settings.xsd needs now to be in the same directory as the order-file.
     */
    public void testIfDefaultOrderXmlIsStillValid() {
        File order = TestInfo.DEFAULT_ORDERXML_FILE;
        validateOrder(order);
    }

    /**
     * Verify, that FixedUURI solves bug 820, and that
     * the class org.archive.net.UURI still has the problem.
     * When bug 820 is resolved in the Heritrix class, this test will fail,
     * and FixedUURI can be removed.
     * @throws URIException
     */
    public void testBug820() throws URIException {
        String troublesomeURL = "http/www.test.foo";
        try {
            new FixedUURI(troublesomeURL, false).getReferencedHost();
        } catch (NullPointerException e) {
            fail("Should not throw an NullPointerException here: " + e);
        }

        try {
            new UURI(troublesomeURL, false).getReferencedHost();
            fail("Bug 820 seems to be solved now. We can now remove FixedUURI");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    /**
     * Test we can use the DeDupFetch fetch-processors.
     * @throws Exception
     */
    public void testDedupfetchOrderXml() throws Exception {
        if (!TestUtils.runningAs("LC")) {
            return; // This is excluded until we have a working DedupFetch
                    // and a proper test for it.
        }
        validateOrder(TestInfo.DEDUPFETCH_ORDERXML_FILE);
        Document d = XmlUtils.getXmlDoc(TestInfo.DEDUPFETCH_ORDERXML_FILE);
        String INDEX_LOCATION_PATH =  "/crawl-order/controller/map[@name='fetch-processors']"
            + "/newObject[@name='HTTP']"
            + "/string[@name='index-location']";
        Node indexLocationNode = d.selectSingleNode(INDEX_LOCATION_PATH);
        File indexDir = mtf.newTmpDir();
        LuceneUtils.generateIndex(new File("tests/dk/netarkivet/externalsoftware/data/launcher/originals/netarkivet/testLaunchHarvest/logs/sorted-crawl.log"),
                getCXDReaderForArc(new File("tests/dk/netarkivet/externalsoftware/data/launcher/originals/netarkivet/testLaunchHarvest/arcs/42-23-20060726143926-00000-udvikling.kb.dk.arc.gz")),
                indexDir);
        if (indexLocationNode != null) {
            // Divide by 1024 since Heritrix uses KB rather than bytes,
            // and add 1 to avoid to low limit due to rounding.
           indexLocationNode.setText(new File(TestInfo.HERITRIX_TEMP_DIR, "index").getAbsolutePath());
        } else {
            fail ("IndexLocation node not found in order.xml");
        }
        File modifiedOrderFile = File.createTempFile("dedup_order","xml");

        OutputStream os = new FileOutputStream(modifiedOrderFile);
        XMLWriter writer = new XMLWriter(os);
        writer.write(d);
        
        runHeritrix(modifiedOrderFile, TestInfo.SEEDS_FILE2, indexDir);
        FileAsserts.assertFileMatches("Must have done some dedup",
                "Duplicates found:  [^0]",
                new File(TestInfo.HERITRIX_TEMP_DIR, "processors-report.txt"));
    }

    /**
     * Test we can use the Deduplicator write-processor.
     * @throws Exception
     */
    public void testDeduplicatorOrderXml() throws Exception {
        validateOrder(TestInfo.DEDUPLICATOR_ORDERXML_FILE);
        Document d = XmlUtils.getXmlDoc(TestInfo.DEDUPLICATOR_ORDERXML_FILE);

        String INDEX_LOCATION_PATH =  "/crawl-order/controller/map[@name='write-processors']"
            + "/newObject[@name='DeDuplicator']"
            + "/string[@name='index-location']";

        /** XPaths needed to insert/setup the DeDuplicator. */

        /** sample DeDuplicator block taken from faulty releasetest trial order.xml. */
//        <map name="write-processors">
//        <newObject name="DeDuplicator" class="is.hi.bok.deduplicator.DeDuplicator">
//          <boolean name="enabled">true</boolean>
//          <map name="filters"/>
//          <string name="index-location">/home/netarkiv/KFC/harvester_8081/2_1153919296552/index</string>
//          <string name="matching-method">By URL</string>
//          <boolean name="try-equivalent">true</boolean>
//          <boolean name="change-content-size">false</boolean>
//          <string name="mime-filter">^text/.*</string>
//          <string name="filter-mode">Blacklist</string>
//          <string name="analysis-mode">Timestamp</string>
//          <string name="log-level">SEVERE</string>
//          <string name="origin"/>
//          <string name="origin-handling">Use index information</string>
//          <boolean name="stats-per-host">true</boolean>
//        </newObject>


        // Set originHandling. Check that originHandling is one of AVAILABLE_ORIGIN_HANDLING
        // If not, set to default DEFAULT_ORIGIN_HANDLING, and log a warning.
        checkAndSetOrderXMLNode(d, DEDUPLICATOR_ORIGIN_HANDLING_XPATH, DeDuplicator.ATTR_ORIGIN_HANDLING,
                "Use index information", DeDuplicator.AVAILABLE_ORIGIN_HANDLING, DeDuplicator.DEFAULT_ORIGIN_HANDLING);
        Node indexLocationNode = d.selectSingleNode(INDEX_LOCATION_PATH);
        if (indexLocationNode != null) {
            // Divide by 1024 since Heritrix uses KB rather than bytes,
            // and add 1 to avoid to low limit due to rounding.
            indexLocationNode.setText(new File(TestInfo.HERITRIX_TEMP_DIR, "index").getAbsolutePath());
        } else {
            fail ("IndexLocation node not found in order.xml");
        }
        File modifiedOrderFile = mtf.newTmpFile();

        OutputStream os = new FileOutputStream(modifiedOrderFile);
        XMLWriter writer = new XMLWriter(os);
        writer.write(d);
        // Now the modified order.xml is in modifiedOrderFile
        
        File indexDir = mtf.newTmpDir();
        File scratchpadDir = mtf.newTmpDir();
  
        // Sort crawl-log:
        
        //File orgCrawlog = new File(TestInfo.HERITRIX_TEMP_DIR, "logs/crawl.log");
        File orgCrawlog = new File("tests/dk/netarkivet/externalsoftware/data/launcher/originals/netarkivet/testLaunchHarvest/logs/crawl.log");
        assertTrue("File does not exist", orgCrawlog.exists());
        Method sortCrawlLog = ReflectUtils.getPrivateMethod(CrawlLogIndexCache.class,
                "sortCrawlLog", File.class, File.class);
        File sortedCrawlLog = new File(scratchpadDir, "sorted-crawl.log");
        sortCrawlLog.invoke(null, orgCrawlog, sortedCrawlLog);
        
        //File arcsDir = new File(TestInfo.HERITRIX_TEMP_DIR, "arcs");
        File arcsDir = new File("tests/dk/netarkivet/externalsoftware/data/launcher/originals/netarkivet/testLaunchHarvest/arcs");
        
        // Get CDXReader of the cdx for the previous crawl.
        // Note that this may break if the arcs dir has more than one file.
        LuceneUtils.generateIndex(sortedCrawlLog,
                getCXDReaderForArc(arcsDir.listFiles(TestFileUtils.NON_CVS_DIRS_FILTER)[0]),
                indexDir);
        
        FileUtils.removeRecursively(TestInfo.HERITRIX_TEMP_DIR);
        runHeritrix(modifiedOrderFile, TestInfo.SEEDS_FILE, indexDir);
        FileAsserts.assertFileMatches("Must have done some dedup",
                "Duplicates found:  [^0]",
                new File(TestInfo.HERITRIX_TEMP_DIR, "processors-report.txt"));
    }

    private void validateOrder(File anOrderFile) {
        SAXReader reader = new SAXReader();
        reader.setValidation(true);
        try {
        reader.setProperty("http://apache.org/xml/properties/schema/external-noNamespaceSchemaLocation",
                //TestInfo.HERITRIX_SETTINGS_SCHEMA_FILE.getAbsolutePath());
                "heritrix_settings.xsd");
        reader.setFeature("http://apache.org/xml/features/validation/schema",true);
        // add error handler which turns any errors into XML
        XMLErrorHandler errorHandler = new XMLErrorHandler();
        reader.setErrorHandler( errorHandler );

       // parse the document
       Document document = reader.read(anOrderFile);
       if (!errorHandler.getErrors().asXML().contentEquals("<errors/>")) {
           fail (anOrderFile.getAbsolutePath() + "is invalid according to schema: "
                   + errorHandler.getErrors().asXML());
       }

       // Find alle classes in the order.xml, and try to load/instantiate these classes
       // TODO: Try to instantiate all classes in the given xml.
        iterateChildren(document.getRootElement());

        } catch (SAXException e) {
            fail ("SaxException thrown" + e);
        } catch (DocumentException e1) {
            fail ("DocumentException thrown" + e1);
        }
    }


    /**
     * Iterate over all children of a XML-element.
     * @param anElement
     */
    private void iterateChildren(Element anElement) {
        Iterator elementIterator = anElement.elementIterator();
        while(elementIterator.hasNext()){
            Element element = (Element) elementIterator.next();

            if (element.attribute("class") != null) {
                //System.out.println("This element (" + element.getName() +") contains a class-name");
                //System.out.println("Parent = " + element.getPath());
                if (!validClass(element.attribute("class").getText())) {
                    fail ("Class not valid: " + element.attribute("class").getText());
                }
            }
            iterateChildren(element);
        }
    }

    /** Check, if class exists, and can be loaded.
     * TODO: try to instantiate the class as well.
     * @param className a name for a class
     * @return true, if class exists, and can be loaded.XS
     */
    private boolean validClass(String className) {
        try {
            Class.forName(className);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private BufferedReader getCXDReaderForArc(File arcfile) throws Exception{
        File cdxfile = mtf.newTmpFile();
        OutputStream cdxstream = null;
        try {
            cdxstream = new FileOutputStream(cdxfile);
            ExtractCDX.writeCDXInfo(arcfile, cdxstream);
        } finally {
            if (cdxstream != null) {
                cdxstream.close();
            }
        }
        Method sortCDX = ReflectUtils.getPrivateMethod(CrawlLogIndexCache.class,
                "sortCDX", File.class, File.class);
        File readable = File.createTempFile("sorted", "cdx", TestInfo.WORKING_DIR);
        sortCDX.invoke(null, cdxfile, readable);
        BufferedReader cr = new BufferedReader(new FileReader(readable));
        return cr;
    }
    /* Utility method to facilitate and update an XML node
    given a set of legal values, and a default value.
    It logs a warning, if the given settingValue is illegal.
    */
   private void checkAndSetOrderXMLNode(Document doc, String xpath, String settingName,
                                        String settingValue, String[] legalValues, String defaultValue) {
       boolean settingOK = false;
       for (String possibleValue : legalValues) {
           if (settingValue.equals(possibleValue)) {
               settingOK = true;
           }
       }
       if (!settingOK) {
           System.out.println(
                   String.format(
                           "Unrecognized %s value given: %s. Replaced by default %s value: %s",
                           settingName, settingValue, defaultValue));
           settingValue = defaultValue;
       }
       setOrderXMLNode(doc, xpath, settingValue);
   }

   /**
    * Set a XmlNode defined by the given XPath to the given value.
    * @param doc the Document, which is being modified
    * @param xpath the given XPath
    * @param value the given value
    */
   private void setOrderXMLNode(Document doc, String xpath, String value) {
       Node xpath_node = doc.selectSingleNode(xpath);
       if (xpath_node == null) {
           throw new IOFailure("Element " + xpath
                   + "could not be found in this order-file!");
       }
       xpath_node.setText(value);
   }
}


