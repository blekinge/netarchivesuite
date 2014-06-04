/* File:    $Id$
 * Revision: $Revision$
 * Author:   $Author$
 * Date:     $Date$
 *
 * The Netarchive Suite - Software to harvest and preserve websites
 * Copyright 2004-2012 The Royal Danish Library, the Danish State and
 * University Library, the National Library of France and the Austrian
 * National Library.
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
 * Foundation,dk.netarkivet.harvester.schedulerFloor, Boston, MA  02110-1301  USA
 */
package dk.netarkivet.harvester.scheduler;

import dk.netarkivet.harvester.datamodel.Job;
import dk.netarkivet.harvester.datamodel.JobDAO;
import dk.netarkivet.harvester.datamodel.JobStatus;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import javax.inject.Provider;
import junit.framework.TestCase;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class JobSupervisorTest extends TestCase {
    private JobSupervisor jobSupervisor;
    private JobDAO jobDaoMock = mock(JobDAO.class);
    private Provider<JobDAO> jobDAOProvider;

    public void setUp() {
        jobDAOProvider = new Provider<JobDAO>() {
            @Override
            public JobDAO get() {
                return jobDaoMock;
            }
        };
    }

    public void testCleanOldJobsMultipleJobs() {
        Long jobTimeoutTime = 1L;
        jobSupervisor = new JobSupervisor(jobDAOProvider, jobTimeoutTime);

        List<Long> jobIDs = Arrays.asList(1L, 2L, 3L);
        when(jobDaoMock.getAllJobIds(JobStatus.STARTED)).thenReturn(jobIDs.iterator());
        Job pastObsoleteJobMock = mock(Job.class);
        Job pastActiveMock = mock(Job.class);
        Job futureActiveMock = mock(Job.class);
        when(jobDaoMock.read(1L)).thenReturn(pastObsoleteJobMock);
        when(jobDaoMock.read(2L)).thenReturn(pastActiveMock);
        when(jobDaoMock.read(3L)).thenReturn(futureActiveMock);

        Date inTheObsoletePast = new Date(System.currentTimeMillis() - 10000);
        Date inTheActivePast = new Date(System.currentTimeMillis() - 1);
        Date inTheActiveFuture = new Date(System.currentTimeMillis() + 10000);

        when(pastObsoleteJobMock.getActualStart()).thenReturn(inTheObsoletePast);
        when(pastActiveMock.getActualStart()).thenReturn(inTheActivePast);
        when(futureActiveMock.getActualStart()).thenReturn(inTheActiveFuture);

        jobSupervisor.cleanOldJobs();

        verify(jobDaoMock).getAllJobIds(JobStatus.STARTED);

        verify(jobDaoMock).read(jobIDs.get(0));
        verify(jobDaoMock).read(jobIDs.get(1));
        verify(jobDaoMock).read(jobIDs.get(2));

        verify(pastObsoleteJobMock).getActualStart();
        verify(pastActiveMock).getActualStart();
        verify(futureActiveMock).getActualStart();
        verify(pastObsoleteJobMock).setStatus(JobStatus.FAILED);
        verify(pastObsoleteJobMock).appendHarvestErrors(any(String.class));
        verifyNoMoreInteractions(pastActiveMock, futureActiveMock);

        verify(jobDaoMock).update(pastObsoleteJobMock);
        verifyNoMoreInteractions(jobDaoMock);
    }

    public void testCleanOldJobsNoJobs() {
        Long jobTimeoutTime = 1L;
        jobSupervisor = new JobSupervisor(jobDAOProvider, jobTimeoutTime);

        List<Long> jobIDs = Arrays.asList(new Long[] {});
        when(jobDaoMock.getAllJobIds(JobStatus.STARTED)).thenReturn(jobIDs.iterator());

        jobSupervisor.cleanOldJobs();

        verify(jobDaoMock).getAllJobIds(JobStatus.STARTED);
        verifyNoMoreInteractions(jobDaoMock);
    }

    public void testRescheduleMultipleSubmittedJobs() {
        Long jobTimeoutTime = 1L;
        jobSupervisor = new JobSupervisor(jobDAOProvider, jobTimeoutTime);

        List<Long> jobIDs = Arrays.asList(1L, 3L);
        when(jobDaoMock.getAllJobIds(JobStatus.SUBMITTED)).thenReturn(jobIDs.iterator());

        jobSupervisor.rescheduleLeftOverJobs();

        verify(jobDaoMock).getAllJobIds(JobStatus.SUBMITTED);
        verify(jobDaoMock).rescheduleJob(jobIDs.get(0));
        verify(jobDaoMock).rescheduleJob(jobIDs.get(1));
        verifyNoMoreInteractions(jobDaoMock);
    }

    public void testRescheduleNoSubmittedJobs() {
        Long jobTimeoutTime = 1L;
        jobSupervisor = new JobSupervisor(jobDAOProvider, jobTimeoutTime);

        List<Long> jobIDs = Arrays.asList(new Long[] {});
        when(jobDaoMock.getAllJobIds(JobStatus.SUBMITTED)).thenReturn(jobIDs.iterator());

        jobSupervisor.rescheduleLeftOverJobs();

        verify(jobDaoMock).getAllJobIds(JobStatus.SUBMITTED);
        verifyNoMoreInteractions(jobDaoMock);
    }
}
