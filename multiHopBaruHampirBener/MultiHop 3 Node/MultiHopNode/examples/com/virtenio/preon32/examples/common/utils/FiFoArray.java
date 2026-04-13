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

/**
 * Umsetzung einer FIFO Implementierung mit einem Array.
 *
 * @param <T>
 */

public class FiFoArray<T> implements FiFo<T> {

	private int pos = 0;
	private int level = 0;
	private final Object[] buffer;

	/** @param size_max */
	public FiFoArray(int size_max) {
		this.buffer = new Object[size_max];
	}

	@Override
	public synchronized void add(T element) throws FiFo.FullException {
		if (buffer.length <= level) {
			throw new FiFo.FullException();
		} else {
			buffer[pos] = element;
			level++;
			pos = (pos + 1) % buffer.length;
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public synchronized T remove() throws FiFo.EmptyException {
		if (level > 0) {
			int pp = ((pos + buffer.length) - level) % buffer.length;
			T element = (T) buffer[pp];
			level--;
			return element;
		} else {
			throw new FiFo.EmptyException();
		}
	}

	@Override
	public synchronized boolean isFull() {
		return buffer.length == level;
	}

	@Override
	public boolean isEmpty() {
		return level == 0;
	}

	/*
	 * public static void main(String[] args) throws FiFo.FullException,
	 * FiFo.EmptyException {
	 *
	 * FiFo_Array<String> fifo = new FiFo_Array<String>(3); fifo.add("s1");
	 * fifo.add("s2"); fifo.add("s3");
	 *
	 * String s1 = fifo.remove(); fifo.add("s4"); String s2 = fifo.remove();
	 * String s3 = fifo.remove(); fifo.add("s5"); String s4 = fifo.remove();
	 * fifo.add("s6"); String s5 = fifo.remove(); String s6 = fifo.remove();
	 * fifo.add("s7"); fifo.add("s8"); fifo.add("s9"); fifo.add("s10");
	 *
	 * int bb = 1; }
	 */
}