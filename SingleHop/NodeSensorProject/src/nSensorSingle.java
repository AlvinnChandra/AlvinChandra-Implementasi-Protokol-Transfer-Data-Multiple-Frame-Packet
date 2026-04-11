import java.io.IOException;
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

    // Inisialisasi LED, Radio, FrameIO, Temperature, Sequence Number
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

    // ----------------------------------------------------------------
    // RECEIVER -> terima frame masuk
    // ----------------------------------------------------------------
    public void startReceiver(final FrameIO fio) {
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

    public void dispatchFrame(final Frame frame, final long t2) throws Exception {
        new Thread() {
            final Lock lock = new ReentrantLock();

            public void run() {
                int code = -1;
                if (frame != null) {
                    try {
                        byte[] payloadBytes = frame.getPayload();
                        String pesanDiterima = new String(payloadBytes, 0, payloadBytes.length);
                        String[] splitPesan = StringUtils.split(pesanDiterima, " ");

                        if (splitPesan[0].equalsIgnoreCase("001"))
                            code = 1;
                        else if (splitPesan[0].equalsIgnoreCase("010"))
                            code = 2;
                        else if (splitPesan[0].equalsIgnoreCase("011"))
                            code = 3;
                        else if (splitPesan[0].equalsIgnoreCase("100"))
                            code = 4;
                        else if (splitPesan[0].equalsIgnoreCase("111"))
                            code = 5;

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
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    // ----------------------------------------------------------------
    // PROCESS COMMANDS dari BS
    // ----------------------------------------------------------------
    public void prosesHello(String splitPesan[], long destinationAddress, long t2) throws Exception
   	{	
   		// Format pesan : 001 t1 t1_kirim
   		long t1 = Long.parseLong(splitPesan[2]);
   		String message = "001" + " " + t1 + " " + t2; 
   		startTransmitter(fio, message, sequenceNumber++, destinationAddress);
   	}
   	
   	public void prosesSinkronisasiWaktu(String splitPesan[], long destinationAddress, long t2) throws Exception
   	{ // format pesan: 010 deltaDelay t1
   		long deltat3t2 = Time.currentTimeMillis() - t2;
   		Time.setCurrentTimeMillis( Long.parseLong(splitPesan[2]) + Long.parseLong(splitPesan[1]) + deltat3t2);

   		// format pesan reply : 010 t2 t3 (time after set)
   		String message = "010" + " " + deltat3t2 + " " + (Time.currentTimeMillis());
   		startTransmitter(fio, message, sequenceNumber++, destinationAddress); 
   	}
   	
   	public void prosesDapatkanWaktu(String splitPesan[], long destinationAddress, long t2) throws Exception
   	{ // untuk memproses Get time NOW
   	  // Format pesan: 011 t1 t1_kirim
   	 
   		long t1 = Long.parseLong(splitPesan[2]);
   		String message = "011" + " " + t1 + " " + t2;
   		startTransmitter(fio, message, sequenceNumber++, destinationAddress);
   	}

    // ----------------------------------------------------------------
    // GO SENSING -> sensing dan kirim langsung ke BS
    // ----------------------------------------------------------------
   	public void goSensing() throws Exception {
        new Thread() {
            public void run() {
                try {
                    initializeTEMPERATURE();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                while (!exit) {
                    try {
                        long waktuSensing = Time.currentTimeMillis();
                        float suhu = sensorSuhu.getTemperatureCelsius();

                        String dataSensing = Integer.toHexString(myAddress) + " " +
                                             stringFormatTime.SFFull(waktuSensing) +
                                             " TEMP:" + suhu + "C";

                        int snSekarang = sequenceNumber++;
                        if(sequenceNumber > 255) sequenceNumber = 0;
                        sendFrameSingleHop(fio, dataSensing, snSekarang);

                        Thread.sleep(1000);

                    } catch (I2CException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {}
                }
                System.out.println("Keluar");
            }
        }.start();
    }


    // ----------------------------------------------------------------
    // SINGLE HOP -> langsung ke BS
    // ----------------------------------------------------------------
    public void sendFrameSingleHop(final FrameIO fio, String msg, int sn) {
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
            } catch (RadioDriverException e) {
                e.printStackTrace();
            } catch (NoAckException e) {
                try { Thread.sleep(Time.currentTimeMillis() % 200); } catch (InterruptedException ie) {}
            } catch (ChannelBusyException e) {
                try { Thread.sleep(Time.currentTimeMillis() % 200); } catch (InterruptedException ie) {}
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // ----------------------------------------------------------------
    // TRANSMITTER -> kirim reply ke BS
    // ----------------------------------------------------------------
    private void startTransmitter(final FrameIO fio, final String mesg, final int sn, final long destADDR) {
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