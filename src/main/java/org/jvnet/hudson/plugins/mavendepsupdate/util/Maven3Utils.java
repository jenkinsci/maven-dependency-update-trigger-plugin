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

import hudson.PluginFirstClassLoader;

import java.net.URL;
import java.util.logging.Logger;

import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;

/**
 * @author Olivier Lamy
 * @since 1.1
 */
public class Maven3Utils
{
    private static final Logger LOGGER = Logger.getLogger( Maven3Utils.class.getName() );
    
    /**
     * will build a PlexusContainer from {@link PluginFirstClassLoader}
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
