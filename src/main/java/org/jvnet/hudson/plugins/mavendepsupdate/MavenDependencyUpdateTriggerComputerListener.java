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
        /*
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
        */
    }

}
