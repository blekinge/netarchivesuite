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
package dk.netarkivet.harvester.datamodel;

import java.util.Date;

import dk.netarkivet.common.exceptions.PermissionDenied;

/**
 * Unit-tests for the ScheduleDBDAO class.
 */
public class ScheduleDBDAOTester extends DataModelTestCase {
    private static final String THIRTY_CHAR_STRING = "123456789012345678901234567890";

    public ScheduleDBDAOTester(String s) {
        super(s);
    }

    public void setUp() throws Exception {
        super.setUp();
    }

    public void tearDown() throws Exception {
        super.tearDown();
    }

    public void testCreateChecksSize() throws Exception {
        Schedule s1 = TestInfo.getDefaultSchedule();
        ScheduleDAO dao = ScheduleDAO.getInstance();
        StringBuilder build = new StringBuilder(3030);
        for (int i = 0; i < 101; i++) {
            build.append(THIRTY_CHAR_STRING);
        }
        s1.setComments(build.toString());
        try {
            dao.update(s1);
            fail("Should throw PermissionDenied on comment of length "
                    + s1.getName().length());
        } catch (PermissionDenied e) {
            // expected
        }
        build = new StringBuilder(330);
        for (int i = 0; i < 11; i++) {
            build.append(THIRTY_CHAR_STRING);
        }
        Schedule s2 = new RepeatingSchedule(new Date(), 2, new HourlyFrequency(2),
                build.toString(), "Small comment");
        try {
            dao.create(s2);
            fail("Should throw PermissionDenied on name of length "
                    + s2.getName().length());
        } catch (PermissionDenied e) {
            //Expected
        }
    }
}
