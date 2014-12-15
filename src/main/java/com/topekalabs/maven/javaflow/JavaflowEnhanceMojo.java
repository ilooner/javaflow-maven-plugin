/*
 * JavaflowEnhanceMojo.java
 * 
 * Copyright 2013 Stephen J. Scheck
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package com.topekalabs.maven.javaflow;

import com.google.common.collect.Lists;
import com.topekalabs.java.utils.ClassUtils;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.javaflow.bytecode.transformation.ResourceTransformer;
import org.apache.commons.javaflow.bytecode.transformation.asm.AsmClassTransformer;
import org.apache.commons.javaflow.utils.RewritingUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

/**
 * 
 */
public abstract class JavaflowEnhanceMojo extends AbstractMojo
{
    public static final String CLASSFILE_REWRITE_TEMPLATE = "%s.JAVAFLOW_INSTRUMENTED";

    protected abstract List<String> getClassNames();
    protected abstract File getBackupDirectory();
    protected abstract File getOutputDirectory();
    protected abstract String getMessage();
    protected abstract List<String> getClasspathElements() throws MojoExecutionException;
    
    @Override
    public void execute() throws MojoExecutionException
    {
        prepareClasspath();
        
        File baseDirectory = getOutputDirectory();
        File backupDirectory = getBackupDirectory();
        List<String> classNames = getClassNames();
        String message = getMessage();
        
        if(classNames == null ||
           classNames.isEmpty())
        {
            getLog().info("No " + message + " to instrument");
            return;
        }
        
        if(!baseDirectory.exists())
        {
            getLog().error("The " + message + " have not been compiled yet.");
            return;
        }
        
        getLog().info("Preparing to instrument " +
                      classNames.size() + " " +
                      message);
        
        List<File> classFiles = Lists.newArrayList();
        
        for(String className: classNames)
        {
            getLog().info("Preparing to scan for: " + className);
            if(ClassUtils.isClassName(className))
            {
                getLog().info("This is a class: " + className);
                Collection<File> tempClassFiles = ClassUtils.getClassAndInnerClassFiles(baseDirectory,
                                                                                        className);
                classFiles.addAll(tempClassFiles);
            }
            else if(ClassUtils.isFQClassName(className))
            {
                getLog().info("This is a fq class: " + className);
                Collection<File> tempClassFiles = ClassUtils.getFQClassAndInnerClassFiles(baseDirectory,
                                                                                          className);
                classFiles.addAll(tempClassFiles);
            }
            else
            {
                throw new MojoExecutionException("This given class name " +
                                                 className +
                                                 " is not valid");
            }
        }
        
        getLog().info("Instrumenting " + classFiles.size() + " class files.");
        
        instrumentClassFiles(baseDirectory,
                             backupDirectory,
                             classFiles);
    }
    
    private void instrumentClassFiles(File baseDirectory,
                                      File backupDirectory,
                                      List<File> classFiles) throws MojoExecutionException
    {
        Log log = getLog();
        ResourceTransformer transformer = new AsmClassTransformer();

        for(File classFile: classFiles)
        {
            try
            {
                File instrumentedClassFile = new File(String.format(CLASSFILE_REWRITE_TEMPLATE,
                                                                    classFile));
                File backupClassFile = com.topekalabs.file.utils.FileUtils.rebase(baseDirectory,
                                                                                  backupDirectory,
                                                                                  classFile);

                if(backupClassFile.exists() && (classFile.lastModified() <= backupClassFile.lastModified()))
                {
                    log.info(classFile + " is up to date");
                    continue;
                }

                log.info(String.format("Enhancing class file bytecode for Javaflow: %s",
                                        classFile));
                RewritingUtils.rewriteClassFile(classFile,
                                                transformer,
                                                instrumentedClassFile);

                if(backupClassFile.exists())
                {
                    log.debug(String.format("Backup for original class file %s already exists - removing it",
                                            backupClassFile));
                    backupClassFile.delete();
                }

                log.info(String.format("Renaming original class file from %s to %s",
                                        classFile,
                                        backupClassFile));
                FileUtils.moveFile(classFile,
                                   backupClassFile);

                log.info(String.format("Renaming rewritten class file from %s to %s",
                                        instrumentedClassFile,
                                        classFile));
                
                FileUtils.moveFile(instrumentedClassFile, classFile);
                backupClassFile.setLastModified(classFile.lastModified());

            }
            catch(IOException e)
            {
                throw new MojoExecutionException(e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void prepareClasspath() throws MojoExecutionException
    {
        List<String> runtimeClasspathElements = null;
        URLClassLoader classLoader = null;

        try
        {
            runtimeClasspathElements = getClasspathElements();
            URL[] runtimeUrls = new URL[runtimeClasspathElements.size()];

            getLog().debug("Number of runtime classpath elements " +
                           runtimeClasspathElements.size());
            
            for(int ii = 0; ii < runtimeClasspathElements.size(); ii++)
            {
                String element = runtimeClasspathElements.get(ii);
                getLog().debug("runtime classpath element " + element);
                File elementFile = new File(element);
                runtimeUrls[ii] = elementFile.toURI().toURL();
            }

            classLoader = new URLClassLoader(runtimeUrls, Thread.currentThread().getContextClassLoader());
            Thread.currentThread().setContextClassLoader(classLoader);

        }
        catch(MalformedURLException e)
        {
            throw new MojoExecutionException(e.getMessage());
        }
    }
}
