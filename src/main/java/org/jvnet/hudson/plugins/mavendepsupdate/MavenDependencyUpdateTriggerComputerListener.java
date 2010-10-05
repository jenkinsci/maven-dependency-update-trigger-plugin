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

import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.remoting.Channel;
import hudson.slaves.ComputerListener;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

/**
 * @author Olivier Lamy
 * @since 1.1
 */
@Extension
public class MavenDependencyUpdateTriggerComputerListener extends ComputerListener
{
    public static final String MAVEN_SHADED_JAR_NAME = "maven-dependency-update-trigger-shaded-maven";

    @Override
    public void preOnline( Computer c, Channel channel, FilePath root, TaskListener listener )
        throws IOException, InterruptedException
    {
        PrintStream logger = listener.getLogger();
        logger.println( "copy jars for MavenDependencyUpdateTrigger" );
        File mavenShadedJar = new File( Hudson.getInstance().getRootDir(),
                                        "plugins/maven-dependency-update-trigger/"+MAVEN_SHADED_JAR_NAME+".jar" );
        if (mavenShadedJar.exists())
        {
            logger.println("mavenShadedJar path " + mavenShadedJar.getPath());
            new FilePath(mavenShadedJar).copyTo(root.child(mavenShadedJar.getName()));
            logger.println("Copied "+mavenShadedJar.getName());
        }
        else
        {
            logger.println("mavenShadedJar not found ");
        }
    }

}
