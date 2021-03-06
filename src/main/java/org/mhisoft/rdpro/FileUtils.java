/*
 *
 *  * Copyright (c) 2014- MHISoft LLC and/or its affiliates. All rights reserved.
 *  * Licensed to MHISoft LLC under one or more contributor
 *  * license agreements. See the NOTICE file distributed with
 *  * this work for additional information regarding copyright
 *  * ownership. MHISoft LLC licenses this file to you under
 *  * the Apache License, Version 2.0 (the "License"); you may
 *  * not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *    http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing,
 *  * software distributed under the License is distributed on an
 *  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  * KIND, either express or implied.  See the License for the
 *  * specific language governing permissions and limitations
 *  * under the License.
 *
 */

package org.mhisoft.rdpro;

import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;

import org.mhisoft.rdpro.ui.RdProUI;

/**
 * Description:
 *
 * @author Tony Xue
 * @since Nov, 2014
 */
public class FileUtils {


	public static boolean isDirectoryEmpty(RdProUI ui, final String sDir) {
		try {
			return !Files.list(Paths.get(sDir)).
					findAny().
					isPresent();
		} catch (IOException e) {

			ui.println("\t[error]:" + e.getMessage());
			return false;
		}
	}

	public static void removeDir(File dir, RdProUI ui, FileRemoveStatistics frs, final RdProRunTimeProperties props) {
		try {
			if (dir.exists()) {
				if (props.isDryRun())
					ui.println("\t Remove dir(dry run only):" + dir.getAbsolutePath());
				else {
					//if still exist delete.
					if (!dir.delete()) {
						if (dir.exists())
							ui.println("\t[warn]Can't remove directory:" + dir.getAbsolutePath() + ". May be locked. ");
					} else {
						ui.println("\tRemoved dir:" + dir.getAbsolutePath());
						frs.dirRemoved++;
						ui.reportStatus(frs);
					}
				}
			}
		} catch (Exception e) {
			ui.println("\t[error]:" + e.getMessage());
		}
	}


	private static void copyFileUsingFileChannels(File source, File dest)
			throws IOException {
		FileChannel inputChannel = null;
		FileChannel outputChannel = null;
		try {
			inputChannel = new FileInputStream(source).getChannel();
			outputChannel = new FileOutputStream(dest).getChannel();
			outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
		} finally {
			inputChannel.close();
			outputChannel.close();
		}
	}

	private static final int BUFFER = 8192;

	private static void nioBufferCopy(File source, File target) {
		FileChannel in = null;
		FileChannel out = null;

		try {
			in = new FileInputStream(source).getChannel();
			out = new FileOutputStream(target).getChannel();

			ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER);
			while (in.read(buffer) != -1) {
				buffer.flip();

				while (buffer.hasRemaining()) {
					out.write(buffer);
				}

				buffer.clear();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			close(in);
			close(out);
		}
	}

	private static void close(Closeable closable) {
		if (closable != null) {
			try {
				closable.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static boolean isDirMatchTarget(final String pathName, final String targetDir) {
		return targetDir == null || targetDir.equals(pathName);
	}

	/**
	 * Split the string into array
	 *
	 * @param str       The original string
	 * @param delimters The delimiters
	 * @return
	 */
	public static String[] split(final String str, final char... delimters) {
		String[] tokens = null;
		if (str != null && str.trim().length() > 0) {

			String regExp = "";
			for (char deliter : delimters) {
				regExp += deliter;
			}
			tokens = str.split("[" + regExp + "]+");
		}
		return tokens;
	}

	private static ConcurrentHashMap<String, Pattern> convertedRegExPatterns = new ConcurrentHashMap<>();


	//new String[]{"_*.repositories", "*.pom", "*-b1605.0.1*", "*-b1605.0.1", "mobile*", "*"};

	/**
	 * Convert user friendly match pattern to regular expressions.
	 */
	public static Pattern getConvertToRegularExpression(final String targetPattern) {
		if (convertedRegExPatterns.get(targetPattern) != null) {
			return convertedRegExPatterns.get(targetPattern);
		} else {
			String regex = targetPattern.replace(".", "\\.");
			regex = regex.replace("?", ".?").replace("*", ".*");

			Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);

			convertedRegExPatterns.put(targetPattern, p);
			return p;
		}
	}


	public static boolean isFileMatchTargetFilePattern(final File f, final String targetPattern) {
		if (targetPattern == null)
			return true; //nothing to match
		// f.getName().matches(getConvertToRegularExpression(targetPattern));
		Matcher m = getConvertToRegularExpression(targetPattern).matcher(f.getName());
		/*
		//matches() return true if the whole string matches the given pattern.
		// find() tries to find a substring that matches the pattern.
		*/
		return m.matches();

	}

	/**
	 * Return true as long as one file pattern matches.
	 * it checks nulls on targetPatterns. If nothing matches, return true.
	 *
	 * @param f
	 * @param targetPatterns
	 * @return
	 */
	public static boolean isFileMatchTargetFilePatterns(final File f, final String[] targetPatterns) {
		if (targetPatterns == null)
			return true; //nothing to match
		for (String targetPattern : targetPatterns) {
			boolean b = isFileMatchTargetFilePattern(f, targetPattern);
			if (b)
				return true;
		}

		return false;
	}

	static final String REMOVE_LINK_WIN_TEMPLATE = "C:/bin/rdpro/tools/linkd.exe %s /D";
	static String REMOVE_LINK_MAC_TEMPLATE = System.getProperty("user.home") + "/bin/rdpro/tools/hunlink %s";

	//cache it for performance.
	static String commandTemplate = null;

	public static String getRemoveHardLinkCommandTemplate() throws IOException {
		if (commandTemplate != null)
			return commandTemplate;

		//read rdpro.properties in the user home's folder?
		String homeDir = System.getProperty("user.home");
		Properties config = new Properties();
		InputStream input = null;

		try {

			input = new FileInputStream(homeDir + "/rdpro.properties");
			// load a properties file
			config.load(input);

		} catch (IOException ex) {
			//
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					//e.printStackTrace();
				}
			}
		}



		if (OSDetectUtils.getOS() == OSDetectUtils.OSType.MAC) {

			String pathToLinkd = (config.getProperty("pathToUnlinkDirExecutable") == null ? REMOVE_LINK_MAC_TEMPLATE
					: config.getProperty("pathToUnlinkDirExecutable"));
			//todo system provided ln ,unlink can't be tested this way.

//			if (!new File(pathToLinkd).exists())
//				throw new IOException("pathToUnlinkDirExecutable is not valid, make sure the linkd.exe exists in the path specified:" + pathToLinkd);

			commandTemplate = pathToLinkd;


		} else if (OSDetectUtils.getOS() == OSDetectUtils.OSType.WINDOWS) {
			String pathToLinkd = (config.getProperty("pathToUnlinkDirExecutable") == null ? REMOVE_LINK_WIN_TEMPLATE
					: config.getProperty("pathToUnlinkDirExecutable"));
			File f = new File(pathToLinkd);
//			if ( !f.exists())
//				throw new IOException("pathToUnlinkDirExecutable is not valid, make sure the linkd.exe exists in the path specified:" + pathToLinkd);

			commandTemplate = pathToLinkd;

		} else {
			throw new IOException("OS not supported:" + OSDetectUtils.getOS());
		}
		return commandTemplate;
	}


	/**
	 * Return true if the direcotry does not exist after unlink. ie.e. we can assume it has been unlinked.
	 */

	public static class UnLinkResp {
		public boolean unlinked;
		public String commandOutput;

		@Override
		public String toString() {
			return "UnLinkResp{" +
					"unlinked=" + unlinked +
					", commandOutput='" + commandOutput + '\'' +
					'}';
		}
	}


	/**
	 * Remove the symbolic link on all OS.
	 * @param dir
	 * @return
	 * @throws IOException
	 */
	public static UnLinkResp removeSymbolicLink(final String dir) throws IOException {
		UnLinkResp ret = new UnLinkResp();
		//use rm to remove the symbolic link
		Files.delete(Paths.get(dir));
		ret.unlinked = !new File(dir).exists();
		return ret;
	}


	/**
	 * Use the linkd tool to remove the junction on windows.
	 * @param dir
	 * @return
	 * @throws IOException
	 */
	public static UnLinkResp removeWindowsJunction(final String dir) throws IOException {
		UnLinkResp ret = new UnLinkResp();
		String command = getRemoveHardLinkCommandTemplate();
		command = String.format(command, dir);
		ret.commandOutput = executeCommand(command);
		ret.unlinked = ret.commandOutput.contains("The delete operation succeeded");
		return ret;
	}


	public static UnLinkResp removeMacHardLink(final String dir) throws IOException {
		UnLinkResp ret = new UnLinkResp();
		String command = getRemoveHardLinkCommandTemplate();
		command = String.format(command, dir);
		ret.commandOutput = executeCommand(command);
		ret.unlinked = !Files.exists(Paths.get(dir));
		return ret;
	}



	/**
	 * returns true for symbolic link , also "soft" link
	 * for windows, it is creates using mklink /D  Link Target
	 * uses rmdir to remove soft links on windows.
	 *
	 * @param file
	 * @return
	 */
	public static boolean isSymbolicLink(String file) {
		Path path = Paths.get(file);
		return Files.isSymbolicLink(path);
	}

	/**
	 * Is the directory a link.
	 *
	 * @param file
	 * @return
	 * @throws IOException
	 */
	//not working for my hard link
	//for MAC it is returning true always.
	public static boolean isJunction(String file) throws IOException {
        if (isWindows()) {
			return !isSymbolicLink(file) && isWindowsJunction(Paths.get(file));
		}
		else
			return false;
	}

	protected static boolean isWindows() {
		return OSDetectUtils.getOS() == OSDetectUtils.OSType.WINDOWS;
	}


	//junction is created by linkd.
	//todo does it work for MAC
	protected static Boolean isWindowsJunction(Path aPath) throws IOException {
		boolean isJunction = false;
		isJunction = (aPath.compareTo(aPath.toRealPath()) != 0);
		return isJunction;
	}


	public static String executeCommand(String command) throws IOException {

		StringBuffer output = new StringBuffer();

		Process p;
		try {
			p = Runtime.getRuntime().exec(command);
			p.waitFor();
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

			String line = "";
			while ((line = reader.readLine()) != null) {
				output.append(line + "\n");
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new IOException(e);
		}

		return output.toString();

	}


	public static String getParentDir(String path) {
		//s:
		if (path.endsWith(":"))
			return path + File.separator;

		int k = path.lastIndexOf(File.separator);
		if (k > 0) {
			return path.substring(0, k);
		}
		return path;


	}


}
