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

/**
 * <p>This interface represents Completion handler in our proactor design pattern.</p>
 */
public interface ISerialComDataListener {

	/**
	 * <p>The class implementing this interface is expected to override onNewSerialDataAvailable() method.
	 * This method gets called from the looper thread associated with the corresponding listener (handler).</p>
	 * 
	 * <p>The listener can extract data bytes from the event object passed by calling getDataBytes() method
	 * which returns array of bytes.</p>
	 * 
	 * <p>In Linux, by default, the listener is called for every new byte available. This behavior can be modified by 
	 * using available fineTuneRead() API for Linux.</p>
	 */
	public abstract void onNewSerialDataAvailable(SerialComDataEvent dataEvent);

	/**
	 * <p>This methods helps in creating fault-tolerant and recoverable application design in case
	 * unexpected situations like serial port removal, bug encountered in OS or driver during operation
	 * occurs. In a nutshell situations which are outside the scope of scm may be handled using this method.</p>
	 * 
	 * <p>Developer can implement different recovery policies like unregister listener, close com port
	 * and then open and register listener again. Another policy might be to send email to system 
	 * administrator so that he can take appropriate actions to recover from situation.</p>
	 */
	public abstract void onDataListenerError(int errorNum);

}