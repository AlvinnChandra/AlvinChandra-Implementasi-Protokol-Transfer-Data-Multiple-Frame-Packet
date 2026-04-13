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

import java.util.Vector;

/**
 * Umsetzung einer FIFO Implementierung mit einem Vector.
 *
 * @param <T>
 */

public class FiFoVector<T> implements FiFo<T> {

	private final int size_max;
	private final Vector<T> buffer;

	// ///////////////////////////////////////////////////////////////////////
	// Constructor
	// ///////////////////////////////////////////////////////////////////////

	public FiFoVector(int size_max) {
		this.size_max = size_max;
		this.buffer = new Vector<T>(size_max, 1);
	}

	@Override
	public synchronized void add(T element) throws FiFo.FullException {
		if (buffer.size() >= size_max) {
			throw new FiFo.FullException();
		} else {
			buffer.addElement(element);
		}
	}

	@Override
	public synchronized T remove() throws FiFo.EmptyException {
		if (buffer.size() > 0) {
			T element = buffer.elementAt(0);
			buffer.removeElementAt(0);
			return element;
		} else {
			throw new FiFo.EmptyException();
		}
	}

	@Override
	public synchronized boolean isFull() {
		return buffer.size() == size_max;
	}

	@Override
	public boolean isEmpty() {
		return buffer.isEmpty();
	}

}
