/**
 * Author : Rishi Gupta
 * 
 * This file is part of 'serial communication manager' library.
 *
 * The 'serial communication manager' is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by the Free Software 
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * The 'serial communication manager' is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with serial communication manager. If not, see <http://www.gnu.org/licenses/>.
 */

package com.embeddedunveiled.serial;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;

/**
 * <p>This class load native library and is an interface between java and native shared library.</p>
 * <p>1. Extract from jar.</p>
 * <p>2. Locate in user specific temp folder.</p>
 * <p>3. Try to load native shared library.</p>
 * <p>4. Try to link functions. </p>
 * 
 * <p>Try to minimize transition from Java to JNI whenever possible for performance reason.</p>
 */

public final class SerialComJNINativeInterface {

	/* Static blocks are executed only once, irrespective of the fact, how many times,
	 * this class has been instantiated. Also they are called before invoking constructors. */
	static {
		try {
			loadNativeLibrary();
		} catch (Exception e) {
			if(SerialComManager.DEBUG) e.printStackTrace();
		}
	}

	public SerialComJNINativeInterface() {
		/* After native library has been loaded, initialize it. */
		initNativeLib();
	}

	/* Load OS specific C-library. Extract native library in our unique "_tuartx1" directory inside 
	 * OS/User specific tmp directory and load from here.
	 * http://docs.oracle.com/javase/7/docs/api/ */
	private static void loadNativeLibrary() throws SerialComException {

		File tmpDir = null;
		File workingDir = null;
		String libNameOnly = null;

		boolean readyToLoad = false;
		File libFile = null;
		InputStream input = null;
		FileOutputStream output = null;

		// Because we use same directory and file, we will not blow tmp directory out of space.
		tmpDir = new File(SerialComManager.javaTmpDir);
		if(!tmpDir.canWrite()) {
			// we don't have write permission probably, so try using user's home directory 
			tmpDir = new File(SerialComManager.userHome);
			if(!tmpDir.canWrite()) {
				throw new SerialComException("loadNativeLibrary()", SerialComErrorMapper.ERR_UNABLE_TO_WRITE);
			}
		}

		/* If the workingTmp directory exist, delete it first and then create. We do not use previously existing 
		 * directory as some other software might have created it or user might have installed different OS (arch)
		 * retaining the tmp directory. */
		if(tmpDir.exists() && tmpDir.isDirectory()){
			workingDir = new File(tmpDir.toString() + SerialComManager.fileSeparator + "_tuartx1");
			try {
				if(!workingDir.exists()) {
					workingDir.mkdir();
				}else {
					workingDir.delete();
					workingDir.mkdir();
				}
			} catch (Exception e) {
				if(SerialComManager.DEBUG) e.printStackTrace();
			}
		}

		int osType = SerialComManager.getOSType();
		if(osType > 0) {
			if(workingDir.exists() && workingDir.isDirectory()){
				if(SerialComManager.osArch.equals("i386") || SerialComManager.osArch.equals("i486") || SerialComManager.osArch.equals("i586") || 
						SerialComManager.osArch.equals("i686") || SerialComManager.osArch.equals("x86") || SerialComManager.osArch.equals("sparc")) {
					if(osType == SerialComManager.OS_LINUX) {
						libNameOnly = "linux_"   + SerialComManager.JAVA_LIB_VERSION + "_x86.so";
					}else if(osType == SerialComManager.OS_WINDOWS) {
						libNameOnly = "windows_" + SerialComManager.JAVA_LIB_VERSION + "_x86.dll";
					}else if(osType == SerialComManager.OS_MAC_OS_X) {
						libNameOnly = "mac_"     + SerialComManager.JAVA_LIB_VERSION + "_x86.dylib";
					}else if(osType == SerialComManager.OS_SOLARIS) {
						libNameOnly = "solaris_" + SerialComManager.JAVA_LIB_VERSION + "_x86.so";
					}
				}else if(SerialComManager.osArch.equals("amd64") || SerialComManager.osArch.equals("x86_64") || SerialComManager.osArch.equals("amd64 em64t x86_64") 
						|| SerialComManager.osArch.equals("x86-64") || SerialComManager.osArch.equals("sparcv9")) {
					if(osType == SerialComManager.OS_LINUX) {
						libNameOnly = "linux_"   + SerialComManager.JAVA_LIB_VERSION + "_x86_64.so";
					}else if(osType == SerialComManager.OS_WINDOWS) {
						libNameOnly = "windows_" + SerialComManager.JAVA_LIB_VERSION + "_x86_64.dll";
					}else if(osType == SerialComManager.OS_MAC_OS_X) {
						libNameOnly = "mac_"     + SerialComManager.JAVA_LIB_VERSION + "_x86_64.dylib";
					}else if(osType == SerialComManager.OS_SOLARIS) {
						libNameOnly = "solaris_" + SerialComManager.JAVA_LIB_VERSION + "_x86_64.so";
					}
				}else if(SerialComManager.osArch.startsWith("arm")) {
					libNameOnly = "linux_" + SerialComManager.JAVA_LIB_VERSION + "_armel.so";
					if(SerialComManager.javaLibPath.contains("gnueabihf") || SerialComManager.javaLibPath.contains("armhf")) {
						libNameOnly = "linux_" + SerialComManager.JAVA_LIB_VERSION + "_armhf.so";
					}else {
						try {
							// take decision based on JVM binary's format
							Process p = Runtime.getRuntime().exec("readelf -A " + System.getProperty("java.home") + "/bin/java");
							BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
							String buffer = "";
							while((buffer = reader.readLine()) != null && !buffer.isEmpty()){
								if(buffer.toLowerCase().contains("tag_abi_vfp_args")){
									libNameOnly = "linux_" + SerialComManager.JAVA_LIB_VERSION + "_armhf.so";
									break;
								}
							}
							reader.close();
						} catch (Exception e) {
							if(SerialComManager.DEBUG) e.printStackTrace();
						}
					}
				}else {
					if(SerialComManager.DEBUG) System.out.println("Unable to determine OS/CPU architecture. Please send your architecture, so that we can add support for it.");
				}

				// Get the library from jar file and extract it in our workingTmp directory
				try {
					libFile = new File(workingDir.getAbsolutePath() + SerialComManager.fileSeparator + libNameOnly);
					input = SerialComJNINativeInterface.class.getResourceAsStream("/libs/" + libNameOnly);
					output = new FileOutputStream(libFile);
					if(input != null) {
						int read;
						byte[] buffer = new byte[4096];
						while((read = input.read(buffer)) != -1){
							output.write(buffer, 0, read);
						}
						output.flush();
						// Check if we got success or not
						if(libFile != null) {
							if(libFile.exists() && libFile.isFile()){
								readyToLoad = true;
							}else {
								if(SerialComManager.DEBUG) System.out.println("Can not write as stream to libFile !");
							}
						}
					}else {
						if(SerialComManager.DEBUG) System.out.println("Can not extract library file : " + libNameOnly + " from jar file !");
					}
				} catch (Exception e) {
					if(SerialComManager.DEBUG) e.printStackTrace();
				} finally {
					try {
						output.close();
						input.close();
					} catch (Exception e) {
						if(SerialComManager.DEBUG) e.printStackTrace();
					}
				}

				/* Try loading the dynamic shared library from the local file system, 
				 * else tell user something went wrong, he should retry. */
				if(readyToLoad == true) {
					try {
						System.load(libFile.toString());
					} catch (UnsatisfiedLinkError e) {
						if(SerialComManager.DEBUG) System.err.println("Failed to load native dynamic shared library.\n" + e);
					} catch (Exception e) {
						if(SerialComManager.DEBUG) e.printStackTrace();
					}
				}
			}
		}else {
			throw new SerialComException("loadNativeLibrary()", SerialComErrorMapper.ERR_UNABLE_TO_DETECT_OS_TYPE);
		}
	}

	public native int initNativeLib();
	public native String getNativeLibraryVersion(SerialComRetStatus retStatus);
	public native boolean debug(boolean enableDebug);
	public native String[] listAvailableComPorts(SerialComRetStatus retStatus);

	public native int registerPortMonitorListener(long handle, String portName, ISerialComPortMonitor portMonitor);
	public native int unregisterPortMonitorListener(long handle);

	public native long openComPort(String portName, boolean enableRead, boolean enableWrite, boolean exclusiveOwner);
	public native int closeComPort(long handle);
	public native byte[] readBytes(long handle, int byteCount, SerialComReadStatus retStatus);
	public native byte[] readBytesBlocking(long handle, int byteCount, SerialComReadStatus retStatus);
	public native int writeBytes(long handle, byte[] buffer, int delay);
	public native int writeBytesBulk(long handle, ByteBuffer buffer);

	public native int configureComPortData(long handle, int dataBits, int stopBits, int parity, int baudRateTranslated, int custBaudTranslated);
	public native int configureComPortControl(long handle, int flowctrl, char xon, char xoff, boolean ParFraError, boolean overFlowErr);
	public native int[] getCurrentConfigurationU(long handle);
	public native String[] getCurrentConfigurationW(long handle);

	public native int setUpDataLooperThread(long handle, SerialComLooper looper);
	public native int setUpEventLooperThread(long handle, SerialComLooper looper);
	public native int destroyDataLooperThread(long handle);
	public native int destroyEventLooperThread(long handle);

	public native int pauseListeningEvents(long handle);
	public native int resumeListeningEvents(long handle);

	public native int setRTS(long handle, boolean enabled);
	public native int setDTR(long handle, boolean enabled);
	public native int[] getLinesStatus(long handle);
	public native int[] getInterruptCount(long handle);

	public native int sendBreak(long handle, int duration);
	public native int[] getByteCount(long handle);
	public native int clearPortIOBuffers(long handle, boolean rxPortbuf, boolean txPortbuf);
	public native int fineTuneRead(long handle, int vmin, int vtime, int rit, int rttm, int rttc);
}
