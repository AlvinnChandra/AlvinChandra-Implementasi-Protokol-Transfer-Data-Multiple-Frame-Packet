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

package com.virtenio.preon32.examples.common.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/** Ansammlung von Funktionen für Strings. */

public class StringAddon {

	/**
	 * Same as {@link #split(String, String)} with one char as delimiter.
	 *
	 * @see #split(String, String)
	 */
	public static List<String> split(String str, char key) {
		return split(str, "" + key);
	}

	/**
	 * Zerlegt einen String in eine Menge von Strings.
	 *
	 * @param str
	 *            String, der zerlegt werden soll
	 * @param key
	 *            Zeichen, mit dem der String zerlegt werden soll
	 *
	 * @return List aus Strings, die durch den Key abgeteilt werden
	 */
	public static List<String> split(String str, String key) {
		ArrayList<String> vec = new ArrayList<String>();
		StringTokenizer tokenizer = new StringTokenizer(str, key);
		while (tokenizer.hasMoreTokens()) {
			vec.add(tokenizer.nextToken());
		}
		return vec;
	}

	/**
	 * Wandelt eine Array aus bytes in einen String um.
	 *
	 * @param dg
	 *            Arry aus bytes, dass in einen String umgewandelt werden soll.
	 *
	 * @return String, der aus Array von bytes entstanden ist.
	 */
	public static String ByteArrayToString(byte[] dg) {
		StringBuilder sb = new StringBuilder(3 * dg.length);
		for (int i = 0; i < dg.length; i++) {
			String tmp = Integer.toHexString(dg[i]);
			if (tmp.length() == 1) {
				tmp = "0" + tmp;
			}
			sb.append(" " + tmp);
		}
		sb.deleteCharAt(0);
		return sb.toString();
	}
}
