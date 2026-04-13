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

/** Sammlung von mathematischen Hilfsfunktionen. */

public class MathAddon {

	/**
	 * Rundet einen Wert auf eine bestimmte Anzahl von Nachkommastellen.
	 *
	 * @param val
	 *            Zahlenwert mit Nachkommastellen
	 * @param digits
	 *            Anzahl der Nachkommastellen nach Verarbeitung
	 *
	 * @return gerundeter Zahlenwert
	 */
	public static double roundscale(double val, int digits) {
		int tmp_digits = digits;
		int pow = 1;
		while (tmp_digits > 0) {
			pow *= 10;
			tmp_digits--;
		}
		val *= pow;
		val = round(val);
		val /= pow;
		return val;
	}

	/*
	 * public static String clipscale(String val, int digits) { int pos =
	 * val.indexOf("."); if (pos == -1) { val = clipscale(val + ".", digits); }
	 * else { int clip = pos + (digits + 1); if (clip <= val.length()) { val =
	 * val.substring(0, clip); } int add = pos + digits; while (add >=
	 * val.length()) { val += "0"; } } return val; }
	 *
	 * private static int round(float a) { return (int) Math.floor(a + 0.5f); }
	 */

	private static long round(double a) {
		return (long) Math.floor(a + 0.5d);
	}

	/**
	 * Wandelt ein Byte in Integer im Zahlenbereich von 0 bis 255 um.
	 *
	 * @param b
	 *            Zahlenwert als Byte
	 *
	 * @return Zahlenwert als Integer
	 */

	public static int byte2int(byte b) {
		return b & 0xFF;

	}

	/*
	 *
	 * public static double exponent(double x, int N) { if (N == 0) { return 1;
	 * } else { double y = 1; while (N > 0) { y *= x; N--; } return y; } }
	 *
	 * public static int factorial(int N) { if (N < 2) { return 1; } else { int
	 * y = 1; int n = 1; while (N > 0) { y *= n; n++; N--; } return y; } }
	 *
	 *
	 * public static double exp(double x) { int N = 10; double y = 1; for (int i
	 * = 1; i < N; i++) { y += exponent(x, i) / factorial(i); } return y; }
	 *
	 * public static void main(String[] args) { double ex = exp(0.5); int bla =
	 * 1;
	 *
	 *
	 * }
	 */

}
