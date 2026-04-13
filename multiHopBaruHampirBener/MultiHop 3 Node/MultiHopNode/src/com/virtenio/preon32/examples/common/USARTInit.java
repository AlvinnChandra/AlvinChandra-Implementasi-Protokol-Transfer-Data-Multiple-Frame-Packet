/*
 * Copyright (c) 2011., Virtenio GmbH
 * All rights reserved.
 *
 * Commercial software license.
 * Only for test and evaluation purposes.
 * Use in commercial products prohibited.
 * No distribution without permission by Virtenio.
 * Ask Virtenio for other type of license at info@virtenio.de
 *
 * Kommerzielle Softwarelizenz.
 * Nur zum Test und Evaluierung zu verwenden.
 * Der Einsatz in kommerziellen Produkten ist verboten.
 * Ein Vertrieb oder eine Veröffentlichung in jeglicher Form ist nicht ohne Zustimmung von Virtenio erlaubt.
 * Für andere Formen der Lizenz nehmen Sie bitte Kontakt mit info@virtenio.de auf.
 */

package com.virtenio.preon32.examples.common;

import com.virtenio.driver.usart.NativeUSART;
import com.virtenio.driver.usart.USARTParams;
import com.virtenio.io.Console;

import java.io.InputStream;
import java.io.OutputStream;

/** Beispiele einer Initialisierung der USART Schnittstelle. */
public class USARTInit {

	protected int instanceID;
	protected USARTParams params;
	protected NativeUSART usart;
	protected InputStream in;
	protected OutputStream out;

	/** @return Gibt den InputStream der USART-Schnittstelle zurück */

	public InputStream getInputStream() {
		return in;
	}

	/** @return Gibt den OutputStream der USART-Schnittstelle zurück */

	public OutputStream getOutputStream() {
		return out;
	}

	/** Der Konstruktor führt auch eine Initialisierung durch. */

	public USARTInit() {
		startup();
	}

	protected void startup() {

	}

	public void common_init() {
		Console console = new Console();

		instanceID = console.readInt("Geben Sie den USART-Port an (0=EXT, 1=USB)", 0, 1);
		int config = console.readInt("Geben Sie den Baudrate an (\n"
				+ "0=9600, 1=19200, 2=38400, 3=115200, 4=250000)", 0, 4);

		switch (config) {
			case 0:
				params = USARTConstants.PARAMS_09600;
				break;
			case 1:
				params = USARTConstants.PARAMS_19200;
				break;
			case 2:
				params = USARTConstants.PARAMS_38400;
				break;
			case 3:
				params = USARTConstants.PARAMS_115200;
				break;
			case 4:
				params = USARTConstants.PARAMS_250000;
				break;
		}
		//
		common_init(instanceID, params);
	}

	/**
	 * Initialisierung einer USART Schnittstelle
	 *
	 * @param instanceID
	 *            Nummer der Instanz der USART-Schnittstelle
	 * @param p
	 *            Parameter zur Initialisierung
	 */
	public void common_init(int instanceID, USARTParams p) {
		//
		usart = NativeUSART.getInstance(instanceID);
		try {
			usart.open(p);
		} catch (Exception e) {
			System.out.println(" Error open(params)");
		}
		in = usart.getInputStream();
		out = usart.getOutputStream();
	}

	public void common_shutdown() {
		try {
			usart.close();
		} catch (Exception e) {
			System.out.println(" Error usart.close()");
		}
	}

}
