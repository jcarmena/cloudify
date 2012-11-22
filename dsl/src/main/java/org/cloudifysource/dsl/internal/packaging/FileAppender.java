package org.cloudifysource.dsl.internal.packaging;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;

import org.apache.commons.io.FileUtils;

/**
 * 
 * @author elip
 *
 */
public class FileAppender {
	
	private static final String LINE_SEPARATOR = System.getProperty("line.separator");
	
	private FileWriter writer;
	
	public FileAppender(final File newFile) throws IOException {
		if (newFile.exists()) {
			String fileContents = FileUtils.readFileToString(newFile);
			writer = new FileWriter(newFile);
			writer.append(fileContents);
			writer.append(LINE_SEPARATOR);
		} else {
			writer = new FileWriter(newFile);
		}
		
	}
	
	/**
	 * @param comment - this comment will be appended to the beginning of the file to merge.
	 * @param fileToMerge - the file to merge with
	 * @return .
	 * @throws IOException 
	 */
	public FileAppender append(final String comment, final File fileToMerge) throws IOException {
		writer.append("//" + comment + " Generated By " + FileAppender.class.getName() 
				+ " On : " + new Date(System.currentTimeMillis()));
		writer.append(LINE_SEPARATOR);
		writer.append(LINE_SEPARATOR);
		writer.append(FileUtils.readFileToString(fileToMerge));
		writer.append(LINE_SEPARATOR);
		writer.append(LINE_SEPARATOR);
		return this;
	}
	
	/**
	 * 
	 * @throws IOException .
	 */
	public void flush() throws IOException {
		writer.flush();
		writer.close();
	}
}
