import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Lock;

import com.virtenio.driver.i2c.NativeI2C;
import com.virtenio.driver.i2c.I2C;
import com.virtenio.driver.i2c.I2CException;
import com.virtenio.driver.device.ADT7410;
import com.virtenio.io.ChannelBusyException;
import com.virtenio.io.NoAckException;
import com.virtenio.misc.StringUtils;
import com.virtenio.preon32.examples.common.RadioInit;
import com.virtenio.preon32.shuttle.Shuttle;
import com.virtenio.radio.ieee_802_15_4.RadioDriver;
import com.virtenio.radio.ieee_802_15_4.Frame;
import com.virtenio.radio.ieee_802_15_4.FrameIO;
import com.virtenio.radio.ieee_802_15_4.RadioDriverFrameIO;
import com.virtenio.radio.RadioDriverException;
import com.virtenio.driver.device.at86rf231.*;
import com.virtenio.driver.device.at86rf231.AT86RF231RadioDriver;
import com.virtenio.driver.led.LED;

import com.virtenio.vm.Time;

public class nSensorSingle {
    int channel = 24;
    int panID = 0xCAFE;

    int[] nodeAddress = {0xBABE, 0xAFFE, 0xBAFE, 0xBEBA};

    int indexNode = 1;
    // 0xBABE -> BS        (index 0)
    // 0xAFFE -> node 1/A  (index 1)
    // 0xBAFE -> node 2/B  (index 2)
    // 0xBEBA -> node 3/C  (index 3)

    int myAddress = nodeAddress[indexNode];

    LED ledMerah;
    AT86RF231 radio;
    FrameIO fio;
    Shuttle shuttle;
    ADT7410 sensorSuhu;
    NativeI2C i2c;

    int sequenceNumber;

    volatile boolean exit = false;

    public void initializeLED() throws Exception {
        shuttle = Shuttle.getInstance();
        ledMerah = shuttle.getLED(Shuttle.LED_RED);
        ledMerah.open();
    }

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

    public void initializeTEMPERATURE() throws Exception {
        i2c = NativeI2C.getInstance(1);
        i2c.open(I2C.DATA_RATE_400);
        sensorSuhu = new ADT7410(i2c, ADT7410.ADDR_0, null, null);
        sensorSuhu.open();
        sensorSuhu.setMode(ADT7410.CONFIG_MODE_CONTINUOUS);
    }

    public void resetSequenceNumber() throws Exception {
        sequenceNumber = 0;
    }

    //Buffer
    int bufferSize = 60;
    int bufferTimeout = 5000;

    String[] dataBuffer = new String[bufferSize];
    int[] snBUffer = new int[bufferSize];
    int bufferCount = 0;
    long lastFlushTime = 0;

    //Ack
    Map<Integer, String> unackMap = new HashMap<Integer, String>(); 
    int highestAckSN = -1;
    Object mapLock = new Object();

    public void startReceiver(FrameIO fio) {
        Frame frame = new Frame();
        boolean stop = false;
        int snSebelumnya = -1;
        while (!stop) {
            System.out.println("ready");
            try {
                radio.setState(AT86RF231.STATE_RX_AACK_ON);
                fio.receive(frame);
                long waktuDiterimat2 = Time.currentTimeMillis();
                System.out.println("received");
                if (frame.getSequenceNumber() != snSebelumnya)
                    dispatchFrame(frame, waktuDiterimat2);
            } catch (Exception e) {
                e.printStackTrace();
            }
            snSebelumnya = frame.getSequenceNumber();
        }
    }

    public void dispatchFrame(Frame frame, long t2) throws Exception {
        new Thread() {
            final Lock lock = new ReentrantLock();

            public void run() {
                int code = -1;
                if (frame != null) {
                    try {
                        byte[] payloadBytes = frame.getPayload();
                        String pesanDiterima = new String(payloadBytes, 0, payloadBytes.length);
                        String[] splitPesan = StringUtils.split(pesanDiterima, " ");

                        if (splitPesan[0].equalsIgnoreCase("001")) {
                        	code = 1;
                        } else if (splitPesan[0].equalsIgnoreCase("010")) {
                        	code = 2;
                        } else if (splitPesan[0].equalsIgnoreCase("011")) {
                        	code = 3;
                        } else if (splitPesan[0].equalsIgnoreCase("100")) {
                        	code = 4;
                        } else if (splitPesan[0].equalsIgnoreCase("STOP")) {
                        	code = 5; // Stop
                        } else if (splitPesan[0].equalsIgnoreCase("110")) {
                        	code = 6;  // CUMACK
                        } else if (splitPesan[0].equalsIgnoreCase("111")) {
                        	code = 7;  // NACK / retransmit request
                        }
                        
                        switch (code) {
                            case 1: {
                                prosesHello(splitPesan, frame.getSrcAddr(), t2);
                                break;
                            }
                            case 2: {
                                prosesSinkronisasiWaktu(splitPesan, frame.getSrcAddr(), t2);
                                break;
                            }
                            case 3: {
                                prosesDapatkanWaktu(splitPesan, frame.getSrcAddr(), t2);
                                break;
                            }
                            case 4: {
                                goSensing();
                                break;
                            }
                            case 5: {
                                System.out.println("stop");
                                lock.lock();
                                try {
                                    exit = true;
                                    if (sensorSuhu != null) sensorSuhu.close();
                                    if (i2c != null) i2c.close();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                } finally {
                                    lock.unlock();
                                }
                                break;
                            }
                            case 6: {
                                // Format pesan: 110 <ackedSN>
                                // BS mengirim ACK kumulatif: semua frame s.d. ackedSN sudah diterima
                                int ackedSN = Integer.parseInt(splitPesan[1]);
                                processCumACK(ackedSN);
                                break;
                            }
                            case 7: {
                                // Format pesan: 111 <snStart> <snEnd>
                                // BS meminta pengiriman ulang frame dari snStart s.d. snEnd
                                int snStart = Integer.parseInt(splitPesan[1]);
                                int snEnd   = Integer.parseInt(splitPesan[2]);
                                retransmit(snStart, snEnd, frame.getSrcAddr());
                                break;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    public void processCumACK(int ackedSN) {
    	synchronized (mapLock) {
    		if(ackedSN <= highestAckSN) {
    			return;
    		}
    		
    		highestAckSN = ackedSN;
    		
    		List<Integer> toRemove = new ArrayList<Integer>(); 
    		for(Integer sn: unackMap.keySet()) {
    			if (sn <= ackedSN) {
    				toRemove.add(sn);
    			}
    		}
    		
    		for(Integer sn: toRemove) {
    			unackMap.remove(sn);
    		}
    		
    		System.out.println("CUMACK diterima: " + ackedSN
                    + " | sisa unacked: " + unackMap.size());
    	}
    }

    public void retransmit(int snStart, int snEnd, long destAddr) {
        new Thread() {
            public void run() {
                final Map<Integer, String> toRetransmit = new HashMap<Integer, String>();
                synchronized (mapLock) {
                    for (int sn = snStart; sn <= snEnd; sn++) {
                        String payload = (String) unackMap.get(sn);
                        if (payload != null) {
                            toRetransmit.put(sn, payload);
                        }
                    }
                }

                // Tahap 2: kirim ulang di luar lock
                for (int sn = snStart; sn <= snEnd; sn++) {
                    String payload = (String) toRetransmit.get(sn);
                    if (payload != null) {
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException e) {}
                        sendFrameSingleHop(fio, payload, sn);
                        System.out.println("Retransmit SN=" + sn);
                    }
                }
            }
        }.start();
    }

    public void prosesHello(String splitPesan[], long destinationAddress, long t2) throws Exception {
        // Format pesan masuk : 001 t1 t1_kirim
        long t1 = Long.parseLong(splitPesan[2]);
        String message = "001" + " " + t1 + " " + t2;
        startTransmitter(fio, message, sequenceNumber++, destinationAddress);
    }

    public void prosesSinkronisasiWaktu(String splitPesan[], long destinationAddress, long t2) throws Exception {
        // Format pesan masuk: 010 deltaDelay t1
        long deltat3t2 = Time.currentTimeMillis() - t2;
        Time.setCurrentTimeMillis(Long.parseLong(splitPesan[2]) + Long.parseLong(splitPesan[1]) + deltat3t2);

        // Format pesan balik: 010 deltat3t2 waktuSekarang
        String message = "010" + " " + deltat3t2 + " " + (Time.currentTimeMillis());
        startTransmitter(fio, message, sequenceNumber++, destinationAddress);
    }

    public void prosesDapatkanWaktu(String splitPesan[], long destinationAddress, long t2) throws Exception {
        // Format pesan masuk: 011 t1 t1_kirim
        long t1 = Long.parseLong(splitPesan[2]);
        String message = "011" + " " + t1 + " " + t2;
        startTransmitter(fio, message, sequenceNumber++, destinationAddress);
    }

    public void goSensing() throws Exception {
        new Thread() {
            public void run() {
                lastFlushTime = Time.currentTimeMillis();

                while (!exit) {
                    try {
                        long waktuSensing = Time.currentTimeMillis();
                        float suhu = sensorSuhu.getTemperatureCelsius();

                        int s = (int)(suhu * 100);
                        int desimal = Math.abs(s % 100);
                        String desimalStr;

                        if (desimal < 10) {
                            desimalStr = "0" + desimal;
                        } else {
                            desimalStr = "" + desimal;
                        }
                        
                        String suhuStr = (s / 100) + "." + desimalStr;

                        String dataSensing = Integer.toHexString(myAddress) + " " +
                                             stringFormatTime.SFFull(waktuSensing) +
                                             " TEMP:" + suhuStr + "°C";

                        synchronized (dataBuffer) {
                            snBUffer[bufferCount] = sequenceNumber;
                            dataBuffer[bufferCount] = dataSensing;
                            bufferCount++;
                            sequenceNumber++;
                            if (sequenceNumber > 255) {
                                sequenceNumber = 0;
                            }

                            if (bufferCount >= bufferSize) {
                                flushBuffer();
                            }
                        }

                        Thread.sleep(1000);

                    } catch (I2CException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {}
                }

                // Flush sisa buffer saat exit
                synchronized (dataBuffer) {
                    if (bufferCount > 0) {
                        flushBuffer();
                    }
                }

                System.out.println("Keluar");
            }
        }.start();
    }

    private void flushBuffer() {
        if (bufferCount == 0) return;

        int start = 0;
        while (start < bufferCount) {
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (int i = start; i < bufferCount; i++) {
                String entry = "SN:" + snBUffer[i] + " " + dataBuffer[i];
                String candidate;

                if (sb.length() > 0) {
                    candidate = "PKT:" + (count + 1) + "|" +
                                sb.toString() + "|" + entry;
                } else {
                    candidate = "PKT:" + (count + 1) + "|" +
                                entry;
                }
                
                if (candidate.getBytes().length > 107) {
                	break;
                }
                if (sb.length() > 0) {
                	sb.append("|");
                }
                sb.append(entry);
                count++;
            }

            if (count == 0) {
                start++;
                continue;
            }

            String payload = "PKT:" + count + "|" + sb.toString();
            int frameSN = sequenceNumber++;
            if (sequenceNumber > 255) sequenceNumber = 0;

            // Simpan ke unackMap sebelum kirim
            synchronized (mapLock) {
                unackMap.put(frameSN, payload);
            }

            sendFrameSingleHop(fio, payload, frameSN);
            System.out.println("Flush " + count + " data, frame SN=" + frameSN);
            start += count;
        }

        bufferCount = 0;
        lastFlushTime = Time.currentTimeMillis();
    }

  
    public void sendFrameSingleHop(FrameIO fio, String msg, int sn) {
        boolean isOK = false;
        while (!isOK) {
            try {
                String message = "SENSE " + msg;
                Frame frame = new Frame(Frame.TYPE_DATA | Frame.ACK_REQUEST
                        | Frame.DST_ADDR_16 | Frame.INTRA_PAN | Frame.SRC_ADDR_16);
                frame.setSequenceNumber(sn);
                frame.setSrcAddr(myAddress);
                frame.setDestAddr(nodeAddress[0]);
                frame.setDestPanId(panID);
                frame.setPayload(message.getBytes());
                radio.setState(AT86RF231.STATE_TX_ARET_ON);
                fio.transmit(frame);
                isOK = true;
                System.out.println("Terkirim SN=" + sn);
            } catch (RadioDriverException e) {
                e.printStackTrace();
            } catch (NoAckException e) {
            } catch (ChannelBusyException e) {
            } catch (IOException e) {
            }
        }
    }

    public void startTransmitter(FrameIO fio, String mesg, int sn, long destADDR) {
        new Thread() {
            public void run() {
                boolean isOK = false;
                while (!isOK) {
                    try {
                        Frame frame = new Frame(Frame.TYPE_DATA | Frame.ACK_REQUEST
                                | Frame.DST_ADDR_16 | Frame.INTRA_PAN | Frame.SRC_ADDR_16);
                        frame.setSrcAddr(myAddress);
                        frame.setSrcPanId(panID);
                        frame.setDestAddr(destADDR);
                        frame.setDestPanId(panID);
                        frame.setSequenceNumber(sn);
                        String message = mesg + " " + Time.currentTimeMillis();
                        frame.setPayload(message.getBytes());
                        radio.setState(AT86RF231.STATE_TX_ARET_ON);
                        fio.transmit(frame);
                        System.out.println("transmitted");
                        isOK = true;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    public void run() {
        try {
            initializeLED();
            initializeRadio();
            initializeFrameIO();
            initializeTEMPERATURE();
            resetSequenceNumber();
            ledMerah.on();
            startReceiver(fio);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        new nSensorSingle().run();
    }
}