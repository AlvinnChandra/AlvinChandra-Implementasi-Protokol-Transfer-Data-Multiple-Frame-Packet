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
import com.virtenio.driver.led.LED;

import com.virtenio.vm.Time;

public class nSensorMulti {
    int channel = 24;
    int panID   = 0xCAFE;

    int[] nodeAddress = {0xBABE, 0xAFFE, 0xBAFE};

    int indexNode  = 3;
    int myAddress  = nodeAddress[indexNode];
    
    int nextHop;
    {
        if (indexNode < nodeAddress.length - 1) {
            nextHop = nodeAddress[indexNode + 1]; 
        } else {
            nextHop = nodeAddress[0];
        }
    }
    
    int prevHop;
    {
        if (indexNode > 1) {
            prevHop = nodeAddress[indexNode - 1];
        }
    }

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

    public void startReceiver(FrameIO fio) {
        Frame frame = new Frame();
        int snSebelumnya = -1;
        while (true) {
            System.out.println("ready");
            try {
                radio.setState(AT86RF231.STATE_RX_AACK_ON);
                fio.receive(frame);
                long t2 = Time.currentTimeMillis();
                System.out.println("received");
                if (frame.getSequenceNumber() != snSebelumnya) {
                    dispatchFrame(frame, t2);
                }
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
                if (frame == null) return;
                try {
                    long srcAddr = frame.getSrcAddr();
                    if (srcAddr == myAddress) {
                    	return;
                    }

                    byte[] payloadBytes = frame.getPayload();
                    String pesanDiterima = new String(payloadBytes, 0, payloadBytes.length);
                    String[] split = StringUtils.split(pesanDiterima, " ");
                    
                    if (split == null || split.length == 0) {
                    	return;
                    }

                    if (srcAddr == prevHop) {
                        relayToNextHop(pesanDiterima, sequenceNumber++, srcAddr);
                        return;
                    }

                    if (split[0].equalsIgnoreCase("SENSE")) {
                    	return;
                    }

                    int code = -1;
                    if (split[0].equalsIgnoreCase("001")) {
                    	code = 1;
                    } else if (split[0].equalsIgnoreCase("010")) {
                    	code = 2;
                    } else if (split[0].equalsIgnoreCase("011")) {
                    	code = 3;
                    } else if (split[0].equalsIgnoreCase("100")) {
                    	code = 4;
                    } else if (split[0].equalsIgnoreCase("111")) {
                    	code = 5;
                    }

                    // STEP 1: Relay command ke affe (semua command 001-111)
                    if (code >= 1 && code <= 5) {
                        relayToPrevHop(pesanDiterima, sequenceNumber++);
                    }

                    // STEP 2: Proses sendiri dan reply ke BS
                    switch (code) {
                        case 1:
                            prosesHello(split, t2);
                            break;
                        case 2:
                            prosesSinkronisasiWaktu(split, t2);
                            break;
                        case 3:
                            prosesDapatkanWaktu(split, t2);
                            break;
                        case 4:
                            // bafe tidak sensing, hanya affe yang sensing
                        	goSensing();
                            break;
                        case 5: {
                            System.out.println("stop");
                            lock.lock();
                            try {
                                exit = true;
                                if (sensorSuhu != null) {
                                	sensorSuhu.close();
                                }
                                if (i2c != null) {
                                	i2c.close();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                lock.unlock();
                            }
                            break;
                        }
                        default:
                            break;
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    // Command 001: Hello
    // BS kirim: "001 <t1> <tsBS>"
    // Reply ke BS: "001 <t1> <t2> <t3>"
    public void prosesHello(String[] split, long t2) throws Exception {
        long t1 = Long.parseLong(split[1]);
        long t3 = Time.currentTimeMillis();
        String msg = "001 " + t1 + " " + t2 + " " + t3;
        relayToNextHop(msg, sequenceNumber++, myAddress);
    }

    // Command 010: Set Time
    // BS kirim: "010 <deltaRTT/2> <t1> <tsBS>"
    // progBS: message = "010 " + rttTable[i]/2 + " " + t1  (lalu append tsBS)
    // Jadi: split[0]=010, split[1]=delta, split[2]=t1, split[3]=tsBS
    // Reply ke BS: "010 <delta_t3t2> <t3_baru>"
    // progBS dispatch case 2: split[3]=t3 -> reply format "010 <d> <t3>"
    public void prosesSinkronisasiWaktu(String[] split, long t2) throws Exception {
        long delta     = Long.parseLong(split[1]);
        long t1        = Long.parseLong(split[2]);
        long deltat3t2 = Time.currentTimeMillis() - t2;
        Time.setCurrentTimeMillis(t1 + delta + deltat3t2);
        long t3 = Time.currentTimeMillis();
        System.out.println(stringFormatTime.SFFull(t3));
        // Format: "010 <deltat3t2> <dummy> <t3>"
        // progBS dispatch case 2 ambil split[3] = t3
        String msg = "010 " + deltat3t2 + " 0 " + t3;
        relayToNextHop(msg, sequenceNumber++, myAddress);
    }

    // Command 011: Tell Time
    // BS kirim: "011 <t1> <tsBS>"
    // Reply ke BS: "011 <t1> <t2> <t3>"
    public void prosesDapatkanWaktu(String[] split, long t2) throws Exception {
        long t1 = Long.parseLong(split[1]);
        long t3 = Time.currentTimeMillis();
        String msg = "011 " + t1 + " " + t2 + " " + t3;
        relayToNextHop(msg, sequenceNumber++, myAddress);
    }
    
    public void goSensing() throws Exception {
        new Thread() {
            public void run() {
                while (!exit) {
                    try {
                        long waktu = Time.currentTimeMillis();
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
                        
                        String data = Integer.toHexString(myAddress) + " " +
                                stringFormatTime.SFFull(waktu) +
                                " TEMP:" + suhuStr + "°C";
                        int sn = sequenceNumber++;
                        if (sequenceNumber > 255) {
                        	sequenceNumber = 0;
                        }
                        sendSenseToNextHop("SENSE " + data, sn);
                        Thread.sleep(1000);
                    } catch (I2CException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {}
                }
                System.out.println("Keluar");
            }
        }.start();
    }
    
    public void sendSenseToNextHop(String msg, int sn) throws InterruptedException {
        boolean ok = false;
        while (!ok) {
            try {
                Frame frame = new Frame(Frame.TYPE_DATA | Frame.ACK_REQUEST
                        | Frame.DST_ADDR_16 | Frame.INTRA_PAN | Frame.SRC_ADDR_16);
                frame.setSrcAddr(myAddress);
                frame.setSrcPanId(panID);
                frame.setDestAddr(nextHop);
                frame.setDestPanId(panID);
                frame.setSequenceNumber(sn);
                frame.setPayload(msg.getBytes());
                radio.setState(AT86RF231.STATE_TX_ARET_ON);
                fio.transmit(frame);
                ok = true;
            } catch (RadioDriverException e) {
                e.printStackTrace();
            } catch (NoAckException e) {
            } catch (ChannelBusyException e) {
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // ----------------------------------------------------------------
    // RELAY ke AFFE (forward command dari BS ke affe)
    // Kirim frame dengan srcAddr = myAddress (bafe), destAddr = affe
    // ----------------------------------------------------------------
    public void relayToPrevHop(String msg, int sn) throws InterruptedException {
        boolean ok = false;
        while (!ok) {
            try {
                Frame frame = new Frame(Frame.TYPE_DATA | Frame.ACK_REQUEST
                        | Frame.DST_ADDR_16 | Frame.INTRA_PAN | Frame.SRC_ADDR_16);
                frame.setSrcAddr(myAddress);
                frame.setSrcPanId(panID);
                frame.setDestAddr(prevHop);
                frame.setDestPanId(panID);
                frame.setSequenceNumber(sn);
                frame.setPayload(msg.getBytes());
                radio.setState(AT86RF231.STATE_TX_ARET_ON);
                fio.transmit(frame);
                System.out.println("relay to affe: " + msg);
                ok = true;
            } catch (RadioDriverException e) {
                e.printStackTrace();
            } catch (NoAckException e) {
            } catch (ChannelBusyException e) {
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    public void relayToNextHop(String msg, int sn, long originalSrcAddr) {
        new Thread() {
            public void run() {
                boolean ok = false;
                while (!ok) {
                    try {
                        Frame frame = new Frame(Frame.TYPE_DATA | Frame.ACK_REQUEST
                                | Frame.DST_ADDR_16 | Frame.INTRA_PAN | Frame.SRC_ADDR_16);
                        // srcAddr = affe agar BS tahu ini dari affe
                        frame.setSrcAddr(originalSrcAddr);
                        frame.setSrcPanId(panID);
                        frame.setDestAddr(nextHop);
                        frame.setDestPanId(panID);
                        frame.setSequenceNumber(sn);
                        frame.setPayload(msg.getBytes());
                        radio.setState(AT86RF231.STATE_TX_ARET_ON);
                        fio.transmit(frame);
                        System.out.println("relay to BS (srcAddr=affe): " + msg);
                        ok = true;
                    } catch (RadioDriverException e) {
                        e.printStackTrace();
                    } catch (NoAckException e) {
                    } catch (ChannelBusyException e) {
                    } catch (Exception e) {
                        e.printStackTrace();
                        ok = true;
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
        new nSensorMulti().run();
    }
}