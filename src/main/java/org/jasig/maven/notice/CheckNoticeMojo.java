/**
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jasig.maven.notice;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.jasig.maven.notice.util.ResourceFinder;

import difflib.Chunk;
import difflib.DeleteDelta;
import difflib.Delta;
import difflib.DiffUtils;
import difflib.InsertDelta;
import difflib.Patch;
import edu.emory.mathcs.backport.java.util.LinkedList;

/**
 * Checks the NOTICE file to make sure it matches the expected output
 * 
 * @author Eric Dalquist
 * @version $Revision$
 * @goal check
 * @threadSafe true
 * @requiresDependencyCollection test
 */
public class CheckNoticeMojo extends AbstractNoticeMojo {
    
    @Override
    protected void handleNotice(Log logger, ResourceFinder finder, String noticeContents) throws MojoFailureException {
        //Write out the generated notice file
        final File outputFile = getNoticeOutputFile();

        //Make sure the existing NOTICE file exists
        if (!outputFile.exists()) {
            throw new MojoFailureException("No NOTICE file exists at: " + outputFile);
        }
        
        //Load up the existing NOTICE file
        final String existingNoticeContents;
        try {
            existingNoticeContents = FileUtils.readFileToString(outputFile, this.encoding);
        }
        catch (IOException e) {
            throw new MojoFailureException("Failed to read existing NOTICE File from: " + outputFile, e);
        }
        
        //Check if the notice files match
        if (!noticeContents.equals(existingNoticeContents)) {
            final String diffText = this.generateDiff(logger, noticeContents, existingNoticeContents);
                
            final String buildDir = project.getBuild().getDirectory();
            final File expectedNoticeFile = new File(new File(buildDir), "NOTICE.expected");
            try {
                FileUtils.writeStringToFile(expectedNoticeFile, noticeContents, this.encoding);
            }
            catch (IOException e) {
                logger.warn("Failed to write expected NOTICE File to: " + expectedNoticeFile, e);
            }
            
            final String msg = "Existing NOTICE file '" + outputFile + "' doesn't match expected NOTICE file: " + expectedNoticeFile;
            logger.error(msg + "\n" + diffText);
            throw new MojoFailureException(msg);
        }
        
        logger.info("NOTICE file is up to date");
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected String generateDiff(Log logger, String noticeContents, final String existingNoticeContents) {
        final StringBuilder diffText = new StringBuilder();
        try {
            final List<?> expectedLines = IOUtils.readLines(new StringReader(noticeContents));
            final List<?> existingLines = IOUtils.readLines(new StringReader(existingNoticeContents));
            final Patch diff = DiffUtils.diff(expectedLines, existingLines);
            
            for (final Delta delta : diff.getDeltas()) {
                final Chunk original = delta.getOriginal();
                final Chunk revised = delta.getRevised();
                
                char changeType = '?';
                char changeDirection = '?'; 
                List lines;
                if (delta instanceof DeleteDelta) {
                    changeType = 'd';
                    changeDirection = '<';
                    lines = original.getLines();
                }
                else if (delta instanceof InsertDelta) {
                    changeType = 'a';
                    changeDirection = '>';
                    lines = revised.getLines();
                }
                else {
                    lines = new LinkedList();
                    lines.addAll(original.getLines());
                    lines.addAll(revised.getLines());
                }
                
                diffText.append(original.getPosition()).append(changeType).append(revised.getPosition()).append("\n");
                for (final Object line : lines) {
                    diffText.append(changeDirection).append(" ").append(line).append("\n");
                }
            }
        }
        catch (IOException e) {
            logger.warn("Failed to generate diff between existing and expected NOTICE files", e);
        }
        return diffText.toString();
    }
}