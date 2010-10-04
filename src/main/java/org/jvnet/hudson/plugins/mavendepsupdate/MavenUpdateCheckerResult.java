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
package org.jvnet.hudson.plugins.mavendepsupdate;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Olivier Lamy
 * @since 1.1
 */
public class MavenUpdateCheckerResult implements Serializable
{

    private List<String> fileUpdatedNames = new ArrayList<String>();
    
    private List<String> debugLines = new ArrayList<String>();
    
    public MavenUpdateCheckerResult ()
    {
        // no op
    }

    public List<String> getFileUpdatedNames()
    {
        return fileUpdatedNames;
    }

    public void addFileUpdatedName( String fileUpdatedName )
    {
        this.fileUpdatedNames.add( fileUpdatedName );
    }
    
    public void addFilesUpdatedNames( List<String> filesUpdatedNames )
    {
        this.fileUpdatedNames.addAll( filesUpdatedNames );
    }    

    public List<String> getDebugLines()
    {
        return debugLines;
    }
  
    public void addDebugLine( String debugLine )
    {
        this.debugLines.add( debugLine );
    }
}
