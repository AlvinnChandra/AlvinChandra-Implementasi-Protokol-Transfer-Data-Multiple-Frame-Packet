import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Lock;
import java.io.IOException;
import java.io.OutputStream;

import com.virtenio.driver.usart.NativeUSART;
import com.virtenio.driver.usart.USART;
import com.virtenio.driver.usart.USARTParams;
import com.virtenio.io.ChannelBusyException;
import com.virtenio.io.NoAckException;
import com.virtenio.misc.StringUtils;
import com.virtenio.preon32.examples.common.USARTConstants;
import com.virtenio.preon32.examples.common.RadioInit;
import com.virtenio.radio.RadioDriverException;
import com.virtenio.radio.ieee_802_15_4.Frame;
import com.virtenio.radio.ieee_802_15_4.FrameIO;
import com.virtenio.radio.ieee_802_15_4.RadioDriver;
import com.virtenio.radio.ieee_802_15_4.RadioDriverFrameIO;

import com.virtenio.driver.device.at86rf231.*;

import com.virtenio.vm.Time;

public class progBS {

	int channel         = 24;
	int panID           = 0xCAFE;
	int broadcastAddress = 0xFFFF;

	int[] nodeAddress   = {0xBABE, 0xAFFE, 0xBAFE, 0xBEBA};
	int[] NODE_ADDRi    = {47806, 45054, 47870, 48826};

	int myAddress       = nodeAddress[0];

	// Flag untuk menandakan node sudah membalas/belum
	int[]  bcTableStatus = {0, 0, 0, 0};
	
	
	//Menyimpan akumulasi RTT tiap node untuk menghitung koreksi waktu sinkronisasi.
	long[] rttTable      = {0, 0, 0, 0};

	AT86RF231 radio;
	FrameIO fio;
	USART usart;

	//GMT +7 = 7 * 60 * 60 * 1000ms
	long hour7 = 25200000;

	volatile boolean stop = false;

	//Saluran output untuk menulis ke PC lewat USART
	private static OutputStream out;

	int sequenceNumber;

	// =========================================================
	// TAMBAHAN: Tracking SN per node untuk ACK & retransmit
	// =========================================================
	// Menyimpan SN tertinggi yang sudah diterima dari tiap node sensor (index 1..3)
	int[] highestReceivedSN = {-1, -1, -1, -1};
	// Menyimpan SN tertinggi yang sudah di-ACK ke tiap node sensor
	int[] lastAckedSN       = {-1, -1, -1, -1};
	// Lock untuk akses highestReceivedSN dan lastAckedSN
	Object ackLock = new Object();
	// =========================================================

	public void initializeRadio() throws Exception {
		radio = RadioInit.initRadio();
		radio.setChannel(channel);
		radio.setPANId(panID);
		radio.setShortAddress(myAddress);
	}
	
	public void initializeFrameIO() throws Exception {
		final RadioDriver radioDriver = new AT86RF231RadioDriver(radio);
		fio = new RadioDriverFrameIO(radioDriver);
	}

	public void resetSequenceNumber() throws Exception {
		sequenceNumber = 0;
	}

	public void useUSART() throws Exception {
	    USARTParams params = USARTConstants.PARAMS_115200;
	    NativeUSART nativeUSART = NativeUSART.getInstance(0);
	    try {
	        nativeUSART.close();
	        nativeUSART.open(params);
	        usart = nativeUSART;
	    } catch (Exception e) {
	        usart = null;
	    }
	}

	public void resetStatusBC() throws Exception {
		for (int i = 0; i < bcTableStatus.length; i++) {
			bcTableStatus[i] = 0;
		}
	}

	public void initrttTable() throws Exception {
		for (int i = 0; i < rttTable.length; i++) {
			rttTable[i] = 0;
		}
	}

	public int indexOf(long srcAddr) throws Exception {
		int idx = -1;
		for (int i = 0; i < NODE_ADDRi.length; i++) {
			if (NODE_ADDRi[i] == srcAddr) {
				idx = i;
			}
		}
		return idx;
	}

	public void broadCast(String mesg, int sn) throws Exception {
		try {
			Frame frame = new Frame(Frame.TYPE_DATA | Frame.ACK_REQUEST
					| Frame.DST_ADDR_16 | Frame.INTRA_PAN | Frame.SRC_ADDR_16);
			frame.setSrcAddr(myAddress);
			frame.setSrcPanId(panID);
			frame.setDestAddr(broadcastAddress);
			frame.setDestPanId(panID);
			frame.setSequenceNumber(sn);
			String message = mesg + " " + Time.currentTimeMillis();
			frame.setPayload(message.getBytes());
			radio.setState(AT86RF231.STATE_TX_ARET_ON);
			radio.transmitFrame(frame);
		} catch (RadioDriverException e) {
		} catch (NoAckException e) {
		} catch (ChannelBusyException e) {
		}
	}

	public void sendTONODE(final FrameIO fio, final String msg, final int sn, final long destADDR) throws Exception {
		try {
			Frame frame = new Frame(Frame.TYPE_DATA | Frame.ACK_REQUEST
					| Frame.DST_ADDR_16 | Frame.INTRA_PAN | Frame.SRC_ADDR_16);
			frame.setSrcAddr(myAddress);
			frame.setSrcPanId(panID);
			frame.setDestAddr(destADDR);
			frame.setDestPanId(panID);
			frame.setSequenceNumber(sn);
			String message = msg + " " + Time.currentTimeMillis();
			frame.setPayload(message.getBytes());
			radio.setState(AT86RF231.STATE_TX_ARET_ON);
			fio.transmit(frame);
		} catch (RadioDriverException e) {
		} catch (NoAckException e) {
		} catch (ChannelBusyException e) {
		} catch (IOException e) {
		}
	}

	public void dispatch(final Frame frame, final long t4) throws Exception {
	    int code = 0; 
	    int idx = -1; 
	    String reply;
	    
	    try {
	        out = usart.getOutputStream();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	    
	    if (frame != null) {
	        byte[] payloadBytes = frame.getPayload();
	        String pesanDiterima = new String(payloadBytes, 0, payloadBytes.length);
	        String[] splitPesan = StringUtils.split(pesanDiterima, " ");
	        String hex_addr = Integer.toHexString((int) frame.getSrcAddr());
	        
	        try { 
	            idx = indexOf(frame.getSrcAddr()); 
	        } catch (Exception e) {}

	        if (splitPesan[0].equalsIgnoreCase("001")) {
	            code = 1;
	        } else if (splitPesan[0].equalsIgnoreCase("010")) {
	            code = 2;
	        } else if (splitPesan[0].equalsIgnoreCase("011")) {
	            code = 3;
	        } else if (splitPesan[0].equalsIgnoreCase("100")) {
	            code = 4;
	        }

	        switch (code) {
	            case 1: {
	                long t1 = Long.parseLong(splitPesan[1]);
	                long t2 = Long.parseLong(splitPesan[2]);
	                long t3 = Long.parseLong(splitPesan[3]);
	                long RTT = t4 - t1 - (t3 - t2);
	                rttTable[idx] = rttTable[idx] + RTT;
	                reply = "#HELLO: " + hex_addr + " " + stringFormatTime.SFFull(t3) + " | RTT: " + RTT + "#";
	                try {
	                    out.write(reply.getBytes(), 0, reply.length());
	                } catch (IOException e) { e.printStackTrace(); }
	                break;
	            }
	            case 2: {
	                long t3 = Long.parseLong(splitPesan[3]);
	                reply = "#SET:" + hex_addr + " change to " + stringFormatTime.SFFull(t3) + "#";
	                try {
	                    out.write(reply.getBytes(), 0, reply.length());
	                } catch (IOException e) { e.printStackTrace(); }
	                break;
	            }
	            case 3: {
	                long t3 = Long.parseLong(splitPesan[3]);
	                reply = "#NOW: " + stringFormatTime.SFFull(t3) + " at " + hex_addr + "#";
	                try {
	                    out.write(reply.getBytes(), 0, reply.length());
	                } catch (IOException e) { e.printStackTrace(); }
	                break;
	            }
	            case 4: {
	                break;
	            }
	        }
	    }
	}

	public void recvReply(final FrameIO fio, final long t1) throws Exception {
		new Thread() {
			public void run() {
				try {
					out = usart.getOutputStream();
				} catch (Exception e) {
					e.printStackTrace();
					
				}
				Frame frame = new Frame();
				int idx = -1;
				int count = 0; 
				int snSebelumnya = -1;
				while ((count < bcTableStatus.length) && ((Time.currentTimeMillis() - t1) <= 3000)) {
					try {
						radio.setState(AT86RF231.STATE_RX_AACK_ON);
						fio.receive(frame);
						long t4 = Time.currentTimeMillis();
						idx = indexOf(frame.getSrcAddr());
						if (bcTableStatus[idx] == 0) {
							bcTableStatus[idx] = 1;
							if (frame.getSequenceNumber() != snSebelumnya) {
								dispatch(frame, t4);
							}
							count++;
						}
					} catch (Exception e) { 
						e.printStackTrace(); 
					}
					snSebelumnya = frame.getSequenceNumber();
				}
			}
		}.start();
	}

	public void recvSense(final FrameIO fio) {
		new Thread() {
			public void run() {
				final Lock lock = new ReentrantLock();
				try {
					useUSART();
					out = usart.getOutputStream();
				} catch (Exception e) {
					e.printStackTrace();
				}
				Frame frame = new Frame();
				int snSebelumnya = -1;
				while (!stop) {
					try {
						radio.setState(AT86RF231.STATE_RX_AACK_ON);
						fio.receive(frame);
					} catch (RadioDriverException e) {
						e.printStackTrace();
					} catch (Exception e) { 
						e.printStackTrace(); 
					}

					if ((frame != null) && (frame.getSequenceNumber() != snSebelumnya)) {
						try {
							byte[] payloadBytes= frame.getPayload();
							String pesanDiterima = "#" + new String(payloadBytes, 0, payloadBytes.length) + "\n" + "#";
							lock.lock();
							try {
								out.write(pesanDiterima.getBytes());
								out.flush();
							} finally { 
								lock.unlock(); 
							}

							// =========================================================
							// TAMBAHAN: Kirim CumACK setelah frame SENSE diterima
							// =========================================================
							byte[] rawPayload = frame.getPayload();
							String rawStr = new String(rawPayload, 0, rawPayload.length);
							if (rawStr.startsWith("SENSE")) {
								int receivedSN = frame.getSequenceNumber();
								int nodeIdx = -1;
								try {
									nodeIdx = indexOf(frame.getSrcAddr());
								} catch (Exception e) {}

								if (nodeIdx >= 0) {
									sendCumACK(fio, receivedSN, frame.getSrcAddr(), nodeIdx);
								}
							}
							// =========================================================

						} catch (IOException e) { 
							e.printStackTrace();
						} catch (NegativeArraySizeException e) { 
							e.printStackTrace();
						} catch (StringIndexOutOfBoundsException e) { 
							e.printStackTrace(); 
						}
						snSebelumnya = frame.getSequenceNumber();
					}
				}
			}
		}.start();
	}


	// =========================================================
	// TAMBAHAN: Kirim CumACK ("110 <ackedSN>") ke node tertentu
	// =========================================================

	// Helper: cek apakah snA "lebih baru" dari snB dalam ruang lingkup 0-255
	// Menggunakan half-window (128) untuk deteksi wrap-around
	private boolean isNewerSN(int snA, int snB) {
		if (snB == -1) return true; // belum pernah ada ACK sebelumnya
		int diff = (snA - snB + 256) % 256;
		// diff 1..127 berarti snA lebih baru, diff 128..255 berarti snA lebih lama
		return (diff > 0 && diff < 128);
	}

	// Helper: hitung jarak maju dari snFrom ke snTo dalam ruang lingkup 0-255
	private int snDistance(int snFrom, int snTo) {
		return (snTo - snFrom + 256) % 256;
	}

	public void sendCumACK(final FrameIO fio, final int ackedSN, final long destAddr, final int nodeIdx) {
		new Thread() {
			public void run() {
				boolean shouldSendRetransmit = false;
				int retransmitStart = -1;
				int retransmitEnd   = -1;

				synchronized (ackLock) {
					// Hanya proses jika SN ini benar-benar lebih baru (handle wrap-around)
					if (!isNewerSN(ackedSN, lastAckedSN[nodeIdx])) {
						return;
					}

					// Cek apakah ada gap SN yang perlu di-retransmit
					int expectedNext;
					if (lastAckedSN[nodeIdx] == -1) {
						// Pertama kali: tidak ada gap, langsung ACK
						expectedNext = ackedSN;
					} else {
						expectedNext = (lastAckedSN[nodeIdx] + 1) % 256;
					}

					int gap = snDistance(expectedNext, ackedSN);
					if (gap > 0) {
						// Ada gap: minta retransmit dari expectedNext s.d. ackedSN - 1
						retransmitStart = expectedNext;
						retransmitEnd   = (ackedSN - 1 + 256) % 256;
						shouldSendRetransmit = true;
						System.out.println("Gap SN terdeteksi: " + retransmitStart + " s.d. " + retransmitEnd);
					}

					lastAckedSN[nodeIdx] = ackedSN;
				}

				// Kirim retransmit request di luar lock jika ada gap
				if (shouldSendRetransmit) {
					sendRetransmitRequest(fio, retransmitStart, retransmitEnd, destAddr);
				}

				// Kirim CumACK ke node
				try {
					String msg = "110 " + ackedSN;
					Frame ackFrame = new Frame(Frame.TYPE_DATA | Frame.ACK_REQUEST
							| Frame.DST_ADDR_16 | Frame.INTRA_PAN | Frame.SRC_ADDR_16);
					ackFrame.setSrcAddr(myAddress);
					ackFrame.setSrcPanId(panID);
					ackFrame.setDestAddr(destAddr);
					ackFrame.setDestPanId(panID);
					ackFrame.setSequenceNumber(sequenceNumber++);
					ackFrame.setPayload(msg.getBytes());
					radio.setState(AT86RF231.STATE_TX_ARET_ON);
					fio.transmit(ackFrame);
					System.out.println("CumACK dikirim: " + ackedSN + " -> " + Long.toHexString(destAddr));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}.start();
	}
	// =========================================================

	// =========================================================
	// TAMBAHAN: Kirim NACK / retransmit request ("111 <snStart> <snEnd>")
	// =========================================================
	public void sendRetransmitRequest(final FrameIO fio, final int snStart, final int snEnd, final long destAddr) {
		new Thread() {
			public void run() {
				try {
					String msg = "111 " + snStart + " " + snEnd;
					Frame nackFrame = new Frame(Frame.TYPE_DATA | Frame.ACK_REQUEST
							| Frame.DST_ADDR_16 | Frame.INTRA_PAN | Frame.SRC_ADDR_16);
					nackFrame.setSrcAddr(myAddress);
					nackFrame.setSrcPanId(panID);
					nackFrame.setDestAddr(destAddr);
					nackFrame.setDestPanId(panID);
					nackFrame.setSequenceNumber(sequenceNumber++);
					nackFrame.setPayload(msg.getBytes());
					radio.setState(AT86RF231.STATE_TX_ARET_ON);
					fio.transmit(nackFrame);
					System.out.println("Retransmit request dikirim: SN " + snStart + " s.d. " + snEnd
							+ " -> " + Long.toHexString(destAddr));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}.start();
	}
	// =========================================================


	public void sayHello() throws Exception {
		long t1 = Time.currentTimeMillis();
		String message = "001" + " " + t1;
		broadCast(message, sequenceNumber++);
		recvReply(fio, t1);
	}

	public void setYourTime(int hi) throws Exception {
		long t1 = Time.currentTimeMillis();
		for (int i = 1; i < nodeAddress.length; i++) {
			rttTable[i] = (rttTable[i] / hi);
			String message = "010" + " " + rttTable[i] / 2;
			sendTONODE(fio, message, sequenceNumber++, nodeAddress[i]);
		}
		recvReply(fio, t1);
	}

	public void tellYourTime() throws Exception {
		long t1 = Time.currentTimeMillis();
		String message = "011" + " " + t1;
		broadCast(message, sequenceNumber++);
		recvReply(fio, t1);
		
	}

	public void goSenseNOW() throws Exception {
		String message = "100" + " " + Time.currentTimeMillis() + " " + Time.currentTimeMillis();
		broadCast(message, sequenceNumber++);
		recvSense(fio);
	}

	public void goSTOP() throws Exception {
		System.out.println("goSTOP");
		String message = "111" + " " + Time.currentTimeMillis();
		broadCast(message, sequenceNumber++);
		stop = true;
	}


	public static void main(String[] args) throws Exception {
		progBS bs = new progBS();

		int[] pilihanMenu = {0, 0, 0, 0, 0};
		bs.initializeRadio();
		bs.initrttTable();
		bs.initializeFrameIO();
		bs.resetSequenceNumber();
		try {
			bs.useUSART();
		} catch (Exception e) {
			e.printStackTrace();
		}
		int pilih;
		int hi = 0;
		Time.setCurrentTimeMillis(Time.currentTimeMillis() + bs.hour7);
		do {
			pilih = bs.usart.read();
			switch (pilih) {
				case 0: { // STOP
					bs.goSTOP();
					break;
				}
				case 1: { // Hi all & hitung RTT
					pilihanMenu[pilih] = pilihanMenu[pilih] + 1;
					bs.resetStatusBC();
					bs.sayHello();
					hi++;
					break;
				}
				case 2: { // Sinkronisasi waktu ke semua node sensor
					pilihanMenu[pilih] = pilihanMenu[pilih] + 1;
					if ((hi > 0) && (pilihanMenu[1] > 0)) {
						bs.resetStatusBC();
						bs.setYourTime(hi);
					}
					break;
				}
				case 3: { // Cek waktu node sensor
					pilihanMenu[pilih] = pilihanMenu[pilih] + 1;
					bs.resetStatusBC();
					bs.tellYourTime();
					break;
				}
				case 4: { // Perintahkan node sensor untuk sensing
					if (pilihanMenu[2] > 0) {
						bs.goSenseNOW();
					}
					break;
				}
				default:
					break;
			}
		} while (pilih != 0);
	}
}