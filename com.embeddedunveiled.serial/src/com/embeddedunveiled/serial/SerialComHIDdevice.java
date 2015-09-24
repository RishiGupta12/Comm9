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

package com.embeddedunveiled.serial;


/**
 * <p>Represents a HID device with information about it.</p>
 */
public final class SerialComHIDdevice {

	private String transport;
	private String deviceNode;
	private String idVendor;
	private String idProduct;
	private String serial;
	private String product;
	private String manufacturer;

	/**
	 * <p>Construct and allocates a new SerialComHIDdevice object with given details.</p>
	 * 
	 * @param transport communication medium USB or Bluetooth this devices uses.
	 * @param deviceNode identifier that can be used to open this device.
	 * @param idVendor USB-IF unique vendor id of this device.
	 * @param idProduct USB product id of this device.
	 * @param serial serial number of this device.
	 * @param product product identifier/description of this device.
	 * @param manufacturer company manufacturing of this device.
	 * @param deviceNode 
	 * @throws SerialComException if the object can not be constructed.
	 */
	public SerialComHIDdevice(String transport, String deviceNode, String idVendor, String idProduct,
			String serial, String product, String manufacturer) {
		this.transport = transport;
		this.deviceNode = deviceNode;
		this.idVendor = idVendor;
		this.idProduct = idProduct;
		this.serial = serial;
		this.product = product;
		this.manufacturer = manufacturer;
	}

	/** 
	 * <p>Returns USB or Bluetooth i.e. transport this device uses.</p>
	 * 
	 * @return USB or Bluetooth string whichever is applicable for this device.
	 */
	public String getTransportType() {
		return transport;
	}

	/** 
	 * <p>Returns device node representing this device in system.</p>
	 * 
	 * @return string device node.
	 */
	public String getDeviceNode() {
		return deviceNode;
	}

	/** 
	 * <p>Retrieves the vendor id of the USB device.</p>
	 * 
	 * @return vendor id of the USB device.
	 * @throws NumberFormatException if the USB vendor id hex string can not be converted into numerical representation.
	 */
	public int getVendorId() {
		if("---".equals(idVendor)) {
			return 0;
		}
		return (int) SerialComUtil.hexStrToLongNumber(idVendor);
	}

	/** 
	 * <p>Retrieves the product id of the USB device.</p>
	 * 
	 * @return product id of the USB device.
	 * @throws NumberFormatException if the USB product id hex string can not be converted into numerical representation.
	 */
	public int getProductId() {
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
	 * <p>Prints information about this device on console.</p>
	 */
	public void dumpDeviceInfo() {
		System.out.println(
				"Transport : " + transport +
				"\nDevice node : " + deviceNode +
				"\nVendor id : 0x" + idVendor + 
				"\nProduct id : 0x" + idProduct + 
				"\nSerial number : " + serial + 
				"\nProduct : " + product + 
				"\nManufacturer : " + manufacturer + "\n");
	}
}
