/* $Id$
 * $Date$
 * $Revision$
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
package dk.netarkivet.harvester.sidekick;

import dk.netarkivet.common.exceptions.NotImplementedException;

/**
 * Default implementation of the MonitorHook interface.
 */
public abstract class DefaultMonitorHook implements MonitorHook {

    /**
     * Does nothing.
     *
     * TODO: Register the object in RMI registry
     */
    public DefaultMonitorHook() {
        super();
    }

    /**
     * Returns true if the application is running.
     *
     * @return true if the application is running
     */
    public abstract boolean isRunning();

    /**
     * Not implemented!
     */
    public long getMemory() {
        throw new NotImplementedException("Not implemented!");
    }
}
