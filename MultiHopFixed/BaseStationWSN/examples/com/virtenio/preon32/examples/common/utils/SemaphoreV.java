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

/** Einfache Umsetzung einer Semaphore */

public class SemaphoreV {

	private final Object lockSema = new Object(); // für acquire & release
													// zuständig
	private final Object lockPermits = new Object();
	private int permits = 0;

	/** Constructor der Klasse */

	public SemaphoreV() {
		this.permits = 0;
	}

	/**
	 * Constructor der Klasse
	 *
	 * @param permits
	 *            Anzahl der initialen Berechtigungen
	 */

	public SemaphoreV(int permits) {
		this.permits = permits;
	}

	/** @return Wieviele Durchgänge sind frei */

	public int availablePermits() {
		int p_ = 0;
		synchronized (lockPermits) {
			p_ = permits;
		}
		return p_;
	}

	/**
	 * Anfordern eines Durchgangs.
	 *
	 * @throws InterruptedException
	 */

	public void acquire() throws InterruptedException {
		acquire(1);
	}

	/**
	 * Anfordern von mehreren Durchgängen.
	 *
	 * @param permits
	 *            Anzahl der Durchgänge
	 *
	 * @throws InterruptedException
	 */

	public void acquire(int permits) throws InterruptedException {
		synchronized (lockSema) {
			if (availablePermits() >= permits) {
				// Es gibt permits
				synchronized (lockPermits) {
					this.permits = this.permits - permits;
				}
			} else {
				lockSema.wait(); // Warte
				acquire(permits); // Neuer versuch
			}
		} // end lockSema
	}

	/** Freigabe eines Durchgangs. */

	public void release() {
		release(1);
	}

	/**
	 * Freigabe von mehreren Durchgangen.
	 *
	 * @param permits
	 *            Anzahl der Durchgänge
	 */

	public void release(int permits) {
		synchronized (lockSema) {
			synchronized (lockPermits) {
				this.permits = this.permits + permits;
			}
			lockSema.notifyAll();
		} // end lockSema
	}

}
