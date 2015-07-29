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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

import com.embeddedunveiled.serial.internal.SerialComCompletionDispatcher;
import com.embeddedunveiled.serial.internal.SerialComErrorMapper;
import com.embeddedunveiled.serial.internal.SerialComHotPlugInfo;
import com.embeddedunveiled.serial.internal.SerialComLooper;
import com.embeddedunveiled.serial.internal.SerialComPlatform;
import com.embeddedunveiled.serial.internal.SerialComPortHandleInfo;
import com.embeddedunveiled.serial.internal.SerialComPortJNIBridge;
import com.embeddedunveiled.serial.internal.SerialComPortsList;
import com.embeddedunveiled.serial.internal.SerialComSystemProperty;
import com.embeddedunveiled.serial.usb.SerialComUSBdevice;
import com.embeddedunveiled.serial.vendor.SerialComVendorLib;



/**
 * <p>Root of this library.</p>
 * <p>The WIKI page for this project is here : http://www.embeddedunveiled.com/ </p>
 * 
 * @author Rishi Gupta
 */
public final class SerialComManager {

	/** <p>Relase version of the SCM library. </p>*/
	public static final String JAVA_LIB_VERSION = "1.0.4";

	/** <p>Pre-defined enum constants for baud rate values. </p>*/
	public enum BAUDRATE {
		B0(0), B50(50), B75(75), B110(110), B134(134), B150(150), B200(200), B300(300), B600(600), B1200(1200),
		B1800(1800), B2400(2400), B4800(4800), B9600(9600), B14400(14400), B19200(19200), B28800(28800), B38400(38400),
		B56000(56000), B57600(57600), B115200(115200), B128000(128000), B153600(153600), B230400(230400), B256000(256000), 
		B460800(460800), B500000(500000), B576000(576000), B921600(921600), B1000000(1000000), B1152000(1152000),
		B1500000(1500000),B2000000(2000000), B2500000(2500000), B3000000(3000000), B3500000(3500000), B4000000(4000000),
		BCUSTOM(251);
		private int value;
		private BAUDRATE(int value) {
			this.value = value;	
		}
		public int getValue() {
			return this.value;
		}
	}

	/** <p>Pre-defined enum constants for number of data bits in a serial frame. </p>*/
	public enum DATABITS {
		/** <p>Number of data bits in one frame is 5 bits. </p>*/
		DB_5(5),
		/** <p>Number of data bits in one frame is 6 bits. </p>*/
		DB_6(6),
		/** <p>Number of data bits in one frame is 7 bits. </p>*/
		DB_7(7),
		/** <p>Number of data bits in one frame is 8 bits. </p>*/
		DB_8(8);
		private int value;
		private DATABITS(int value) {
			this.value = value;	
		}
		public int getValue() {
			return this.value;
		}
	}

	/** <p>Pre-defined enum constants for number of stop bits in a serial frame. </p>*/
	public enum STOPBITS {
		/** <p>Number of stop bits in one frame is 1. </p>*/
		SB_1(1),
		/** <p>Number of stop bits in one frame is 1.5. </p>*/
		SB_1_5(4),
		/** <p>Number of stop bits in one frame is 2. </p>*/
		SB_2(2);
		private int value;
		private STOPBITS(int value) {
			this.value = value;	
		}
		public int getValue() {
			return this.value;
		}
	}

	/** <p>Pre-defined enum constants for enabling type of parity in a serial frame. </p>*/
	public enum PARITY {
		/** The uart frame does not contain any parity bit. Errors are handled by application for example using CRC algorithm.*/
		P_NONE(1),
		/** <p>The number of bits in the frame with the value one is odd. If the sum of bits 
		 * with a value of 1 is odd in the frame, the parity bit's value is set to zero. 
		 * If the sum of bits with a value of 1 is even in the frame, the parity bit value 
		 * is set to 1, making the total count of 1's in the frame an odd number. </p>*/
		P_ODD(2),
		/** <p>The number of bits in the frame with the value one is even. The number 
		 * of bits whose value is 1 in a frame is counted. If that total is odd, 
		 * the parity bit value is set to 1, making the total count of 1's in the frame 
		 * an even number. If the count of ones in a frame a is already even, 
		 * the parity bit's value remains 0. </p>
		 * <p>Odd parity may be more fruitful since it ensures that at least one state 
		 * transition occurs in each character, which makes it more reliable as compared 
		 * even parity. </p>
		 * <p>Even parity is a special case of a cyclic redundancy check (CRC), 
		 * where the 1-bit CRC is generated by the polynomial x+1.</p>*/
		P_EVEN(3),
		/** <p>The parity bit is set to the mark signal condition (logical 1). An application
		 * may use the 9th (parity) bit for some form of addressing or special signaling. </p>*/
		P_MARK(4),
		/** <p>The parity bit is set to the space signal condition (logical 0). The mark 
		 * and space parity is uncommon, as it adds no error detection information. </p>*/
		P_SPACE(5);
		private int value;
		private PARITY(int value) {
			this.value = value;	
		}
		public int getValue() {
			return this.value;
		}
	}

	/** <p>Pre-defined enum constants for controlling data flow between DTE and DCE.</p>*/
	public enum FLOWCONTROL {
		/** <p>No flow control. Application is responsible to manage data buffers. Application can use RTS/CTS or DTR/DSR signals explicitly. </p>*/
		NONE(1),
		/** <p>Operating system (or driver) will assert or de-assert RTS/DTR lines as per the amount of data in buffers. </p>*/
		HARDWARE(2),
		/** <p>Operating system (or driver) will send XON or XOFF characters as per the amount of data in buffers. Upon reception of XOFF 
		 * system will stop transmitting data. </p>*/
		SOFTWARE(3);
		private int value;
		private FLOWCONTROL(int value) {
			this.value = value;	
		}
		public int getValue() {
			return this.value;
		}
	}

	/** <p>Pre-defined enum constants for defining endianness of data to be sent over serial port. </p>*/
	public enum ENDIAN {
		/** <p>Little endian data format. The least significant byte (LSB) value is at the lowest address. </p>*/
		E_LITTLE(1),
		/** <p>Big endian data format. The most significant byte (MSB) value is at the lowest address. </p>*/
		E_BIG(2),
		/** <p>Platform default. </p>*/
		E_DEFAULT(3);
		private int value;
		private ENDIAN(int value) {
			this.value = value;	
		}
		public int getValue() {
			return this.value;
		}
	}

	/** <p>Pre-defined enum constants for defining number of bytes given data can be represented in. </p>*/
	public enum NUMOFBYTES {
		/** <p>Integer value requires 16 bits. </p>*/
		NUM_2(2),
		/** <p>Integer value requires 32 bits. </p>*/
		NUM_4(4);
		private int value;
		private NUMOFBYTES(int value) {
			this.value = value;	
		}
		public int getValue() {
			return this.value;
		}
	}

	/** <p>Pre-defined enum constants for defining file transfer protocol to use. </p>*/
	public enum FTPPROTO {
		/** <p>XMODEM protocol with three variants checksum, CRC and 1k. </p>*/
		XMODEM(1),
		/** <p>YMODEM protocol with two variants CRC + 128 data bytes and CRC + 1k block. </p>*/
		YMODEM(2),
		/** <p>coming soon </p>*/
		ZMODEM(3);
		private int value;
		private FTPPROTO(int value) {
			this.value = value;	
		}
		public int getValue() {
			return this.value;
		}
	}
	
	/** <p>Pre-defined enum constants for defining variant of file transfer protocol to use. </p>*/
	public enum FTPVAR {
		/** <p>Checksum for XMODEM protocol, 128 data byte block for YMODEM.  </p>*/ //TODO FOR zmodem
		DEFAULT(0),
		/** <p>Checksum variant for XMODEM protocol (1 byte checksum with total block size of 132). </p>*/
		CHKSUM(1),
		/** <p>CRC variant for XMODEM protocol (2 byte CRC with total block size of 133).  </p>*/
		CRC(2),
		/** <p>1k variant for X/Y MODEM protocol (2 byte CRC with total block size of 1024). </p>*/ //TODO DOUBLE CHK THIS
		VAR1K(3),
		/** <p>128 byte data variant for YMODEM protocol (//TODO).  </p>*/
		VAR128B(4);
		private int value;
		private FTPVAR(int value) {
			this.value = value;	
		}
		public int getValue() {
			return this.value;
		}
	}
	
	/** <p>Pre-defined enum constants for defining translation mode file transfer protocol to use. </p>*/ 
	public enum FTPMODE {
		/** <p>Specify translating of data as per the operating system on which receiver application is running. </p>*/
		TEXT(1),
		/** <p>Specify no translation on data received. </p>*/
		BINARY(2);
		private int value;
		private FTPMODE(int value) {
			this.value = value;	
		}
		public int getValue() {
			return this.value;
		}
	}
	
	/** <p>Pre-defined enum constants for defining behavior of byte stream. </p>*/
	public enum SMODE {
		/** <p>Read will block till data is available. </p>*/
		BLOCKING(1), 
		/** <p>Read will not block till data is available. </p>*/
		NONBLOCKING(2);
		private int value;
		private SMODE(int value) {
			this.value = value;	
		}
		public int getValue() {
			return this.value;
		}
	}
	
	/** <p>The value indicating that operating system is unknown to SCM library. Integer constant with value 0x00. </p>*/
	public static final int OS_UNKNOWN  = 0x00;
	
	/** <p>The value indicating the Linux operating system. Integer constant with value 0x01. </p>*/
	public static final int OS_LINUX    = 0x01;
	
	/** <p>The value indicating the Windows operating system. Integer constant with value 0x02. </p>*/
	public static final int OS_WINDOWS  = 0x02;
	
	/** <p>The value indicating the Solaris operating system. Integer constant with value 0x03. </p>*/
	public static final int OS_SOLARIS  = 0x03;
	
	/** <p>The value indicating the Mac OS X operating system. Integer constant with value 0x04. </p>*/
	public static final int OS_MAC_OS_X = 0x04;
	
	/** <p>The value indicating the FreeBSD operating system.. Integer constant with value 0x05. </p>*/
	public static final int OS_FREEBSD  = 0x05;
	
	/** <p>The value indicating the NetBSD operating system. Integer constant with value 0x06. </p>*/
	public static final int OS_NETBSD   = 0x06;
	
	/** <p>The value indicating the OpenBSD operating system. Integer constant with value 0x07. </p>*/
	public static final int OS_OPENBSD  = 0x07;
	
	/** <p>The value indicating the IBM AIX operating system. Integer constant with value 0x08. </p>*/
	public static final int OS_IBM_AIX  = 0x08;
	
	/** <p>The value indicating the HP-UX operating system. Integer constant with value 0x09. </p>*/
	public static final int OS_HP_UX    = 0x09;
	
	/** <p>The value indicating the Android operating system. Integer constant with value 0x0A. </p>*/
	public static final int OS_ANDROID  = 0x0A;
	
	/** <p>The value indicating that platform architecture is unknown to SCM library. Integer constant with value 0x00. </p>*/
	public static final int ARCH_UNKNOWN  = 0x00;
	
	/** <p>The common value indicating that the library is running on a 32 bit Intel i386/i486/i586/i686/i786/i886/i986/IA-32 based architecture. Integer constant with value 0x01. </p>*/
	public static final int ARCH_X86 = 0x01;
	
	/** <p>The common value indicating that the library is running on a 64 bit Intel x86_64 (x86-64/x64/Intel 64) and AMD amd64 based architecture. Integer constant with value 0x02. </p>*/
	public static final int ARCH_AMD64 = 0x02;
	
	/** <p>The value indicating that the library is running on a 64 bit Intel/HP Itanium based architecture. Integer constant with value 0x03. </p>*/
	public static final int ARCH_IA64 = 0x03;
	
	/** <p>The value indicating that the library is running on an IA64 32 bit based architecture. Integer constant with value 0x04. </p>*/
	public static final int ARCH_IA64_32 = 0x04;
	
	/** <p>The value indicating that the library is running on a 32 bit PowerPC based architecture from Apple–IBM–Motorola. Integer constant with value 0x05. </p>*/
	public static final int ARCH_PPC32 = 0x05;
	
	/** <p>The value indicating that the library is running on a 64 bit PowerPC based architecture from Apple–IBM–Motorola. Integer constant with value 0x06. </p>*/
	public static final int ARCH_PPC64 = 0x06;
	
	/** <p>The value indicating that the library is running on a 64 bit PowerPC based architecture in little endian mode from Apple–IBM–Motorola. Integer constant with value 0x06. </p>*/
	public static final int ARCH_PPC64LE = 0x06;
	
	/** <p>The value indicating that the library is running on a 32 bit Sparc based architecture from Sun Microsystems. Integer constant with value 0x07. </p>*/
	public static final int ARCH_SPARC32 = 0x07;
	
	/** <p>The value indicating that the library is running on a 64 bit Sparc based architecture from Sun Microsystems. Integer constant with value 0x08. </p>*/
	public static final int ARCH_SPARC64 = 0x08;
	
	/** <p>The value indicating that the library is running on a 32 bit PA-RISC based architecture. Integer constant with value 0x09. </p>*/
	public static final int ARCH_PA_RISC32 = 0x09;
	
	/** <p>The value indicating that the library is running on a 64 bit PA-RISC based architecture. Integer constant with value 0x0A. </p>*/
	public static final int ARCH_PA_RISC64 = 0x0A;
	
	/** <p>The value indicating that the library is running on a 32 bit IBM S/390 system. Integer constant with value 0x0B. </p>*/
	public static final int ARCH_S390 = 0x0B;
	
	/** <p>The value indicating that the library is running on a 64 bit IBM S/390 system. Integer constant with value 0x0C. </p>*/
	public static final int ARCH_S390X = 0x0C;
	
	/** <p>The value indicating that the library is running on a ARMv5 based architecture CPU. Integer constant with value 0x0D. </p>*/
	public static final int ARCH_ARMV5 = 0x0D;
	
	/** <p>The value indicating that the library is running on a ARMv6 soft float based JVM. Integer constant with value 0x0E. </p>*/
	public static final int ARCH_ARMV6 = 0x0E;
	
	/** <p>The value indicating that the library is running on a ARMv7 soft float based JVM. Integer constant with value 0x10. </p>*/
	public static final int ARCH_ARMV7 = 0x0F;
	
	/** <p>The value indicating hard float ABI. </p>*/
	public static final int ABI_ARMHF =  0x01;
	
	/** <p>The value indicating soft float ABI. </p>*/
	public static final int ABI_ARMEL  = 0x02;

	/** <p>Default number of bytes (1024) to read from serial port. </p>*/
	public static final int DEFAULT_READBYTECOUNT = 1024;

	/** <p>Clear to send mask bit constant for UART control line. </p>*/
	public static final int CTS =  0x01;  // 0000001
	
	/** <p>Data set ready mask bit constant for UART control line. </p>*/
	public static final int DSR =  0x02;  // 0000010
	
	/** <p>Data carrier detect mask bit constant for UART control line. </p>*/
	public static final int DCD =  0x04;  // 0000100
	
	/** <p>Ring indicator mask bit constant for UART control line. </p>*/
	public static final int RI  =  0x08;  // 0001000
	
	/** <p>Loop indicator mask bit constant for UART control line. </p>*/
	public static final int LOOP = 0x10;  // 0010000
	
	/** <p>Request to send mask bit constant for UART control line. </p>*/
	public static final int RTS =  0x20;  // 0100000
	
	/** <p>Data terminal ready mask bit constant for UART control line. </p>*/
	public static final int DTR  = 0x40;  // 1000000
	
	/** <p>Maintain integrity and consistency among all operations, synchronize them for
	 *  making structural changes. This array can be sorted array if scaled to large scale.</p>*/
	private ArrayList<SerialComPortHandleInfo> handleInfo = new ArrayList<SerialComPortHandleInfo>();
	private List<SerialComPortHandleInfo> mPortHandleInfo = Collections.synchronizedList(handleInfo);
	
	private ArrayList<SerialComHotPlugInfo> hotPlugListenerInfo = new ArrayList<SerialComHotPlugInfo>();
	private List<SerialComHotPlugInfo> mHotPlugListenerInfo = Collections.synchronizedList(hotPlugListenerInfo);
	
	private SerialComIOCTLExecutor mSerialComIOCTLExecutor;
	private SerialComPlatform mSerialComPlatform;
	private final SerialComSystemProperty mSerialComSystemProperty;
	private final SerialComPortJNIBridge mComPortJNIBridge;
	private final SerialComErrorMapper mErrMapper;
	private final SerialComCompletionDispatcher mEventCompletionDispatcher;
	private final SerialComPortsList mSerialComPortsList;
	private final Object lockB = new Object();
	
	private static int osType;
	private static int cpuArch;
	private static int javaABIType;
	private static SerialComVendorLib mSerialComVendorLib;
	private static final Object lockA = new Object();
	private static boolean nativeLibLoadAndInitAlready = false;

	/**
	 * <p>Allocates a new SerialComManager object. Identify operating system type, CPU architecture, prepares 
	 * environment required for running this library, initiates extraction and loading of native libraries.</p>
	 * 
	 * <p>The native shared library will be extracted in folder named 'scm_tuartx1' inside system/user 'temp' folder 
	 * or user home folder if access to 'temp' folder is denied.</p>
	 * 
	 * @throws SecurityException if java system properties can not be  accessed
	 * @throws SerialComUnexpectedException if java system property is null
	 * @throws SerialComLoadException if any file system related issue occurs
	 * @throws UnsatisfiedLinkError if loading/linking shared library fails
	 * @throws FileNotFoundException if file "/proc/cpuinfo" can not be found for Linux on ARM platform
	 * @throws IOException if file operations on "/proc/cpuinfo" fails for Linux on ARM platform
	 * @throws SerialComException if initializing native library fails
	 */
	public SerialComManager() throws SecurityException, SerialComUnexpectedException, SerialComLoadException,
	                           UnsatisfiedLinkError, SerialComException, FileNotFoundException, IOException {
		mSerialComSystemProperty = new SerialComSystemProperty();
		synchronized(lockA) {
			if(osType <= 0) {
				mSerialComPlatform = new SerialComPlatform(mSerialComSystemProperty);
				osType = mSerialComPlatform.getOSType();
				if(osType == OS_UNKNOWN) {
					throw new SerialComException("Could not identify operating system. Please report to us your environemnt so that we can add support for it !");
				}
				cpuArch = mSerialComPlatform.getCPUArch(osType);
				if(osType == ARCH_UNKNOWN) {
					throw new SerialComException("Could not identify CPU architecture. Please report to us your environemnt so that we can add support for it !");
				}
				if((cpuArch == ARCH_ARMV7) || (cpuArch == ARCH_ARMV6) || (cpuArch == ARCH_ARMV5)) {
					if(osType == OS_LINUX) {
						javaABIType = mSerialComPlatform.getJAVAABIType();
					}
				}
			}
		}
		mErrMapper = new SerialComErrorMapper(osType);
		mComPortJNIBridge = new SerialComPortJNIBridge();
		if(nativeLibLoadAndInitAlready == false) {
			SerialComPortJNIBridge.loadNativeLibrary(null, null, mSerialComSystemProperty, osType, cpuArch, javaABIType);
			mComPortJNIBridge.initNativeLib();
			nativeLibLoadAndInitAlready = true;
		}
		mEventCompletionDispatcher = new SerialComCompletionDispatcher(mComPortJNIBridge, mErrMapper, mPortHandleInfo);
		mSerialComPortsList = new SerialComPortsList(mComPortJNIBridge, osType);
	}
	
	/**
	 * <p>Allocates a new SerialComManager object. Identify operating system type, CPU architecture, prepares 
	 * environment required for running this library, initiates extraction and loading of native libraries.</p>
	 * 
	 * <p>It extracts native shared library in the folder specified by argument directoryPath and 
	 * gives library name specified by loadedLibName. This helps in increasing isolation as completely independent 
	 * applications might also be using this library. Using different folders make sure that independent applications 
	 * unaware if each other does not override shared library file in file system.</p>
	 * 
	 * <p>This also increase security as the folder may be given specific user permissions.</p>
	 * 
	 * @param directoryPath absolute path of directory for extraction
	 * @param loadedLibName library name without extension (do not append .so, .dll or .dylib etc.)
	 * @throws SecurityException if java system properties can not be  accessed
	 * @throws SerialComUnexpectedException if java system property is null
	 * @throws SerialComLoadException if any file system related issue occurs
	 * @throws UnsatisfiedLinkError if loading/linking shared library fails
	 * @throws FileNotFoundException if file "/proc/cpuinfo" can not be found for Linux on ARM platform
	 * @throws IOException if file operations on "/proc/cpuinfo" fails for Linux on ARM platform
	 * @throws SerialComException if initializing native library fails
	 */
	public SerialComManager(String directoryPath, String loadedLibName) throws SecurityException, SerialComUnexpectedException, 
	                        SerialComLoadException, UnsatisfiedLinkError, SerialComException, FileNotFoundException, IOException {
		if(directoryPath == null) {
			throw new IllegalArgumentException("SerialComManager() " + "Argument directoryPath can not be null");
		}
		if(directoryPath.length() == 0) {
			throw new IllegalArgumentException("SerialComManager(), " + "The directory path can not be empty");
		}
		if(loadedLibName == null) {
			throw new IllegalArgumentException("SerialComManager() " + "Argument loadedLibName can not be null");
		}
		if(loadedLibName.length() == 0) {
			throw new IllegalArgumentException("SerialComManager(), " + "The library name can not be empty");
		}
		mSerialComSystemProperty = new SerialComSystemProperty();
		synchronized(lockA) {
			if(osType <= 0) {
				mSerialComPlatform = new SerialComPlatform(mSerialComSystemProperty);
				osType = mSerialComPlatform.getOSType();
				if(osType == OS_UNKNOWN) {
					throw new SerialComException("Could not identify operating system. Please report to us your environemnt so that we can add support for it !");
				}
				cpuArch = mSerialComPlatform.getCPUArch(osType);
				if(osType == ARCH_UNKNOWN) {
					throw new SerialComException("Could not identify CPU architecture. Please report to us your environemnt so that we can add support for it !");
				}
				if((cpuArch == ARCH_ARMV7) || (cpuArch == ARCH_ARMV6) || (cpuArch == ARCH_ARMV5)) {
					if(osType == OS_LINUX) {
						javaABIType = mSerialComPlatform.getJAVAABIType();
					}
				}
			}
		}
		mErrMapper = new SerialComErrorMapper(osType);
		mComPortJNIBridge = new SerialComPortJNIBridge();
		if(nativeLibLoadAndInitAlready == false) {
			SerialComPortJNIBridge.loadNativeLibrary(directoryPath, loadedLibName, mSerialComSystemProperty, osType, cpuArch, javaABIType);
			mComPortJNIBridge.initNativeLib();
			nativeLibLoadAndInitAlready = true;
		}
		mEventCompletionDispatcher = new SerialComCompletionDispatcher(mComPortJNIBridge, mErrMapper, mPortHandleInfo);
		mSerialComPortsList = new SerialComPortsList(mComPortJNIBridge, osType);
	}

	/**
	 * <p>Gives library versions of java and native library implementations.</p>
	 * 
	 * @return Java and C library versions implementing this library.
	 * @throws SerialComException if native library version could not be determined
	 */
	public String getLibraryVersions() throws SerialComException {
		String version = null;
		String nativeLibversion = mComPortJNIBridge.getNativeLibraryVersion();
		if(nativeLibversion != null) {
			version = "Java lib version: " + JAVA_LIB_VERSION + "\n" + "Native lib version: " + nativeLibversion;
		}else {
			version = "Java lib version: " + JAVA_LIB_VERSION + "\n" + "Native lib version: ?????";
		}
		return version;
	}
	
	/**
	 * <p>Gives operating system type as identified by this library. To interpret return integer see constants defined
	 * SerialComManager class.</p>
	 * 
	 * @return Operating system type as identified by the scm library
	 */
	public int getOSType() {
		return osType;
	}
	
	/**
	 * <p>Gives CPU/Platform architecture as identified by this library. To interpret return integer see constants defined
	 * SerialComManager class.</p>
	 * 
	 * @return CPU/Platform architecture as identified by the scm library
	 */
	public int getCPUArchitecture() {
		return cpuArch;
	}

	/**
	 * <p>Returns all available UART style ports available on this system, otherwise an empty array of strings, if no serial style port is
	 * found in the system.</p>
	 * 
	 * <p>This should find regular UART ports, hardware/software virtual COM ports, port server, USB-UART converter, bluetooth/3G dongles, 
	 * ports connected through USB hub/expander, serial card, serial controller, pseudo terminals, printers and virtual modems etc.</p>
	 * 
	 * <p>This method may be used to find valid serial ports for communications before opening them for writing more robust application.</p>
	 * 
	 * <p>Note : The BIOS may ignore UART ports on a PCI card and therefore BIOS settings has to be corrected if you modified
	 * default BIOS in OS.</p>
	 * 
	 * @return Available UART style ports name for windows, full path with name for Unix like OS, returns empty array if no ports found.
	 * @throws SerialComException if an I/O error occurs.
	 */
	public String[] listAvailableComPorts() throws SerialComException {
		String[] availablePorts = mSerialComPortsList.listAvailableComPorts();
		if(availablePorts != null) {
			return availablePorts;
		}else {
			return new String[] { };
		}	
	}
	
	/**
	 * <p>Returns an array containing information about all the USB devices found by this library. Application can call various 
	 * methods on returned SerialComUSBdevice class objects to get specific information like vendor id and product id etc. The 
	 * GUI applications may display a dialogue asking user to connect the end product.</p>
	 * 
	 * <p>The USB vendor id, USB product id, serial number, product name and manufacturer information is encapsulated in the 
	 * object of class SerialComUSBdevice returned.</p>
	 * 
	 * <p>Some USB-UART chip manufactures may give some unique USB PID(s) to end product manufactures at minimal or no cost. 
	 * Applications written for these end products may be interested in finding devices only from the USB-UART chip manufacturer.
	 * For example, an application built for finger print scanner based on FT232 IC will like to list only those devices whose 
	 * VID matches VID of FTDI. Then further application may verify PID by calling methods on the USBDevice object. For this 
	 * purpose argument vendorFilter may be used.</p>
	 * 
	 * @param vendorFilter vendor whose devices should be listed (one of the constants SerialComUSB.V_xxxxx or any valid USB VID)
	 * @return list of the USB devices with information about them or empty array if no device matching given criteria found
	 * @throws SerialComException if an I/O error occurs.
	 * @throws IllegalArgumentException if vendorFilter is negative
	 */
	public SerialComUSBdevice[] listUSBdevicesWithInfo(int vendorFilter) throws SerialComException {
		int i = 0;
		int numOfDevices = 0;
		SerialComUSBdevice[] usbDevicesFound = null;
		if((vendorFilter < 0) || (vendorFilter > 0XFFFF)) {
			throw new IllegalArgumentException("listUSBdevicesWithInfo(), " + "Argument vendorFilter can not be negative or greater tha 0xFFFF");
		}
		String[] usbDevicesInfo = mComPortJNIBridge.listUSBdevicesWithInfo(vendorFilter);
				
		if(usbDevicesInfo != null) {
			numOfDevices = usbDevicesInfo.length / 5;
			usbDevicesFound = new SerialComUSBdevice[numOfDevices];
			for(int x=0; x<numOfDevices; x++) {
				usbDevicesFound[x] = new SerialComUSBdevice(usbDevicesInfo[i], usbDevicesInfo[i+1], usbDevicesInfo[i+2], 
															usbDevicesInfo[i+3], usbDevicesInfo[i+4]);
				i = i + 5;
			}
			return usbDevicesFound;
		}else {
			return new SerialComUSBdevice[] { };
		}	
	}
	
	/**
	 * <p>Gives COM port (device node) assigned by operating system to the given USB-UART device.</p>
	 * 
	 * <p>Assume a bar code scanner using FTDI chip FT232R is to be used by application at point of sale.
	 * First we need to know whether it is connect to system or not. This can be done using listUSBdevicesWithInfo() 
	 * or by using hot plug listener depending upon application design.</p>
	 * 
	 * <p>Once it is known that the device is connected to system, we application need to open it. For this, application 
	 * needs to know the COM port number or device node corresponding to the scanner. It is for this purpose this method 
	 * can be used.</p>
	 * 
	 * <p>Another use case of this API is to align application design with true spirit of hot plugging in operating system. 
	 * When a USB-UART device is connected, OS may assign different COM port number or device node to the same device 
	 * depending upon system scenario. Generally we need to write custom udev rules so that device node will be same. 
	 * Using this API this limitation can be overcome.
	 * 
	 * <p>The reason why this method returns array instead of string is that two or more USB-UART converters connected 
	 * to system might have exactly same USB attributes. So this will list COM ports assigned to all of them.<p>
	 * 
	 * @param usbVidToMatch USB vendor id of the device to match
	 * @param usbPidToMatch USB product id of the device to match
	 * @param serialNumber USB serial number of device to match (case insensitive) or null if not to be matched
	 * @return list of COM port(s) (device node) for given USB device or empty array if no com port is assigned
	 * @throws SerialComException if an I/O error occurs.
	 * @throws IllegalArgumentException if usbVidToMatch or usbPidToMatch is negative
	 */
	public String[] listComPortFromUSBAttributes(int usbVidToMatch, int usbPidToMatch, final String serialNumber) throws SerialComException {
		if(usbVidToMatch < 0) {
			throw new IllegalArgumentException("listComPortFromUSBAttributes(), " + "Argument usbVidToMatch can not be negative");
		}
		if(usbPidToMatch < 0) {
			throw new IllegalArgumentException("listComPortFromUSBAttributes(), " + "Argument usbPidToMatch can not be negative");
		}
		
		String serialNum = null;
		if(serialNumber != null) {
			serialNum = serialNumber.toLowerCase();
		}
		String[] comPortsInfo = mComPortJNIBridge.listComPortFromUSBAttributes(usbVidToMatch, usbPidToMatch, serialNum);
		if(comPortsInfo != null) {
			return comPortsInfo;
		}else {
			return new String[] { };
		}	
	}

	/** 
	 * <p>This opens a serial port for communication. If an attempt is made to open a port which is already opened exception in throw.</p>
	 * 
	 * <p>For Linux and Mac OS X, if exclusiveOwnerShip is true, before this method return, the caller will either be exclusive owner
	 * or not. If the caller is successful in becoming exclusive owner than all the attempt to open the same port again will cause
	 * native code to return error. Note that a root owned process (root user) will still be able to open the port.</p>
	 * 
	 * <p>The exclusiveOwnerShip must be true for Windows as it does not allow sharing COM ports. An exception is thrown if 
	 * exclusiveOwnerShip is set to false.</p>
	 * 
	 * <p>For Solaris, exclusiveOwnerShip should be set to false as of now.</p>
	 * 
	 * <p>Sometimes, DTR acts as a modem on-hook/off-hook control for other end. By default when the SCM opens a port, it sets both
	 *  DTR and RTS signals. So just in case other end was waiting for its DTS line to be asserted can see this end as online.
	 *  Modern modems are highly flexible in their dependency, working and configurations. It is best to consult modem manual.</p>
	 * 
	 * <p>This method is thread safe.</p>
	 * 
	 * @param portName name of the port to be opened for communication
	 * @param enableRead allows application to read bytes from this port
	 * @param enableWrite allows application to write bytes to this port
	 * @param exclusiveOwnerShip application wants to become exclusive owner of this port or not
	 * @return handle of the port successfully opened
	 * @throws SerialComException if both enableWrite and enableRead are false, trying to become exclusive owner when port is already opened
	 * @throws IllegalArgumentException if portName is null or invalid length
	 */
	public long openComPort(final String portName, boolean enableRead, boolean enableWrite, boolean exclusiveOwnerShip) throws SerialComException {
		long handle = 0;
		if(portName == null) {
			throw new IllegalArgumentException("openComPort(), " + "Argument portName can not be null");
		}
		String portNameVal = portName.trim();
		if(portNameVal.length() == 0) {
			throw new IllegalArgumentException("openComPort(), " + "Name of the port to be opened can not be empty string");
		}
		if((enableRead == false) && (enableWrite == false)) {
			throw new SerialComException(portName, "openComPort()",  "Enable at-least read, write or both.");
		}
		
		if(getOSType() == OS_WINDOWS) {
			// For windows COM port can not be shared, so throw exception
			if(exclusiveOwnerShip == false) {
				throw new SerialComException(portName, "openComPort()", "Windows OS does not allow port sharing; exclusiveOwnerShip must be true");
			}
		}
		
		synchronized(lockB) {
			/* Try to reduce transitions from java to JNI layer as it is possible here by performing check in java layer itself. */
			if(exclusiveOwnerShip == true) {
				for(SerialComPortHandleInfo mInfo: mPortHandleInfo){
					if(mInfo.containsPort(portNameVal)) {
						throw new SerialComException(portName, "openComPort()", "Given port is already opened");
					}
				}
			}
			
			handle = mComPortJNIBridge.openComPort(portNameVal, enableRead, enableWrite, exclusiveOwnerShip);
			if(handle < 0) {
				/* JNI should have already thrown exception, this is an extra check to increase reliability of program */
				throw new SerialComException(portName, "openComPort()", "Could not open port. Please retry !");
			}
			boolean added = mPortHandleInfo.add(new SerialComPortHandleInfo(portNameVal, handle, null, null, null));
			if(added != true) {
				closeComPort(handle);
				throw new SerialComException(portName, "openComPort()", "Could not save info locally, please retry opening port");
			}
		}

		return handle;
	}

	/**
	 * <p>Close the serial port. Application should unregister listeners if it has registered any before calling this method.</p>
	 * 
	 * <p>DTR line is dropped when port is closed.</p>
	 * 
	 * <p>This method is thread safe.</p>
	 * 
	 * @param handle of the port to be closed
	 * @return Return true on success in closing the port false otherwise
	 * @throws SerialComException if invalid handle is passed or when it fails in closing the port
	 * @throws IllegalStateException if application tries to close port while data/event listener exist
	 */
	public boolean closeComPort(long handle) throws SerialComException {
		boolean handlefound = false;
		SerialComPortHandleInfo mHandleInfo = null;

		synchronized(lockB) {
			for(SerialComPortHandleInfo mInfo: mPortHandleInfo){
				if(mInfo.containsHandle(handle)) {
					handlefound = true;
					mHandleInfo = mInfo;
					break;
				}
			}
	
			if(handlefound == false) {
				throw new SerialComException("closeComPort()", "Wrong port handle passed");
			}
	
			if(mHandleInfo.getDataListener() != null) {
				/* Proper clean up requires that, native thread should be destroyed before closing port. */
				throw new IllegalStateException("closeComPort() " + "Closing port without unregistering data listener is not allowed");
			}
			if(mHandleInfo.getEventListener() != null) {
				throw new IllegalStateException("closeComPort() " + "Closing port without unregistering event listener is not allowed");
			}
	
			mComPortJNIBridge.closeComPort(handle);
	
			/* delete info about this port/handle from global info arraylist. */
			mPortHandleInfo.remove(mHandleInfo);
		}

		return true;
	}

	/**
	 * <p>This method writes bytes from the specified byte type buffer. If the method returns false, the application
	 * should try to re-send bytes. The data has been transmitted out of serial port when this method returns.</p>
	 * 
	 * <p>If large amount of data need to be written, consider breaking it into chunks of data of size for example
	 * 2KB each.</p>
	 * 
	 * <p>Writing empty buffer i.e. zero length array is not allowed.</p>
	 * 
	 * <p>It should be noted that on Linux system reading from the terminal after a disconnect causes an end-of-file
	 * condition, and writing causes an EIO error to be returned. The terminal device must be closed and reopened to
	 * clear the condition.</p>
	 * 
	 * @param handle handle of the opened port on which to write bytes
	 * @param buffer byte type buffer containing bytes to be written to port
	 * @param delay  time gap between transmitting two successive bytes
	 * @return true on success, false on failure or if empty buffer is passed
	 * @throws SerialComException if an I/O error occurs.
	 * @throws IllegalArgumentException if buffer is null or delay is negative
	 */
	public boolean writeBytes(long handle, final byte[] buffer, int delay) throws SerialComException {
		if(buffer == null) {
			throw new IllegalArgumentException("writeBytes(), " + "Argumenet buffer can not be null");
		}
		if(buffer.length == 0) {
			return false;
		}
		if(delay < 0) {
			throw new IllegalArgumentException("writeBytes(), " + "Argument delay can not be negative");
		}
		
		int ret = mComPortJNIBridge.writeBytes(handle, buffer, delay);
		if(ret < 0) {
			/* JNI should have already thrown exception, this is an extra check to increase reliability of program */
			throw new SerialComException("writeBytes()", "Could not write data to serial port. Please retry !");
		}
		return true;
	}

	/**
	 * <p>Utility method to call writeBytes without delay between successive bytes.</p>
	 * <p>The writeBytes(handle, buffer) method for class SerialComManager has the same effect
	 * as: </p>
	 * <p>writeBytes(handle, buffer, 0) </p>
	 * 
	 * @param handle handle of the opened port on which to write bytes
	 * @param buffer byte type buffer containing bytes to be written to port
	 * @return true on success, false on failure or if empty buffer is passed
	 * @throws SerialComException if an I/O error occurs.
	 * @throws IllegalArgumentException if buffer is null
	 */
	public boolean writeBytes(long handle, byte[] buffer) throws SerialComException {
		return writeBytes(handle, buffer, 0);
	}

	/**
	 * <p>This method writes a single byte to the specified port. The data has been transmitted out of serial port when 
	 * this method returns.</p>
	 * 
	 * @param handle handle of the opened port on which to write byte
	 * @param data byte to be written to port
	 * @return true on success false otherwise
	 * @throws SerialComException if an I/O error occurs.
	 */
	public boolean writeSingleByte(long handle, byte data) throws SerialComException {
		return writeBytes(handle, new byte[] { data }, 0);
	}

	/**
	 * <p>This method writes a string to the specified port. The library internally converts string to byte buffer. 
	 * The data has been transmitted out of serial port when this method returns.</p>
	 * 
	 * @param handle handle of the opened port on which to write byte
	 * @param data the string to be send to port
	 * @param delay interval between two successive bytes while sending string
	 * @return true on success false otherwise
	 * @throws SerialComException if an I/O error occurs.
	 * @throws IllegalArgumentException if data is null
	 */
	public boolean writeString(long handle, String data, int delay) throws SerialComException {
		if(data == null) {
			throw new IllegalArgumentException("writeString(), " + "Argument data can not be null");
		}
		return writeBytes(handle, data.getBytes(), delay);
	}

	/**
	 * <p>This method writes a string to the specified port. The library internally converts string to byte buffer. 
	 * The data has been transmitted out of serial port when this method returns.</p>
	 * 
	 * @param handle handle of the opened port on which to write byte
	 * @param data the string to be send to port
	 * @param charset the character set into which given string will be encoded
	 * @param delay  time gap between transmitting two successive bytes in this string
	 * @return true on success false otherwise
	 * @throws SerialComException if an I/O error occurs.
	 * @throws IllegalArgumentException if data is null
	 */
	public boolean writeString(long handle, final String data, Charset charset, int delay) throws UnsupportedEncodingException, SerialComException {
		if(data == null) {
			throw new IllegalArgumentException("writeString(), " + "Argument data can not be null");
		}
		return writeBytes(handle, data.getBytes(charset), delay);
	}

	/** 
	 * <p>Different CPU and OS will have different endianness. It is therefore we handle the endianness conversion 
	 * as per the requirement. If the given integer is in range −32,768 to 32,767, only two bytes will be needed.
	 * In such case we might like to send only 2 bytes to serial port. On the other hand application might be implementing
	 * some custom protocol so that the data must be 4 bytes (irrespective of its range) in order to be interpreted 
	 * correctly by the receiver terminal. This method assumes that integer value can be represented by 32 or less
	 * number of bits. On x86_64 architecture, loss of precision will occur if the integer value is of more than 32 bit.</p>
	 * 
	 * <p>The data has been transmitted physically out of serial port when this method returns.</p>
	 * 
	 * <p>In java numbers are represented in 2's complement, so number 650 whose binary representation is 0000001010001010
	 * is printed byte by byte, then will be printed as 1 and -118, because 10001010 in 2's complement is negative number.</p>
	 * 
	 * @param handle handle of the opened port on which to write byte
	 * @param data an integer number to be sent to port
	 * @param delay interval between two successive bytes 
	 * @param endianness big or little endian sequence to be followed while sending bytes representing this integer
	 * @param numOfBytes number of bytes this integer can be represented in
	 * @return true on success false otherwise
	 * @throws SerialComException if an I/O error occurs.
	 * @throws IllegalArgumentException if endianness or numOfBytes is null
	 */
	public boolean writeSingleInt(long handle, int data, int delay, ENDIAN endianness, NUMOFBYTES numOfBytes) throws SerialComException {
		byte[] buffer = null;
		
		if(endianness == null) {
			throw new IllegalArgumentException("writeSingleInt() " + "Argument endianness can not be null");
		}
		if(numOfBytes == null) {
			throw new IllegalArgumentException("writeSingleInt() " + "Argument numOfBytes can not be null");
		}

		if(numOfBytes.getValue() == 2) {             // conversion to two bytes data
			buffer = new byte[2];
			if(endianness.getValue() == 1) {         // Little endian
				buffer[1] = (byte) (data >>> 8);
				buffer[0] = (byte)  data;
			}else {                                 // big endian/default (java is big endian by default)
				buffer[1] = (byte)  data;
				buffer[0] = (byte) (data >>> 8);
			}
			return writeBytes(handle, buffer, delay);
		}else {                                     // conversion to four bytes data
			buffer = new byte[4];
			if(endianness.getValue() == 1) {        // Little endian
				buffer[3] = (byte) (data >>> 24);
				buffer[2] = (byte) (data >>> 16);
				buffer[1] = (byte) (data >>> 8);
				buffer[0] = (byte)  data;
			}else {                                 // big endian/default (java is big endian by default)
				buffer[3] = (byte)  data;
				buffer[2] = (byte) (data >>> 8);
				buffer[1] = (byte) (data >>> 16);
				buffer[0] = (byte) (data >>> 24);
			}
			return writeBytes(handle, buffer, delay);
		}
	}

	/** 
	 * <p>This method send an array of integers on the specified port. The data has been transmitted out of serial 
	 * port when this method returns.</p>
	 * 
	 * @param handle handle of the opened port on which to write byte
	 * @param buffer an array of integers to be sent to port
	 * @param delay interval between two successive bytes 
	 * @param endianness big or little endian sequence to be followed while sending bytes representing this integer
	 * @param numOfBytes number of bytes this integer can be represented in
	 * @return true on success false otherwise
	 * @throws SerialComException if an I/O error occurs.
	 * @throws IllegalArgumentException if endianness or numOfBytes is null
	 */
	public boolean writeIntArray(long handle, final int[] buffer, int delay, ENDIAN endianness, NUMOFBYTES numOfBytes) throws SerialComException {
		byte[] localBuf = null;
		
		if(endianness == null) {
			throw new IllegalArgumentException("writeIntArray() " + "Argument endianness can not be null");
		}
		if(numOfBytes == null) {
			throw new IllegalArgumentException("writeIntArray() " + "Argument numOfBytes can not be null");
		}

		if(numOfBytes.getValue() == 2) {
			localBuf = new byte[2 * buffer.length];
			if(endianness.getValue() == 1) {                 // little endian
				int a = 0;
				for(int b=0; b<buffer.length; b++) {
					localBuf[a] = (byte)  buffer[b];
					a++;
					localBuf[a] = (byte) (buffer[b] >>> 8);
					a++;
				}
			}else {                                         // big/default endian
				int c = 0;
				for(int d=0; d<buffer.length; d++) {
					localBuf[c] = (byte) (buffer[d] >>> 8);
					c++;
					localBuf[c] = (byte)  buffer[d];
					c++;
				}
			}
			return writeBytes(handle, localBuf, delay);
		}else {
			localBuf = new byte[4 * buffer.length];
			if(endianness.getValue() == 1) {                  // little endian
				int e = 0;
				for(int f=0; f<buffer.length; f++) {
					localBuf[e] = (byte)  buffer[f];
					e++;
					localBuf[e] = (byte) (buffer[f] >>> 8);
					e++;
					localBuf[e] = (byte) (buffer[f] >>> 16);
					e++;
					localBuf[e] = (byte) (buffer[f] >>> 24);
					e++;
				}
			}else {                                          // big/default endian
				int g = 0;
				for(int h=0; h<buffer.length; h++) {
					localBuf[g] = (byte)  buffer[h];
					g++;
					localBuf[g] = (byte) (buffer[h] >>> 8);
					g++;
					localBuf[g] = (byte) (buffer[h] >>> 16);
					g++;
					localBuf[g] = (byte) (buffer[h] >>> 24);
					g++;
				}
			}
			return writeBytes(handle, localBuf, delay);
		}
	}
	
	/**
	 * <p>Writes the bytes from the given direct byte buffer using facilities of the underlying JVM and operating system. 
	 * When this method returns data would have sent out of serial port physically.</p>
	 * 
	 * <p>Consider using this method when developing applications based on Bluetooth serial port profile or applications 
	 * like printing document using printer.</p>
	 * 
	 * <p>This method does not modify the direct byte buffer attributes position, capacity, limit and mark. The application 
	 * design is expected to take care of this as and when required in appropriate manner. Further, this method does not 
	 * consume or modify the data in the given buffer.</p>
	 * 
	 * @param handle handle of the serial port on which to write bytes
	 * @param direct byte buffer containing bytes to be written to port
	 * @param offset location from where to start sending data out of serial port
	 * @param length number of bytes from offset to sent to serial port
	 * @return number of bytes sent to serial port, 0 if length is 0
	 * @throws SerialComException if an I/O error occurs.
	 * @throws IllegalArgumentException if buffer is null, or if position or limit is negative, 
	 *          or if given buffer is not direct byte buffer, or if length > (buffer.capacity() - offset)
	 */
	public int writeBytesDirect(long handle, ByteBuffer buffer, int offset, int length) throws SerialComException {
		if(buffer == null) {
			throw new IllegalArgumentException("writeBytesDirect(), " + "Argument buffer can not be null !");
		}
		if((offset < 0) || (length < 0)) {
			throw new IllegalArgumentException("writeBytesDirect(), " + "Argument offset or length can not be negative !");
		}
		if(!buffer.isDirect()) {
			throw new IllegalArgumentException("writeBytesDirect(), " + "Given buffer is not direct byte buffer !");
		}
		if(length > (buffer.capacity() - offset)) {
			throw new IllegalArgumentException("writeBytesDirect(), " + "Index violation detected !");
		}
		if(length == 0) {
			return 0;
		}

		int ret = mComPortJNIBridge.writeBytesDirect(handle, buffer, offset, length);
		if(ret < 0) {
			throw new SerialComException("writeBytesDirect()", "Could not write given data to serial port. Please retry !");
		}
		return ret;
	}
	
	/**
	 * <p>Reads the bytes from the serial port into the given direct byte buffer using facilities of the underlying 
	 * JVM and operating system.</p>
	 * 
	 * <p>This method does not modify the direct byte buffer attributes position, capacity, limit and mark. The application 
	 * design is expected to take care of this as and when required in appropriate manner.</p>
	 * 
	 * @param handle handle of the serial port from which to read data bytes
	 * @param direct byte buffer into which data bytes will be placed
	 * @param offset location in byte buffer from which to start saving data
	 * @param length number of bytes from offset to read in buffer
	 * @return number of bytes read from serial port, 0 if length is 0
	 * @throws SerialComException if an I/O error occurs.
	 * @throws IllegalArgumentException if buffer is null, or if position or limit is negative, or if given buffer is not direct byte buffer
	 */
	public int readBytesDirect(long handle, ByteBuffer buffer, int offset, int length) throws SerialComException {
		if(buffer == null) {
			throw new IllegalArgumentException("readBytesDirect(), " + "Argument buffer can not be null !");
		}
		if((offset < 0) || (length < 0)) {
			throw new IllegalArgumentException("readBytesDirect(), " + "Argument offset or length can not be negative !");
		}
		if(!buffer.isDirect()) {
			throw new IllegalArgumentException("readBytesDirect(), " + "Argument buffer is not direct byte buffer !");
		}
		if(length > (buffer.capacity() - offset)) {
			throw new IllegalArgumentException("readBytesDirect(), " + "Index violation detected !");
		}
		if(length == 0) {
			return 0;
		}

		int ret = mComPortJNIBridge.readBytesDirect(handle, buffer, offset, length);
		if(ret < 0) {
			throw new SerialComException("writeBytesDirect()", "Could not write given data to serial port. Please retry !");
		}
		return ret;
	}
	
	/** 
	 * <p>Read specified number of bytes from given serial port and stay blocked till bytes arrive at serial port.</p>
	 * <p>1. If data is read from serial port, array of bytes containing data is returned.</p>
	 * <p>2. If there was no data in serial port to read, null is returned. Note that this case is not possible however for 
	 * blocking read call.</p>
	 * 
	 * <p>The number of bytes to read must be greater than or equal to 1 and less than or equal to 2048 (1 <= byteCount <= 2048).
	 * This method may return less than the requested number of bytes due to reasons like, there is less data in operating system
	 * buffer (serial port) or operating system returned less data which is also legal.</p>
	 * 
	 * @param handle of the serial port from which to read bytes
	 * @param byteCount number of bytes to read from serial port
	 * @return array of bytes read from port or null
	 * @throws SerialComException if an I/O error occurs or if byteCount is greater than 2048
	 */
	public byte[] readBytesBlocking(long handle, int byteCount) throws SerialComException {
		if(byteCount > 2048) {
			throw new SerialComException("Number of bytes to read can not be greater than 2048 !");
		}
		byte[] buffer = null;
		if(osType == SerialComManager.OS_WINDOWS) {
			buffer = mComPortJNIBridge.readBytesBlocking(handle, byteCount);
		}else {
			buffer = mComPortJNIBridge.readBytes(handle, byteCount);
		}
		
		// data read from serial port, pass to application
		if(buffer != null) {
			return buffer;
		}else {
			return null; // not possible for blocking call, just keeping it
		}
	}

	/** 
	 * <p>Read specified number of bytes from given serial port.</p>
	 * <p>1. If data is read from serial port, array of bytes containing data is returned.</p>
	 * <p>2. If there was no data in serial port to read, null is returned.</p>
	 * 
	 * <p>The number of bytes to read must be greater than or equal to 1 and less than or equal to 2048 (1 <= byteCount <= 2048).
	 * This method may return less than the requested number of bytes due to reasons like, there is less data in operating system
	 * buffer (serial port) or operating system returned less data which is also legal.</p>
	 * 
	 * @param handle of the serial port from which to read bytes
	 * @param byteCount number of bytes to read from serial port
	 * @return array of bytes read from port or null
	 * @throws SerialComException if an I/O error occurs or if byteCount is greater than 2048
	 */
	public byte[] readBytes(long handle, int byteCount) throws SerialComException {
		if(byteCount > 2048) {
			throw new SerialComException("Number of bytes to read can not be greater than 2048 !");
		}
		byte[] buffer = mComPortJNIBridge.readBytes(handle, byteCount);
		if(buffer != null) {
			return buffer; // data read from serial port, pass it the to application
		}else {
			return null;  // serial port does not have any data
		}
	}

	/** 
	 * <p>If user does not specify any count, library try to read DEFAULT_READBYTECOUNT (1024 bytes) bytes as default value.</p>
	 * 
	 * <p>It has same effect as readBytes(handle, 1024)</p>
	 * 
	 * @param handle of the port from which to read bytes
	 * @return array of bytes read from port or null
	 * @throws SerialComException if an I/O error occurs.
	 */
	public byte[] readBytes(long handle) throws SerialComException {
		return readBytes(handle, DEFAULT_READBYTECOUNT);
	}

	/**
	 * <p>This method reads data from serial port and converts it into string. Caller has more finer control over the byte operation.</p>
	 * 
	 * <p> It Constructs a new string by decoding the specified array of bytes using the platform's default charset. The length of the new
     * string is a function of the charset, and hence may not be equal to the length of the byte array read from serial port.</p>
	 * 
	 * @param handle of port from which to read bytes
	 * @param byteCount number of bytes to read from this port
	 * @return string constructed from data read from serial port or null
	 * @throws SerialComException if an I/O error occurs or if byteCount is greater than 2048
	 */
	public String readString(long handle, int byteCount) throws SerialComException {
		byte[] buffer = readBytes(handle, byteCount);
		if(buffer != null) {
			return new String(buffer);
		}
		return null;
	}

	/**
	 * <p>This method reads data from serial port and converts it into string. Caller has more finer control over the byte operation.</p>
	 * 
	 * <p> It Constructs a new string by decoding the specified array of bytes using the platform's default charset. The length of the new
     * string is a function of the charset, and hence may not be equal to the length of the byte array read from serial port.</p>
     * 
	 * <p>Note that the length of data bytes read using this method can not be greater than DEFAULT_READBYTECOUNT i.e. 1024.</p>
	 * 
	 * @param handle of the port from which to read bytes
	 * @return string constructed from data read from serial port or null
	 * @throws SerialComException if an I/O error occurs.
	 */
	public String readString(long handle) throws SerialComException {
		return readString(handle, DEFAULT_READBYTECOUNT);
	}

	/** 
	 * <p>This is a utility method to read a single byte from serial port.</p>
	 * 
	 * <p>Its effect is same as readBytes(handle, 1)</p>
	 * 
	 * @param handle of the port from which to read byte
	 * @return array of length 1 representing 1 byte data read from serial port or null
	 * @throws SerialComException if an I/O error occurs.
	 */
	public byte[] readSingleByte(long handle) throws SerialComException {
		return readBytes(handle, 1);
	}

	/**
	 * <p>This method configures the rate at which communication will occur and the format of data frame. Note that, most of the DTE/DCE (hardware)
	 * does not support different baud rates for transmission and reception and therefore we take only single value applicable to both transmission and
	 * reception. Further, all the hardware and OS does not support all the baud rates (maximum change in signal per second). It is the applications 
	 * responsibility to consider these factors when writing portable software.</p>
	 * 
	 * <p>If parity is enabled, the parity bit will be removed from frame before passing it library.</p>
	 * 
	 * Note: (1) some restrictions apply in case of Windows. Please refer http://msdn.microsoft.com/en-us/library/windows/desktop/aa363214(v=vs.85).aspx
	 * for details.
	 * 
	 * <p>(2) Some drivers especially windows driver for usb to serial converters support non-standard baud rates. They either supply a text file that can be used for 
	 * configuration or user may edit windows registry directly to enable this support. The user supplied standard baud rate is translated to custom baud rate as 
	 * specified in vendor specific configuration file.</p>
	 * 
	 * <p>Take a look at http://www.ftdichip.com/Support/Documents/AppNotes/AN232B-05_BaudRates.pdf to understand using custom baud rates with USB-UART chips.</p>
	 * 
	 * @param handle of opened port to which this configuration applies to
	 * @param dataBits number of data bits in one frame (refer DATABITS enum for this)
	 * @param stopBits number of stop bits in one frame (refer STOPBITS enum for this)
	 * @param parity of the frame (refer PARITY enum for this)
	 * @param baudRate of the frame (refer BAUDRATE enum for this)
	 * @param custBaud custom baudrate if the desired rate is not included in BAUDRATE enum
	 * @return true on success false otherwise
	 * @throws SerialComException if invalid handle is passed or an error occurs in configuring the port
	 * @throws IllegalArgumentException if dataBits or stopBits or parity or baudRate is null, or if custBaud is zero or negative
	 */
	public boolean configureComPortData(long handle, DATABITS dataBits, STOPBITS stopBits, PARITY parity, BAUDRATE baudRate, int custBaud) throws SerialComException {

		int baudRateTranslated = 0;
		int custBaudTranslated = 0;
		int baudRateGiven = 0;
		
		if(dataBits == null) {
			throw new IllegalArgumentException("configureComPortData() " + "Argument dataBits can not be null");
		}
		if(stopBits == null) {
			throw new IllegalArgumentException("configureComPortData() " + "Argument stopBits can not be null");
		}
		if(parity == null) {
			throw new IllegalArgumentException("configureComPortData() " + "Argument parity can not be null");
		}
		if(baudRate == null) {
			throw new IllegalArgumentException("configureComPortData() " + "Argument baudRate can not be null");
		}

		boolean handlefound = false;
		for(SerialComPortHandleInfo mInfo: mPortHandleInfo){
			if(mInfo.containsHandle(handle)) {
				handlefound = true;
				break;
			}
		}
		if(handlefound == false) {
			throw new SerialComException("configureComPortData()", "Wrong port handle passed for the requested operations");
		}

		baudRateGiven = baudRate.getValue();
		if(baudRateGiven != 251) {
			baudRateTranslated = baudRateGiven;
			custBaudTranslated = 0;
		}else {
			// custom baud rate
			if(custBaud <= 0) {
				throw new IllegalArgumentException("configureComPortData() " + "Baudrate can not be negative or zero");
			}
			baudRateTranslated = baudRateGiven;
			custBaudTranslated = custBaud;
		}

		int ret = mComPortJNIBridge.configureComPortData(handle, dataBits.getValue(), stopBits.getValue(), parity.getValue(), baudRateTranslated, custBaudTranslated);
		if(ret < 0) {
			/* JNI should have already thrown exception, this is an extra check to increase reliability of program */
			throw new SerialComException("configureComPortData()", "Could not configure serial port. Please retry !");
		}
		return true;
	}

	/**
	 * <p>This method configures the way data communication will be controlled between DTE and DCE. This specifies flow control and actions that will
	 * be taken when an error is encountered in communication.</p>
	 * 
	 * @param handle of opened port to which this configuration applies to
	 * @param flowctrl flow control, how data flow will be controlled (refer FLOWCONTROL enum for this)
	 * @param xon character representing on condition if software flow control is used
	 * @param xoff character representing off condition if software flow control is used
	 * @param ParFraError true if parity and frame errors are to be checked false otherwise
	 * @param overFlowErr true if overflow error is to be detected false otherwise
	 * @return true on success false otherwise
	 * @throws SerialComException if invalid handle is passed or an error occurs in configuring the port
	 * @throws IllegalArgumentException if flowctrl is null
	 */
	public boolean configureComPortControl(long handle, FLOWCONTROL flowctrl, char xon, char xoff, boolean ParFraError, boolean overFlowErr) throws SerialComException {
		boolean handlefound = false;
		
		if(flowctrl == null) {
			throw new IllegalArgumentException("configureComPortControl() " + "Argument flowctrl can not be null");
		}
		
		for(SerialComPortHandleInfo mInfo: mPortHandleInfo){
			if(mInfo.containsHandle(handle)) {
				handlefound = true;
				break;
			}
		}
		if(handlefound == false) {
			throw new SerialComException("configureComPortControl()", "Wrong port handle passed for the requested operations");
		}

		int ret = mComPortJNIBridge.configureComPortControl(handle, flowctrl.getValue(), xon, xoff, ParFraError, overFlowErr);
		if(ret < 0) {
			/* JNI should have already thrown exception, this is an extra check to increase reliability of program */
			throw new SerialComException("configureComPortData()", "Could not configure serial port. Please retry !");
		}
		return true;
	}

	/**
	 * <p>This method gives currently applicable settings associated with particular serial port.
	 * The values are bit mask so that application can manipulate them to get required information.</p>
	 * 
	 * <p>For Unix-like OS the order is : c_iflag, c_oflag, c_cflag, c_lflag, c_line, c_cc[0], c_cc[1], c_cc[2], c_cc[3]
	 * c_cc[4], c_cc[5], c_cc[6], c_cc[7], c_cc[8], c_cc[9], c_cc[10], c_cc[11], c_cc[12], c_cc[13], c_cc[14],
	 * c_cc[15], c_cc[16], c_ispeed and c_ospeed.</p>
	 * 
	 * <p>For Windows OS the order is :DCBlength, BaudRate, fBinary, fParity, fOutxCtsFlow, fOutxDsrFlow, fDtrControl,
	 * fDsrSensitivity, fTXContinueOnXoff, fOutX, fInX, fErrorChar, fNull, fRtsControl, fAbortOnError, fDummy2,
	 * wReserved, XonLim, XoffLim, ByteSize, Parity, StopBits, XonChar, XoffChar, ErrorChar, StopBits, EvtChar,
	 * wReserved1.</p>
	 * 
	 * @param handle of the opened port
	 * @return array of string giving configuration
	 * @throws SerialComException if invalid handle is passed or an error occurs while reading current settings
	 */
	public String[] getCurrentConfiguration(long handle) throws SerialComException {

		boolean handlefound = false;
		for(SerialComPortHandleInfo mInfo: mPortHandleInfo){
			if(mInfo.containsHandle(handle)) {
				handlefound = true;
				break;
			}
		}
		if(handlefound == false) {
			throw new SerialComException("getCurrentConfiguration()", "Wrong port handle passed for the requested operations");
		}

		if(getOSType() != OS_WINDOWS) {
			// for unix-like os
			int[] config = mComPortJNIBridge.getCurrentConfigurationU(handle);
			String[] configuration = new String[config.length];
			if(config[0] < 0) {
				throw new SerialComException("getCurrentConfiguration()", "Could not determine current configuration. Please retry !");
			}
			// if an error occurs, config[0] will contain error code, otherwise actual data
			for(int x=0; x<config.length; x++) {
				configuration[x] = "" + config[x];
			}
			return configuration;
		}else {
			// for windows os
			String[] configuration = mComPortJNIBridge.getCurrentConfigurationW(handle);
			return configuration;
		}
	}

	/**
	 * <p>This method assert/de-assert RTS line of serial port. Set "true" for asserting signal, false otherwise. 
	 * This changes the state of RTS line electrically.</p>
	 * 
	 * <p>RTS and DTR lines can be asserted or de-asserted even when using no flow control on serial port.</p>
	 * 
	 * <p>The RS-232 standard defines the voltage levels that correspond to logical one and logical zero levels for the data 
	 * transmission and the control signal lines. Valid signals are either in the range of +3 to +15 volts or the range 
	 * −3 to −15 volts with respect to the ground/common pin; consequently, the range between −3 to +3 volts is not a 
	 * valid RS-232 level.</p>
	 * 
	 * <p>In asserted condition, voltage at pin number 7 (RTS signal) will be greater than 3 volts. Voltage 5.0 volts
	 * was observed when using USB-UART converter http://www.amazon.in/Bafo-USB-Serial-Converter-DB9/dp/B002SCRCDG.</p>
	 * 
	 * <p>On some hardware IC, signals may be active low and therefore for actual voltage datasheet should be consulted. Also please check if the 
	 * driver supports setting RTS/DTR lines or not.<p>
	 * 
	 * @param handle of the opened port
	 * @param enabled if true RTS will be asserted and vice-versa
	 * @return true on success false otherwise
	 * @throws SerialComException if system is unable to complete requested operation
	 */
	public boolean setRTS(long handle, boolean enabled) throws SerialComException {
		mComPortJNIBridge.setRTS(handle, enabled);
		return true;
	}

	/**
	 * <p>This method assert/de-assert DTR line of serial port. Set "true" for asserting signal, false otherwise. 
	 * This changes the state of RTS line electrically.</p>
	 * 
	 * <p>RTS and DTR lines can be asserted or de-asserted even when using no flow control on serial port.</p>
	 * 
	 * @param handle of the opened port
	 * @param enabled if true DTR will be asserted and vice-versa
	 * @return true on success false otherwise
	 * @throws SerialComException if system is unable to complete requested operation
	 */
	public boolean setDTR(long handle, boolean enabled) throws SerialComException {
		mComPortJNIBridge.setDTR(handle, enabled);
		return true;
	}

	/**
	 * <p>This method associate a data looper with the given listener. This looper will keep delivering new data whenever
	 * it is made available from native data collection and dispatching subsystem.
	 * Note that listener will start receiving new data, even before this method returns.</p>
	 * 
	 * <p>Application (listener) should implement ISerialComDataListener and override onNewSerialDataAvailable method.</p>
	 * 
	 * <p>The scm library can manage upto 1024 listeners corresponding to 1024 port handles.</p>
	 * <p>This method is thread safe.</p>
	 * 
	 * @param handle of the port opened
	 * @param dataListener instance of class which implements ISerialComDataListener interface
	 * @return true on success false otherwise
	 * @throws SerialComException if invalid handle passed, handle is null or data listener already exist for this handle
	 * @throws IllegalArgumentException if dataListener is null 
	 */
	public boolean registerDataListener(long handle, final ISerialComDataListener dataListener) throws SerialComException {

		boolean handlefound = false;
		SerialComPortHandleInfo mHandleInfo = null;

		if(dataListener == null) {
			throw new IllegalArgumentException("registerDataListener(), " + "Argument dataListener can not be null");
		}
		
		synchronized(lockB) {
			for(SerialComPortHandleInfo mInfo: mPortHandleInfo){
				if(mInfo.containsHandle(handle)) {
					handlefound = true;
					if(mInfo.getDataListener() != null) {
						throw new SerialComException("registerDataListener()", "Data listener already exist. Only one listener allowed.");
					}else {
						mHandleInfo = mInfo;
					}
					break;
				}
			}
	
			if(handlefound == false) {
				throw new SerialComException("registerDataListener()", "Wrong port handle passed for the requested operations");
			}
	
			return mEventCompletionDispatcher.setUpDataLooper(handle, mHandleInfo, dataListener);
		}
	}

	/**
	 * <p>This method destroys complete java and native looper subsystem associated with this particular data listener. This has no
	 * effect on event looper subsystem. This method returns only after native thread has been terminated successfully.</p>
	 * 
	 * <p>This method is thread safe.</p>
	 * 
	 * @param dataListener instance of class which implemented ISerialComDataListener interface
	 * @return true on success false otherwise
	 * @throws SerialComException if null value is passed in dataListener field
	 * @throws IllegalArgumentException if dataListener is null 
	 */
	public boolean unregisterDataListener(final ISerialComDataListener dataListener) throws SerialComException {
		if(dataListener == null) {
			throw new IllegalArgumentException("unregisterDataListener(), " + "Argument dataListener can not be null");
		}

		synchronized(lockB) {
			if(mEventCompletionDispatcher.destroyDataLooper(dataListener)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * <p>This method associate a event looper with the given listener. This looper will keep delivering new event whenever
	 * it is made available from native event collection and dispatching subsystem.</p>
	 * 
	 * <p>Application (listener) should implement ISerialComEventListener and override onNewSerialEvent method.</p>
	 * 
	 * <p>By default all four events are dispatched to listener. However, application can mask events through setEventsMask()
	 * method. In current implementation, native code sends all the events irrespective of mask and we actually filter
	 * them in java layers, to decide whether this should be sent to application or not (as per the mask set by
	 * setEventsMask() method).</p>
	 * 
	 * <p>Before calling this method, make sure that port has been configured for hardware flow control using configureComPortControl
	 * method.</p>
	 * <p>This method is thread safe.</p>
	 * 
	 * @param handle of the port opened
	 * @param eventListener instance of class which implements ISerialComEventListener interface
	 * @return true on success false otherwise
	 * @throws SerialComException if invalid handle passed, handle is null or event listener already exist for this handle
	 * @throws IllegalArgumentException if eventListener is null 
	 */
	public boolean registerLineEventListener(long handle, final ISerialComEventListener eventListener) throws SerialComException {
		boolean handlefound = false;
		SerialComPortHandleInfo mHandleInfo = null;

		if(eventListener == null) {
			throw new IllegalArgumentException("registerLineEventListener(), " + "Argument eventListener can not be null");
		}

		synchronized(lockB) {
			for(SerialComPortHandleInfo mInfo: mPortHandleInfo){
				if(mInfo.containsHandle(handle)) {
					handlefound = true;
					if(mInfo.getEventListener() != null) {
						throw new SerialComException("registerLineEventListener()", "Event listener already exist. Only one listener allowed");
					}else {
						mHandleInfo = mInfo;
					}
					break;
				}
			}
	
			if(handlefound == false) {
				throw new SerialComException("registerLineEventListener()", "Wrong port handle passed for the requested operations");
			}
	
			return mEventCompletionDispatcher.setUpEventLooper(handle, mHandleInfo, eventListener);
		}
	}

	/**
	 * <p>This method destroys complete java and native looper subsystem associated with this particular event listener. This has no
	 * effect on data looper subsystem.</p>
	 * <p>This method is thread safe.</p>
	 * 
	 * @param eventListener instance of class which implemented ISerialComEventListener interface
	 * @return true on success false otherwise
	 * @throws SerialComException if null value is passed in eventListener field
	 * @throws IllegalArgumentException if eventListener is null 
	 */
	public boolean unregisterLineEventListener(final ISerialComEventListener eventListener) throws SerialComException {
		if(eventListener == null) {
			throw new IllegalArgumentException("unregisterLineEventListener(), " + "Argument eventListener can not be null");
		}
		synchronized(lockB) {
			if(mEventCompletionDispatcher.destroyEventLooper(eventListener)) {
				return true;
			}
		}

		return false;
	}


	/**
	 * <p>This pauses delivering events to application. The events kept accumulating in queue.</p>
	 * 
	 * @param eventListener instance of class which implemented ISerialComEventListener interface
	 * @return true on success false otherwise
	 * @throws SerialComException if null is passed for eventListener field
	 * @throws IllegalArgumentException if eventListener is null 
	 */
	public boolean pauseListeningEvents(final ISerialComEventListener eventListener) throws SerialComException {
		if(eventListener == null) {
			throw new IllegalArgumentException("pauseListeningEvents(), " + "Argument eventListener can not be null");

		}
		if(mEventCompletionDispatcher.pauseListeningEvents(eventListener)) {
			return true;
		}

		return false;
	}

	/**
	 * <p>Resume delivering events kept in queue to application.</p>
	 * 
	 * @param eventListener is an instance of class which implements ISerialComEventListener
	 * @return true on success false otherwise
	 * @throws SerialComException if error occurs
	 * @throws IllegalArgumentException if eventListener is null 
	 */
	public boolean resumeListeningEvents(final ISerialComEventListener eventListener) throws SerialComException {
		if(eventListener == null) {
			throw new IllegalArgumentException("resumeListeningEvents(), " + "Argument eventListener can not be null");

		}
		if(mEventCompletionDispatcher.resumeListeningEvents(eventListener)) {
			return true;
		}

		return false;
	}
	
	/**
	 * <p>This method gives more fine tune control to application for tuning performance and behavior of read
	 * operations to leverage OS specific facility for read operation. The read operations can be optimized for
	 * receiving for example high volume data speedily or low volume data but received in burst mode.</p>
	 * 
	 * <p>If more than one client has opened the same port, then all the clients will be affected by new settings.</p>
	 * 
	 * <p>When this method is called application should make sure that previous read or write operation is not in progress.</p>
	 * 
	 * @param handle of the opened port
	 * @param vmin c_cc[VMIN] field of termios structure
	 * @param vtime c_cc[VTIME] field of termios structure (10th of a second)
	 * @param rit ReadIntervalTimeout field of COMMTIMEOUTS structure
	 * @param rttm ReadTotalTimeoutMultiplier field of COMMTIMEOUTS structure
	 * @param rttc ReadTotalTimeoutConstant field of COMMTIMEOUTS structure
	 * @return true on success false otherwise
	 * @throws SerialComException if wrong handle is passed or operation can not be done successfully
	 * @throws IllegalArgumentException if invalid combination of arguments is passed
	 */
	public boolean fineTuneRead(long handle, int vmin, int vtime, int rit, int rttm, int rttc) throws SerialComException {
		boolean handlefound = false;		
		if(osType == SerialComManager.OS_WINDOWS) {
			if((rit < 0) || (rttm < 0) || (rttc < 0)) {
				throw new IllegalArgumentException("fineTuneRead(), " + "Argument(s) rit, rttm and rttc can not be neagative");
			}
		}else {
			if((vmin == 0) && (vtime == 0)) {
				throw new IllegalArgumentException("fineTuneRead(), " + "Invalid combination of vmin and vtime arguments passed");
			}
			if((vmin < 0) || (vtime < 0)) {
				throw new IllegalArgumentException("fineTuneRead(), " + "Argument(s) vmin and vtime can not be negative");
			}
		}

		for(SerialComPortHandleInfo mInfo: mPortHandleInfo){
			if(mInfo.containsHandle(handle)) {
				handlefound = true;
				break;
			}
		}

		if(handlefound == false) {
			throw new SerialComException("fineTuneRead()", "Wrong port handle passed for the requested operations");
		}

		mComPortJNIBridge.fineTuneRead(handle, vmin, vtime, rit, rttm, rttc);
		return true;
	}

	/**
	 * <p>Defines for which line events registered event listener will be called.</p>
	 * 
	 * <p>In future we may shift modifying mask in the native code itself, so as to prevent JNI transitions.
	 * This filters what events should be sent to application. Note that, although we sent only those event
	 * for which user has set mask, however native code send all the events to java layer as of now.</p>
	 * 
	 * @param eventListener instance of class which implemented ISerialComEventListener interface
	 * @return true on success false otherwise
	 * @throws SerialComException if null is passed for listener field or invalid listener is passed
	 * @throws IllegalArgumentException if eventListener is null
	 */
	public boolean setEventsMask(final ISerialComEventListener eventListener, int newMask) throws SerialComException {

		SerialComLooper looper = null;
		ISerialComEventListener mEventListener = null;

		if(eventListener == null) {
			throw new IllegalArgumentException("setEventsMask(), " + "Argument eventListener can not be null");
		}

		for(SerialComPortHandleInfo mInfo: mPortHandleInfo){
			if(mInfo.containsEventListener(eventListener)) {
				looper = mInfo.getLooper();
				mEventListener = mInfo.getEventListener();
				break;
			}
		}

		if(looper != null && mEventListener != null) {
			looper.setEventsMask(newMask);
			return true;
		}else {
			throw new SerialComException("setEventsMask()", "This listener is not registered");
		}
	}

	/**
	 * <p>This method return currently applicable mask for events on serial port.</p>
	 * 
	 * @param eventListener instance of class which implemented ISerialComEventListener interface
	 * @return an integer containing bit fields representing mask
	 * @throws SerialComException if null or wrong listener is passed
	 * @throws IllegalArgumentException if eventListener is null
	 */
	public int getEventsMask(final ISerialComEventListener eventListener) throws SerialComException {
		
		SerialComLooper looper = null;
		ISerialComEventListener mEventListener = null;

		if(eventListener == null) {
			throw new IllegalArgumentException("getEventsMask(), " + "Argument eventListener can not be null");
		}

		for(SerialComPortHandleInfo mInfo: mPortHandleInfo){
			if(mInfo.containsEventListener(eventListener)) {
				looper = mInfo.getLooper();
				mEventListener = mInfo.getEventListener();
				break;
			}
		}

		if(looper != null && mEventListener != null) {
			return looper.getEventsMask();
		}else {
			throw new SerialComException("setEventsMask()", "This listener is not registered");
		}
	}

	/**
	 * <p>Discards data sent to port but not transmitted, or data received but not read. Some device/OS/driver might
	 * not have support for this, but most of them may have.
	 * If there is some data to be pending for transmission, it will be discarded and therefore no longer sent.
	 * If the application wants to make sure that all data has been transmitted before discarding anything, it must
	 * first flush data and then call this method.</p>
	 * 
	 * @param handle of the opened port
	 * @param clearRxPort if true receive buffer will be cleared otherwise will be left untouched 
	 * @param clearTxPort if true transmit buffer will be cleared otherwise will be left untouched
	 * @return true on success false otherwise
	 * @throws SerialComException if invalid handle is passed or operation can not be completed successfully
	 */
	public boolean clearPortIOBuffers(long handle, boolean clearRxPort, boolean clearTxPort) throws SerialComException {
		boolean handlefound = false;
		for(SerialComPortHandleInfo mInfo: mPortHandleInfo){
			if(mInfo.containsHandle(handle)) {
				handlefound = true;
				break;
			}
		}
		if(handlefound == false) {
			throw new SerialComException("clearPortIOBuffers()", "Wrong port handle passed for the requested operations");
		}

		if(clearRxPort == true || clearTxPort == true) {
			mComPortJNIBridge.clearPortIOBuffers(handle, clearRxPort, clearTxPort);
			return true;
		}

		return false;
	}

	/**
	 * <p>Assert a break condition on the specified port for the duration expressed in milliseconds.
	 * If the line is held in the logic low condition (space in UART jargon) for longer than a character 
	 * time, this is a break condition that can be detected by the UART.</p>
	 * 
	 * <p>A "break condition" occurs when the receiver input is at the "space" level for longer than some duration
	 * of time, typically, for more than a character time. This is not necessarily an error, but appears to the
	 * receiver as a character of all zero bits with a framing error. The term "break" derives from current loop
	 * Signaling, which was the traditional signaling used for tele-typewriters. The "spacing" condition of a 
	 * current loop line is indicated by no current flowing, and a very long period of no current flowing is often
	 * caused by a break or other fault in the line.</p>
	 * 
	 * @param handle of the opened port
	 * @param duration the time in milliseconds for which break will be active
	 * @return true on success
	 * @throws SerialComException if invalid handle is passed or operation can not be successfully completed
	 * @throws IllegalArgumentException if duration is zero or negative
	 */
	public boolean sendBreak(long handle, int duration) throws SerialComException {
		boolean handlefound = false;
		for(SerialComPortHandleInfo mInfo: mPortHandleInfo){
			if(mInfo.containsHandle(handle)) {
				handlefound = true;
				break;
			}
		}
		if(handlefound == false) {
			throw new SerialComException("sendBreak()", "Wrong port handle passed for the requested operations");
		}
		
		if((duration < 0) || (duration == 0)) {
			throw new IllegalArgumentException("sendBreak(), " + "Argument duration can not be negative or zero");
		}

		mComPortJNIBridge.sendBreak(handle, duration);
		return true;
	}

	/**
	 * <p>This method gives the number of interrupts on serial line that have occurred. The interrupt count is in following
	 * order in array beginning from index 0 and ending at index 11 :
	 * CTS, DSR, RING, CARRIER DETECT, RECEIVER BUFFER, TRANSMIT BUFFER, FRAME ERROR, OVERRUN ERROR, PARITY ERROR,
	 * BREAK AND BUFFER OVERRUN.</p>
	 * 
	 * <p>Note: It is supported for Unix-like OS only. For other operating systems, this will return 0 for all the indexes.</p>
	 * 
	 * @param handle of the port opened on which interrupts might have occurred
	 * @return array of integers containing values corresponding to each interrupt source
	 * @throws SerialComException if invalid handle is passed or operation can not be completed
	 */
	public int[] getInterruptCount(long handle) throws SerialComException {
		boolean handlefound = false;
		int[] interruptsCount;

		for(SerialComPortHandleInfo mInfo: mPortHandleInfo){
			if(mInfo.containsHandle(handle)) {
				handlefound = true;
				break;
			}
		}
		if(handlefound == false) {
			throw new SerialComException("getInterruptCount()", "Wrong port handle passed for the requested operations");
		}

		interruptsCount = mComPortJNIBridge.getInterruptCount(handle);
		if(interruptsCount == null) {
			throw new SerialComException("getInterruptCount()", "Unknown error occurred !");
		}
		return interruptsCount;
	}

	/**
	 * <p>Gives status of serial port's control lines as supported by underlying operating system.
	 * The sequence of status in returned array is :</p>
	 * 
	 * <p>Linux OS &nbsp;&nbsp;&nbsp;: CTS, DSR, DCD, RI, LOOP, RTS, DTR respectively.</p>
	 * <p>MAC OS X &nbsp;&nbsp;:       CTS, DSR, DCD, RI, 0,    RTS, DTR respectively.</p>
	 * <p>Windows OS :                 CTS, DSR, DCD, RI, 0,    0,   0   respectively.</p>
	 * 
	 * @param handle of the port whose status is to be read
	 * @return status of UART port control lines
	 * @throws SerialComException if invalid handle is passed or operation can not be completed successfully
	 */
	public int[] getLinesStatus(long handle) throws SerialComException {
		boolean handlefound = false;
		int[] status = null;

		for(SerialComPortHandleInfo mInfo: mPortHandleInfo){
			if(mInfo.containsHandle(handle)) {
				handlefound = true;
				break;
			}
		}
		if(handlefound == false) {
			throw new SerialComException("getLinesStatus()", "Wrong port handle passed for the requested operations !");
		}

		status = mComPortJNIBridge.getLinesStatus(handle);
		if(status == null) {
			throw new SerialComException("getLinesStatus()", "Unknown error occurred !");
		}
		return status;
	}

	/**
	 * <p>Get number of bytes in input and output port buffers used by operating system for instance tty buffers
	 * in Unix like systems. Sequence of data in array is : Input buffer byte count, Output buffer byte count.</p>
	 * 
	 * <p>It should be noted that some chipset specially USB to UART converters might have FIFO buffers in chipset
	 * itself. For example FT232R has internal buffers controlled by FIFO CONTROLLERS. For this reason this method
	 * should be tested carefully if application is using USB-UART converters. This is driver and OS specific scenario.</p>
	 * 
	 * @param handle of the opened port for which counts need to be determined
	 * @return array containing number of bytes in input and output buffer
	 * @throws SerialComException if invalid handle is passed or operation can not be completed successfully
	 */
	public int[] getByteCountInPortIOBuffer(long handle) throws SerialComException {
		boolean handlefound = false;
		int[] numBytesInfo;

		for(SerialComPortHandleInfo mInfo: mPortHandleInfo){
			if(mInfo.containsHandle(handle)) {
				handlefound = true;
				break;
			}
		}
		if(handlefound == false) {
			throw new SerialComException("getByteCountInPortIOBuffer()", "Wrong port handle passed for the requested operations");
		}

		numBytesInfo = mComPortJNIBridge.getByteCount(handle);
		if(numBytesInfo == null) {
			throw new SerialComException("getByteCountInPortIOBuffer()", "Could not determine number of bytes in buffer. Please retry !");
		}

		return numBytesInfo;
	}

	/**
	 * <p>This registers a listener who will be invoked whenever a USB device has been plugged or un-plugged in system. This method can 
	 * be used to write auto discovery applications for example when a hardware USB device is added to system, application can automatically 
	 * detect and identify it and launch appropriate service.</p>
	 * 
	 * <p>Application must implement ISerialComHotPlugListener interface and override onHotPlugEvent method. The event value 
	 * SerialComUSB.DEV_ADDED indicates USB device has been added to the system. The event value SerialComUSB.DEV_REMOVED 
	 * indicates USB device has been removed from system.</p>
	 * 
	 * <p>Application can specify the usb device for which callback should be called based on USB VID and USB PID. If the value of 
	 * filterVID is specified however the value of filterPID is constant SerialComUSB.DEV_ANY, then callback will be called 
	 * for USB device which matches given VID and its PID can have any value. If the value of filterPID is specified however the 
	 * value of filterVID is constant SerialComUSB.DEV_ANY, then callback will be called for USB device which matches given PID 
	 * and its VID can have any value.</p>
	 * 
	 * <p>If both filterVID and filterPID are set to SerialComUSB.DEV_ANY, then callback will be called for every USB device.</p>
	 * 
	 * @param hotPlugListener object of class which implements ISerialComHotPlugListener interface
	 * @param filterVID USB vendor ID to match
	 * @param filterPID USB product ID to match
	 * @return true on success
	 * @throws SerialComException if registration fails due to some reason
	 * @throws IllegalArgumentException if hotPlugListener is null
	 */
	public boolean registerHotPlugEventListener(final ISerialComHotPlugListener hotPlugListener, int filterVID, int filterPID) throws SerialComException {
		if(hotPlugListener == null) {
			throw new IllegalArgumentException("registerHotplugEventListener(), " + "Argument hotPlugListener can not be null");
		}
		
		if((filterVID < 0) || (filterPID < 0)) {
			throw new IllegalArgumentException("registerHotplugEventListener(), " + "USB VID or PID can not be negative number(s)");
		}
		
		synchronized(lockB) {
			int ret = mComPortJNIBridge.registerHotPlugEventListener(hotPlugListener, filterVID, filterPID);
			if(ret < 0) {
				throw new SerialComException("registerHotPlugEventListener()", "Failed to register hotplug listener. Please retry !");
			}
			
			boolean added = mHotPlugListenerInfo.add(new SerialComHotPlugInfo(hotPlugListener, ret));
			if(added != true) {
				unregisterHotPlugEventListener(hotPlugListener);
				throw new SerialComException("registerHotPlugEventListener()",  "Could not save info about hot plug listener locally. Please retry registering hot plug listener");
			}
		}

		return true;
	}

	/**
	 * <p>This unregisters listener and terminate native thread used for monitoring specified hot plug events.</p>
	 * 
	 * @param hotPlugListener object of class which implemented ISerialComHotPlugListener interface
	 * @return true on success
	 * @throws SerialComException un-registration fails due to some reason
	 * @throws IllegalArgumentException if hotPlugListener is null
	 */
	public boolean unregisterHotPlugEventListener(final ISerialComHotPlugListener hotPlugListener) throws SerialComException {
		int index = -1;
		SerialComHotPlugInfo mListenerInfo = null;
		if(hotPlugListener == null) {
			throw new IllegalArgumentException("unregisterHotplugEventListener(), " + "Argument hotPlugListener can not be null");
		}
		
		for(SerialComHotPlugInfo mInfo: mHotPlugListenerInfo){
			if(mInfo.getSerialComHotPlugListener() ==  hotPlugListener) {
				index = mInfo.getSerialComHotPlugListenerIndex();
				mListenerInfo = mInfo;
				break;
			}
		}
		if(index == -1) {
			throw new SerialComException("unregisterHotPlugEventListener()", "This listener is not registered");
		}
		
		synchronized(lockB) {
			mComPortJNIBridge.unregisterHotPlugEventListener(index);
			
			/* delete info about this listener from global info arraylist. */
			mHotPlugListenerInfo.remove(mListenerInfo);
		}

		return true;
	}

	/**
	 * <p>This method gives the port name with which given handle is associated. If the given handle is
	 * unknown to SCM library, null is returned. A serial port is known to SCM if it was opened using this library.</p>
	 * 
	 * @param handle for which the port name is to be found
	 * @return port name if port found for given handle or null if not found
	 */
	public String getPortName(long handle) {
		String portName = null;

		for(SerialComPortHandleInfo mInfo: mPortHandleInfo){
			if(mInfo.containsHandle(handle)) {
				portName = mInfo.getOpenedPortName();
				break;
			}
		}
		if(portName == null) {
			return null;
		}

		return portName;
	}

	/**
	 * <p>Send given file using specified file transfer protocol.</p>
	 * 
	 * @param handle of the port on which file is to be sent
	 * @param fileToSend File instance representing file to be sent
	 * @param ftpProto file transfer protocol to use for communication over serial port
	 * @param ftpVariant variant of file transfer protocol to use
	 * @param ftpMode define whether data should be translated(ASCII mode) or not (binary mode)
	 * @return true on success false otherwise
	 * @throws SerialComException if invalid handle is passed
	 * @throws SecurityException If a security manager exists and its SecurityManager.checkRead(java.lang.String) method denies read access to the file
	 * @throws FileNotFoundException if the file does not exist, is a directory rather than a regular file, or for some other reason cannot be opened for reading.
	 * @throws SerialComTimeOutException if timeout occurs as per file transfer protocol
	 * @throws IOException if error occurs while reading data from file to be sent
	 * @throws IllegalArgumentException if fileToSend or ftpProto or ftpVariant or ftpMode argument is null
	 */
	public boolean sendFile(long handle, final java.io.File fileToSend, FTPPROTO ftpProto, FTPVAR ftpVariant, FTPMODE ftpMode) throws SerialComException, SecurityException,
							  FileNotFoundException, SerialComTimeOutException, IOException {
		int protocol = 0;
		int variant = 0;
		int mode = 0;
		boolean handlefound = false;
		boolean result = false;
		
		if(fileToSend == null) {
			throw new IllegalArgumentException("sendFile()" + "Argument fileToSend can not be null");
		}
		if(ftpProto == null) {
			throw new IllegalArgumentException("sendFile() " + "Argument ftpProto can not be null");
		}
		if(ftpVariant == null) {
			throw new IllegalArgumentException("sendFile() " + "Argument ftpVariant can not be null");
		}
		if(ftpMode == null) {
			throw new IllegalArgumentException("sendFile() " + "Argument ftpMode can not be null");
		}

		for(SerialComPortHandleInfo mInfo: mPortHandleInfo){
			if(mInfo.containsHandle(handle)) {
				handlefound = true;
				break;
			}
		}
		if(handlefound == false) {
			throw new SerialComException("sendFile()", "Wrong port handle passed for the requested operations");
		}

		protocol = ftpProto.getValue();
		variant = ftpVariant.getValue();
		mode = ftpMode.getValue();
		if(protocol == 1) {
			if((variant == 0) || (variant == 1)) {
				SerialComXModem xmodem = new SerialComXModem(this, handle, fileToSend, mode);
				result = xmodem.sendFileX();
			}else if(variant == 2) {
				SerialComXModemCRC xmodem = new SerialComXModemCRC(this, handle, fileToSend, mode);
				result = xmodem.sendFileX();
			}else if(variant == 3) {
				SerialComXModem1K xmodem = new SerialComXModem1K(this, handle, fileToSend, mode);
				result = xmodem.sendFileX();
			}else {
			}
		}else if(protocol == 2) {
			
		}else if(protocol == 3) {
			
		}else {
		}
		
		return result;
	}

	/**
	 * <p>Receives file using specified file transfer protocol.</p>
	 * 
	 * @param handle of the port on which file is to be sent
	 * @param fileToReceive File instance representing file to be sent
	 * @param ftpProto file transfer protocol to use for communication over serial port
	 * @param ftpVariant variant of file transfer protocol to use
	 * @param ftpMode define whether data should be translated(ASCII mode) or not (binary mode)
	 * @return true on success false otherwise
	 * @throws SerialComException if invalid handle is passed
	 * @throws SecurityException If a security manager exists and its SecurityManager.checkRead(java.lang.String) method denies read access to the file
	 * @throws FileNotFoundException if the file does not exist, is a directory rather than a regular file, or for some other reason cannot be opened for reading.
	 * @throws SerialComTimeOutException if timeout occurs as per file transfer protocol
	 * @throws IOException if error occurs while reading data from file to be sent
	 * @throws IllegalArgumentException if fileToReceive or ftpProto or ftpVariant or ftpMode argument is null
	 */
	public boolean receiveFile(long handle, final java.io.File fileToReceive, FTPPROTO ftpProto, FTPVAR ftpVariant, FTPMODE ftpMode) throws SerialComException,
								SecurityException, FileNotFoundException, SerialComTimeOutException, IOException {
		int protocol = 0;
		int variant = 0;
		int mode = 0;
		boolean handlefound = false;
		boolean result = false;
		
		if(fileToReceive == null) {
			throw new IllegalArgumentException("receiveFile()" + "Argument fileToReceive can not be null");
		}
		if(ftpProto == null) {
			throw new IllegalArgumentException("receiveFile() " + "Argument ftpProto can not be null");
		}
		if(ftpVariant == null) {
			throw new IllegalArgumentException("receiveFile() " + "Argument ftpVariant can not be null");
		}
		if(ftpMode == null) {
			throw new IllegalArgumentException("receiveFile() " + "Argument ftpMode can not be null");
		}
		
		for(SerialComPortHandleInfo mInfo: mPortHandleInfo){
			if(mInfo.containsHandle(handle)) {
				handlefound = true;
				break;
			}
		}
		if(handlefound == false) {
			throw new SerialComException("receiveFile()", "Wrong port handle passed for the requested operations");
		}

		protocol = ftpProto.getValue();
		variant = ftpVariant.getValue();
		mode = ftpMode.getValue();
		if(protocol == 1) {
			if((variant == 0) || (variant == 1)) {
				SerialComXModem xmodem = new SerialComXModem(this, handle, fileToReceive, mode);
				result = xmodem.receiveFileX();
			}else if(variant == 2) {
				SerialComXModemCRC xmodem = new SerialComXModemCRC(this, handle, fileToReceive, mode);
				result = xmodem.receiveFileX();
			}else if(variant == 3) {
				SerialComXModem1K xmodem = new SerialComXModem1K(this, handle, fileToReceive, mode);
				result = xmodem.receiveFileX();
			}else {
			}
		}else if(protocol == 2) {
			
		}else if(protocol == 3) {
			
		}else {
		}

		return result;
	}
	
	/**
	 * <p>Prepares context and returns an input streams of bytes for receiving data bytes from the 
	 * serial port.</p>
	 * 
	 * <p>A handle can have only one input stream. Application should close stream after it is done.</p>
	 * 
	 * @param handle handle of the opened port from which to read data bytes
	 * @return reference to an object of type SerialComInByteStream
	 * @throws SerialComException if input stream already exist for this handle or invalid handle is passed
	 * @throws IllegalArgumentException if streamMode is null
	 */
	public SerialComInByteStream createInputByteStream(long handle, SMODE streamMode) throws SerialComException {
		boolean handlefound = false;
		SerialComInByteStream scis = null;
		SerialComPortHandleInfo mHandleInfo = null;
		
		if(streamMode == null) {
			throw new IllegalArgumentException("createInputByteStream() " + "Argument streamMode can not be null");
		}

		for(SerialComPortHandleInfo mInfo: mPortHandleInfo){
			if(mInfo.containsHandle(handle)) {
				handlefound = true;
				scis = mInfo.getSerialComInByteStream();
				mHandleInfo = mInfo;
				break;
			}
		}
		if(handlefound == false) {
			throw new SerialComException("createInputByteStream()", "Wrong port handle passed for the requested operations");
		}
		
		if(scis == null) {
			scis = new SerialComInByteStream(this, handle, streamMode);
			mHandleInfo.setSerialComInByteStream(scis);
		}else {
			// if 2nd attempt is made to create already existing input stream, throw exception
			throw new SerialComException("createInputByteStream()", "Input stream already exist for this handle");
		}
		
		return scis;
	}
	
	/**
	 * <p>Prepares context and returns an output streams of bytes for transferring data bytes out of 
	 * serial port.</p>
	 * 
	 * <p>A handle can have only one output stream. Application should close stream after it is done.</p>
	 * 
	 * <p>Using SerialComOutByteStream for writing data while not using SerialComInByteStream for
	 * reading is a valid use case.</p>
	 * 
	 * @param handle handle of the opened port on which to write data bytes
	 * @return reference to an object of type SerialComOutByteStream
	 * @throws SerialComException if output stream already exist for this handle or invalid handle is passed
	 * @throws IllegalArgumentException if streamMode is null
	 */
	public SerialComOutByteStream createOutputByteStream(long handle, SMODE streamMode) throws SerialComException {
		boolean handlefound = false;
		SerialComOutByteStream scos = null;
		SerialComPortHandleInfo mHandleInfo = null;
		
		if(streamMode == null) {
			throw new IllegalArgumentException("createOutputByteStream() " + "Argument streamMode can not be null");
		}

		for(SerialComPortHandleInfo mInfo: mPortHandleInfo){
			if(mInfo.containsHandle(handle)) {
				handlefound = true;
				scos = mInfo.getSerialComOutByteStream();
				mHandleInfo = mInfo;
				break;
			}
		}
		if(handlefound == false) {
			throw new SerialComException("createOutputByteStream()", "Wrong port handle passed for the requested operations");
		}
		
		if(scos == null) {
			scos = new SerialComOutByteStream(this, handle, streamMode);
			mHandleInfo.setSerialComOutByteStream(scos);
		}else {
			// if 2nd attempt is made to create already existing output stream, throw exception
			throw new SerialComException("createOutputByteStream()", "Output stream already exist for this handle");
		}
		
		return scos;
	}
	
	/** Internal use only */
	public void destroyInputByteStream(SerialComInByteStream scis) {
		for(SerialComPortHandleInfo mInfo: mPortHandleInfo){
			if(mInfo.getSerialComInByteStream() == scis) {
				mInfo.setSerialComInByteStream(null);
				break;
			}
		}
	}
	
	/** Internal use only */
	public void destroyOutputByteStream(SerialComOutByteStream scos) {
		for(SerialComPortHandleInfo mInfo: mPortHandleInfo){
			if(mInfo.getSerialComOutByteStream() == scos) {
				mInfo.setSerialComOutByteStream(null);
				break;
			}
		}
	}
	
	/**
	 * <p>Prepares context for excuting IOCTL operations on the given port.</p>
	 * 
	 * @param handle handle of the opened port on which to execute ioctl operations
	 * @return reference to an object of type SerialComIOCTLExecutor on which various ioctl methods can be invoked
	 * @throws SerialComException if invalid handle is passed
	 */
	public SerialComIOCTLExecutor getIOCTLExecutor(long handle) throws SerialComException {
		boolean handlefound = false;
		for(SerialComPortHandleInfo mInfo: mPortHandleInfo){
			if(mInfo.containsHandle(handle)) {
				handlefound = true;
				break;
			}
		}
		if(handlefound == false) {
			throw new SerialComException("getIOCTLExecutor()", "Wrong port handle passed for the requested operations");
		}
		
		if(mSerialComIOCTLExecutor != null) {
			return mSerialComIOCTLExecutor;
		}
		mSerialComIOCTLExecutor = new SerialComIOCTLExecutor(mComPortJNIBridge, mErrMapper);
		return mSerialComIOCTLExecutor;
	}
	
	/**
	 * <p>Checks whether a particular USB device identified by vendor id and product id is connected to 
	 * the system or not.</p>
	 * 
	 * @param vendorID USB-IF vendor ID of the device to match
	 * @param vendorID product ID of the device to match
	 * @return true is device is connected otherwise false
	 * @throws SerialComException if an I/O error occurs.
	 * @throws IllegalArgumentException if productID or vendorID is negative or invalid
	 */
	public boolean isUSBDevConnected(int vendorID, int productID) throws SerialComException {
		if((vendorID < 0) || (vendorID > 0XFFFF)) {
			throw new IllegalArgumentException("listUSBdevicesWithInfo(), " + "Argument vendorID can not be negative or greater tha 0xFFFF");
		}
		if((productID < 0) || (productID > 0XFFFF)) {
			throw new IllegalArgumentException("listUSBdevicesWithInfo(), " + "Argument productID can not be negative or greater tha 0xFFFF");
		}
		
		int ret = mComPortJNIBridge.isUSBDevConnected(vendorID, productID);
		if(ret < 0) {
			throw new SerialComException("isUSBDevConnected()", "Unknown error occurred !");
		}else if(ret == 1) {
			return true;
		}
		return false;
	}
	
	/**
	 * <p>Gives an instance on which vendor specific method calls can be made.</p>
	 * 
	 * @param vendorLibIdentifier one of the constant VLI_xxx_xxx in SerialComVendorLib class
	 * @param libDirectory absolute directory path where vendor library is placed
	 * @return true is device is connected otherwise false
	 * @throws SerialComException if an I/O error occurs.
	 * @throws SerialComLoadException if the library can not be extracted or loaded
	 * @throws IllegalArgumentException if productID or vendorID is negative or invalid
	 */
	public SerialComVendorLib getVendorLibInstance(int vendorLibIdentifier, String libDirectory) throws SerialComException, SerialComLoadException {
		File baseDir = new File(libDirectory.trim());
		if(!baseDir.exists()) {
			throw new SerialComLoadException("getVendorLibInstance()", "given directory does not exist.");
		}
		if(!baseDir.isDirectory()) {
			throw new SerialComLoadException("getVendorLibInstance()", "given directory is not a directory.");
		}
		if(!baseDir.canWrite()) {
			throw new SerialComLoadException("getVendorLibInstance()", "given directory is not writeable (permissions ??).");
		}
		
		if(mSerialComVendorLib != null) {
			return mSerialComVendorLib.getVendorLibInstance(vendorLibIdentifier, baseDir);
		}
		mSerialComVendorLib = new SerialComVendorLib();
		return mSerialComVendorLib.getVendorLibInstance(vendorLibIdentifier, baseDir);
	}
	
}
