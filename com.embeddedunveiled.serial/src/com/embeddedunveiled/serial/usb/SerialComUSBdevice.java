/*
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

package com.embeddedunveiled.serial.usb;

import com.embeddedunveiled.serial.SerialComException;
import com.embeddedunveiled.serial.SerialComUtil;

/**
 * <p>Represents a USB device with information about it.</p>
 * 
 * @author Rishi Gupta
 */
public final class SerialComUSBdevice {

	private String idVendor;
	private String idProduct;
	private String serial;
	private String product;
	private String manufacturer;
	private String busNumber;
	private String devNumber;

	/**
	 * <p>Construct and allocates a new SerialComUSBdevice object with given details.</p>
	 * 
	 * @param idVendor USB-IF unique vendor id of this device.
	 * @param idProduct USB product id of this device.
	 * @param serial serial number of this device.
	 * @param product product identifier/description of this device.
	 * @param manufacturer company manufacturing of this device.
	 * @param busNumber usb bus number on which this device is connected.
	 * @param devNumber usb device number as assigned by operating system.
	 * @throws SerialComException if the object can not be constructed.
	 */
	public SerialComUSBdevice(String idVendor, String idProduct, String serial, String product, 
			String manufacturer, String busNumber, String devNumber) {
		this.idVendor = idVendor;
		this.idProduct = idProduct;
		this.serial = serial;
		this.product = product;
		this.manufacturer = manufacturer;
		this.busNumber = busNumber;
		this.devNumber = devNumber;
	}

	/** 
	 * <p>Retrieves the vendor id of the USB device.</p>
	 * 
	 * @return vendor id of the USB device.
	 * @throws NumberFormatException if the USB vendor id hex string can not be converted into 
	 *          numerical representation.
	 */
	public int getVendorID() {
		if("---".equals(idVendor)) {
			return 0;
		}
		return (int) SerialComUtil.hexStrToLongNumber(idVendor);
	}

	/** 
	 * <p>Retrieves the product id of the USB device.</p>
	 * 
	 * @return product id of the USB device.
	 * @throws NumberFormatException if the USB product id hex string can not be converted into 
	 *          numerical representation.
	 */
	public int getProductID() {
		if("---".equals(idProduct)) {
			return 0;
		}
		return (int) SerialComUtil.hexStrToLongNumber(idProduct);
	}

	/** 
	 * <p>Retrieves the serial number string of the USB device.</p>
	 * 
	 * @return serial number string of the USB device.
	 */
	public String getSerialNumber() {
		return serial;
	}

	/** 
	 * <p>Retrieves the product string of the USB device.</p>
	 * 
	 * @return serial number string of the USB device.
	 */
	public String getProductString() {
		return product;
	}

	/** 
	 * <p>Retrieves the manufacturer string of the USB device.</p>
	 * 
	 * @return serial number string of the USB device.
	 */
	public String getManufacturerString() {
		return manufacturer;
	}

	/** 
	 * <p>Retrieves the USB bus number on which this USB device is connected.</p>
	 * 
	 * @return bus number of which this USB device is connected.
	 * @throws NumberFormatException if the USB bus number string can not be converted into 
	 *          numerical representation.
	 */
	public int getBusNumber() {
		if("---".equals(busNumber)) {
			return 0;
		}
		return Integer.parseInt(busNumber, 10);
	}

	/** 
	 * <p>Retrieves the USB device number as assigned by operating system.</p>
	 * 
	 * @return USB device number as assigned by operating system.
	 * @throws NumberFormatException if the USB device number string can not be converted into 
	 *          numerical representation.
	 */
	public int getUSBDeviceNumberInSystem() {
		if("---".equals(devNumber)) {
			return 0;
		}
		return Integer.parseInt(devNumber, 10);
	}

	/** 
	 * <p>Prints information about this device on console.</p>
	 */
	public void dumpDeviceInfo() {
		System.out.println(
				"\nVendor id : 0x" + idVendor + 
				"\nProduct id : 0x" + idProduct + 
				"\nSerial number : " + serial + 
				"\nProduct : " + product + 
				"\nManufacturer : " + manufacturer +
				"\nUSB bus number : " + busNumber + 
				"\nUSB Device number : " + devNumber);
	}
}
