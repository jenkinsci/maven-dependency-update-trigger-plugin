/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc. Olivier Lamy
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jvnet.hudson.plugins.mavendepsupdate.util;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.sonatype.aether.transfer.TransferCancelledException;
import org.sonatype.aether.transfer.TransferEvent;
import org.sonatype.aether.transfer.TransferListener;

/**
 * NOTE : <b>this class is not designed for external use so it can change without any prior notice</b>
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
                if ( file.getName().endsWith( ".jar" ) || file.getName().endsWith( ".war" ) )
                {
                    LOGGER.info( "download " + file.getName() );
                    snapshots.add( file.getName() );
                    snapshotDownloaded = true;
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
