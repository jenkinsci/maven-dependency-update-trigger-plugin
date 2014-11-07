/*
 * Copyright (c) 2011, Olivier Lamy, Talend
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jvnet.hudson.plugins.mavendepsupdate.util;

import org.apache.commons.lang.StringUtils;
import org.jvnet.hudson.plugins.mavendepsupdate.MavenDependencyUpdateTrigger;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * NOTE : <b>this class is not designed for external use so it can change without any prior notice</b>
 *
 * @author Olivier Lamy
 * @since 1.1
 */
public class SnapshotTransfertListener
    implements TransferListener, Serializable
{
    private static final Logger LOGGER = Logger.getLogger( SnapshotTransfertListener.class.getName() );

    private boolean snapshotDownloaded = false;

    private List<String> snapshots = new ArrayList<String>();

    public void transferCorrupted( TransferEvent transferEvent )
        throws TransferCancelledException
    {
        // no op
    }

    public void transferFailed( TransferEvent transferEvent )
    {
        // no op       
    }

    public void transferInitiated( TransferEvent transferEvent )
        throws TransferCancelledException
    {
        // no op
    }

    public void transferProgressed( TransferEvent transferEvent )
        throws TransferCancelledException
    {
        // no op
    }

    public void transferStarted( TransferEvent transferEvent )
        throws TransferCancelledException
    {
        // no op
    }

    public void transferSucceeded( TransferEvent transferEvent )
    {
        if ( transferEvent != null && transferEvent.getResource() != null )
        {
            File file = transferEvent.getResource().getFile();
            if ( file != null && transferEvent.getResource().getResourceName().contains( "SNAPSHOT" ) )
            {
                // filtering on maven metadata
                boolean isArtifact =
                    !StringUtils.contains( file.getName(), "maven-metadata" ) && !StringUtils.endsWith( file.getName(),
                                                                                                        ".xml" );
                if ( isArtifact )
                {
                    if ( MavenDependencyUpdateTrigger.debug )
                    {
                        LOGGER.info( "download " + file.getName() );
                    }
                    snapshots.add( file.getName() );
                    snapshotDownloaded = true;
                }
                else
                {
                    if ( MavenDependencyUpdateTrigger.debug )
                    {
                        LOGGER.info( "ignore file " + file.getName() );
                    }
                }
            }
        }
    }

    public boolean isSnapshotDownloaded()
    {
        return snapshotDownloaded;
    }

    public List<String> getSnapshots()
    {
        return snapshots;
    }
}
