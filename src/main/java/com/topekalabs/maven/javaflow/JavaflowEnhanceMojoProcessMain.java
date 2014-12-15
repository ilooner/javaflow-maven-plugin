/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.topekalabs.maven.javaflow;

import java.io.File;
import java.util.List;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

/**
 * @author Topeka Labs
 * 
 * @goal instrument-main
 * 
 * @phase process-classes
 *
 * @requiresDependencyResolution compile
 */
public class JavaflowEnhanceMojoProcessMain extends JavaflowEnhanceMojo
{
    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;
    
    /**
     * backupDirectory
     *
     * @parameter default-value="${project.build.directory}/javaflow/main"
     */
    private File backupDirectory;
    
    /**
     * classNames
     * 
     * @parameter
     */
    private List<String> classNames;
    
    public JavaflowEnhanceMojoProcessMain()
    {
        //Do nothing
    }
    
    @Override
    protected List<String> getClassNames()
    {
        return classNames;
    }

    @Override
    protected File getBackupDirectory()
    {
        return backupDirectory;
    }

    @Override
    protected File getOutputDirectory()
    {
        return new File(project.getBuild().getOutputDirectory());
    }

    @Override
    protected String getMessage()
    {
        return "main classes";
    }

    @Override
    protected List<String> getClasspathElements() throws MojoExecutionException
    {
        try
        {
            return project.getCompileClasspathElements();
        }
        catch(DependencyResolutionRequiredException ex)
        {
            throw new MojoExecutionException(ex.getMessage());
        }
    }
}
