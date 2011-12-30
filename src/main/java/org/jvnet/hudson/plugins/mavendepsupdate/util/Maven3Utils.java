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

import hudson.PluginFirstClassLoader;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;

import java.net.URL;
import java.util.logging.Logger;

/**
 * @author Olivier Lamy
 * @since 1.1
 */
public class Maven3Utils
{
    private static final Logger LOGGER = Logger.getLogger( Maven3Utils.class.getName() );

    /**
     * will build a PlexusContainer from {@link PluginFirstClassLoader}
     *
     * @param pluginFirstClassLoader
     * @return
     * @throws PlexusContainerException
     */
    public static PlexusContainer getPlexusContainer( PluginFirstClassLoader pluginFirstClassLoader )
        throws PlexusContainerException
    {
        DefaultContainerConfiguration conf = new DefaultContainerConfiguration();
        ClassRealm classRealm = getClassLoader( pluginFirstClassLoader );
        conf.setRealm( classRealm );

        return new DefaultPlexusContainer( conf );
    }

    public static ClassRealm getClassLoader( PluginFirstClassLoader pluginFirstClassLoader )
    {
        ClassWorld world = new ClassWorld();
        ClassRealm classRealm = new ClassRealm( world, "project-building", pluginFirstClassLoader );
        // olamy yup hackish but it's needed for plexus-shim which a URLClassLoader and PluginFirstClassLoader is not
        for ( URL url : pluginFirstClassLoader.getURLs() )
        {
            classRealm.addURL( url );
            LOGGER.fine( "add url " + url.toExternalForm() );
        }
        return classRealm;
    }

}
