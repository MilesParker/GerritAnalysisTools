package name.milesparker.gerrit.analysis;

/*******************************************************************************
 * Copyright (c) 2012 Tasktop Technologies and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Miles Parker - initial API and implementation
 *******************************************************************************/

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;

import name.milesparker.gerrit.analysis.SimpleFileConverter.Replacement;

import org.apache.commons.lang.StringUtils;

public class CollectGit {

	public static final String DEFAULT_FORMAT = "†%cn\\t%ci\\t%ai\\t%nç%s%bç%n";

	static Replacement[] REPLACEMENTS = new Replacement[] {
			new Replacement("\\\\t", "\t"),
			new Replacement(
					"(\\d*) files? changed, (\\d*) insertions?\\([\\+\\-]\\), (\\d*) deletions?\\([\\+\\-]\\)",
					"$1\t$2\t$3"),
			new Replacement(
					"(\\d*) files? changed, (\\d*) insertions?\\([\\+\\-]\\)",
					"$1\t$2\t0"),
			new Replacement(
					"(\\d*) files? changed, (\\d*) deletions?\\([\\+\\-]\\)",
					"$1\t0\t$2"),
			new Replacement("0 files changed", "0\t0\t0"),
			new Replacement("\\s\\d\\d:\\d\\d:\\d\\d\\s\\+\\d\\d\\d\\d", ""),
			new Replacement("\\s\\d\\d:\\d\\d:\\d\\d\\s\\-\\d\\d\\d\\d", ""),
			new Replacement("\\n\\s", "\t", true),
			new Replacement("\\A\\n", "", true),
			new Replacement("\\n", "", true), new Replacement("†", "\n", true),
			new Replacement("ç(.*Change-Id:.*)ç", "true", true),
			new Replacement("çç", "true", true),
			new Replacement("ç(.*)ç", "false", true),
			new Replacement("e\\t\\n", "e\t0\t0\t0\n", true),
			new Replacement("\\t\\n", "\n", true),
			new Replacement("\\A\\n", "", true),
			new Replacement("e\\Z", "e\t0\t0\t0", true),
			new Replacement("\\Z", "\n", true) };

	static Replacement[] CLEAN_UP = new Replacement[] { new Replacement(
			"e\\t\\n", "0\t0\t0\n"), };

	public static void main(String[] args) {
		String gitDirectory = args[0];
		String dataDirectory = args[1];
		String[] projects = StringUtils.split(args[2], ",");
		deleteContents(dataDirectory);
		collectStats(gitDirectory, dataDirectory, projects);
		formatFiles(dataDirectory);
		cocatenate(dataDirectory, projects);
	}

	protected static void deleteContents(String dataDirectory) {
		File dir = new File(dataDirectory);
		for (File file : dir.listFiles()) {
			file.delete();
		}
	}

	protected static void formatFiles(String rootDir) {
		System.out.println("Converting: " + rootDir);
		SimpleFileConverter converter = new SimpleFileConverter(rootDir, "txt",
				new String[] { "git" }, REPLACEMENTS);
		converter.setLogToConsole(false);
		converter.setUser(true);
		converter.schedule();
		while (converter.getResult() == null) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		System.err.println(converter.getResult().getMessage());
	}

	protected static void collectStats(String gitDirectory,
			String dataDirectory, String[] projects) {
		for (String project : projects) {
			collectStats(gitDirectory, dataDirectory, project);
		}
	}

	protected static void collectStats(String gitDirectory,
			String dataDirectory, String project) {
		System.out.println("Reading: " + project);
		String command = "/usr/local/git/bin/git";
		command += " log";
		command += " --pretty=format:" + DEFAULT_FORMAT;
		command += " --shortstat";
		command += " --branches=master";
		execOuput(command, gitDirectory + File.separator + project,
				dataDirectory + File.separator + project + ".txt");
	}

	protected static void execOuput(String command, String directory,
			String outputFile) {
		try {
			Process process = new ProcessBuilder(StringUtils.split(command))
					.directory(new File(directory))
					.redirectError(Redirect.INHERIT)
					.redirectOutput(new File(outputFile)).start();
			process.waitFor();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	protected static void cocatenate(String dataDirectory, String[] projects) {
		System.out.println("Cocatentating: " + dataDirectory);
		String command = "/bin/cat ";
		command += StringUtils.join(projects, ".txt ") + ".txt";
		execOuput(command, dataDirectory, dataDirectory + File.separator
				+ "all.txt");
	}
}
