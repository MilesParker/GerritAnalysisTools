package name.milesparker.gerrit.analysis;

/**
 * Copyright (c) 2010, 2012 Ericsson
 * Copyright (c) 2012 Tasktop Technologies and others.
 *  
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Description:
 * 
 * Contributors:
 * Miles Parker  - Initial implementation
 * 
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

/**
 * @author Miles Parker
 */
public class SimpleFileConverter extends Job {

	BufferedWriter logWriter;

	int filesConverted;

	int linesConverted;

	private final String extension;

	private Pattern[] patterns;

	private final Replacement[] replacements;

	private String rootPath;

	private final String[] ignoreExtension;

	private IProgressMonitor monitor;

	private boolean logToConsole = true;

	public static class Replacement {
		String match;

		String replace;

		private final boolean fileScope;

		public Replacement(String match, String replace, boolean fileScope) {
			super();
			this.match = match;
			this.replace = replace;
			this.fileScope = fileScope;
		}

		public Replacement(String match, String replace) {
			this(match, replace, false);
		}
	}

	public SimpleFileConverter(String rootPath, String extension,
			String[] ignoreExtension, Replacement[] replacements) {
		super("Convert " + extension + " files.");
		this.rootPath = rootPath;
		this.extension = extension;
		this.ignoreExtension = ignoreExtension;
		this.replacements = replacements;
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		this.monitor = monitor;
		patterns = new Pattern[replacements.length];
		for (int i = 0; i < patterns.length; i++) {
			patterns[i] = Pattern.compile(replacements[i].match);
		}
		File file = new File(rootPath);
		monitor.beginTask("Converting Files", count(file));
		File logFile = new File(rootPath + File.separator + "conversion.log");
		try {
			logFile.createNewFile();
			logWriter = new BufferedWriter(new FileWriter(logFile));
			log("Conversion Log: " + logFile.getAbsolutePath() + "\n\n");
			log("Root Folder: " + rootPath);
			convert(file);
			logWriter.close();
		} catch (IOException e) {
			return new Status(IStatus.ERROR, Activator.PLUGIN_ID,
					"Couldn't convert model at URI: " + file, e);
		}
		return Status.OK_STATUS;
	}

	protected void convert(File file) throws FileNotFoundException, IOException {
		Path path = new Path(file.getAbsolutePath());
		if (!file.isDirectory() && path.getFileExtension() == null) {
			return;
		}
		for (String ext : ignoreExtension) {
			if (StringUtils.equals(ext, path.getFileExtension())) {
				return;
			}
		}
		if (file.exists()) {
			if (file.isDirectory()) {
				for (File member : file.listFiles()) {
					convert(member);
				}
			} else {
				if (path.getFileExtension() == null) {
					return;
				}
				if (path.getFileExtension().equals(extension)) {
					log("    " + file.getAbsolutePath());
					filesConverted++;
					BufferedReader br = new BufferedReader(new FileReader(file));
					StringBuilder fileContents = new StringBuilder(8000);
					int lineNum = 0;
					while (br.ready()) {
						String line = br.readLine();
						String convert = convertLine(line);
						fileContents.append(convert + "\n");
						if (!line.equals(convert)) {
							String lineNumString = StringUtils.leftPad(lineNum
									+ "", 5);
							log("      " + lineNumString + ":  " + line
									+ "\n              " + convert);
						}
						lineNum++;
					}
					br.close();
					BufferedWriter writer = new BufferedWriter(new FileWriter(
							file));
					String convertString = convertFile(fileContents.toString());
					writer.write(convertString);
					writer.close();
				}
			}
		}
		monitor.worked(1);
	}

	protected int count(File file) {
		int count = 0;
		Path path = new Path(file.getAbsolutePath());
		if (!file.isDirectory() && path.getFileExtension() == null) {
			return 0;
		}
		for (String ext : ignoreExtension) {
			if (StringUtils.equals(ext, path.getFileExtension())) {
				return 0;
			}
		}
		if (file.exists()) {
			count++;
			if (file.isDirectory()) {
				for (File member : file.listFiles()) {
					count += count(member);
				}
			}
		}
		return count;
	}

	protected String convertLine(String line) {
		String result = line;
		for (int i = 0; i < patterns.length; i++) {
			if (!replacements[i].fileScope) {
				Matcher matcher = patterns[i].matcher(result);
				result = matcher.replaceAll(replacements[i].replace);
			}
		}
		return result;
	}

	protected String convertFile(String contents) {
		String result = contents;
		for (int i = 0; i < patterns.length; i++) {
			if (replacements[i].fileScope) {
				Matcher matcher = patterns[i].matcher(result);
				result = matcher.replaceAll(replacements[i].replace);
			}
		}
		return result;
	}

	private void log(String item) {
		if (logToConsole) {
			System.out.println(item);
		}
		try {
			logWriter.write(item + "\n");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void setLogToConsole(boolean logToConsole) {
		this.logToConsole = logToConsole;
	}
}
