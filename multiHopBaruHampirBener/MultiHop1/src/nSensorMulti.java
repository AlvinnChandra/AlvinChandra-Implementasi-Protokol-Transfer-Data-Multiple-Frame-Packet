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

// ============================================================
// NODE A (affe) - index 1
// Topologi: BS (babe) <-> bafe <-> affe
// affe hanya bisa komunikasi dengan bafe (nextHop = bafe)
// affe yang sensing, bafe sebagai relay
// ============================================================
public class nSensorMulti {
    int channel = 24;
    int panID   = 0xCAFE;

    int[] nodeAddress = {0xBABE, 0xAFFE, 0xBAFE};

    int indexNode  = 1;
    int myAddress  = nodeAddress[indexNode]; // 0xAFFE

    // nextHop: satu-satunya tetangga affe adalah bafe
    int nextHop = nodeAddress[2]; // 0xBAFE

    LED ledMerah;
    AT86RF231 radio;
    FrameIO fio;
    Shuttle shuttle;
    ADT7410 sensorSuhu;
    NativeI2C i2c;

    int sequenceNumber;
    volatile boolean exit = false;

    // ----------------------------------------------------------------
    // INIT
    // ----------------------------------------------------------------
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
    // RECEIVER
    // ----------------------------------------------------------------
    public void startReceiver(final FrameIO fio) {
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

    public void dispatchFrame(final Frame frame, final long t2) throws Exception {
        new Thread() {
            public void run() {
                if (frame == null) return;
                try {
                    // Abaikan frame dari diri sendiri
                    if (frame.getSrcAddr() == myAddress) return;

                    byte[] payloadBytes = frame.getPayload();
                    String pesanDiterima = new String(payloadBytes, 0, payloadBytes.length);
                    String[] split = StringUtils.split(pesanDiterima, " ");
                    if (split == null || split.length == 0) return;

                    // Affe tidak relay SENSE (affe adalah ujung, tidak ada node di bawah)
                    if (split[0].equalsIgnoreCase("SENSE")) return;

                    int code = -1;
                    if      (split[0].equalsIgnoreCase("001")) code = 1;
                    else if (split[0].equalsIgnoreCase("010")) code = 2;
                    else if (split[0].equalsIgnoreCase("011")) code = 3;
                    else if (split[0].equalsIgnoreCase("100")) code = 4;
                    else if (split[0].equalsIgnoreCase("111")) code = 5;

                    // Proses command dan kirim reply ke bafe
                    // bafe yang bertanggung jawab meneruskan ke BS dengan srcAddr = affe
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
                            goSensing();
                            break;
                        case 5:
                            exit = true;
                            break;
                        default:
                            break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    // ----------------------------------------------------------------
    // PROSES COMMAND
    // Semua reply dikirim langsung ke bafe (nextHop).
    // bafe akan relay ke BS dengan MENGGANTI srcAddr menjadi myAddress (affe).
    // ----------------------------------------------------------------

    // Command 001: Hello
    // Format masuk dari bafe: "001 <t1> <tsBS>"
    // Reply ke bafe:           "001 <t1> <t2> <t3>"
    public void prosesHello(String[] split, long t2) throws Exception {
        long t1 = Long.parseLong(split[1]);
        long t3 = Time.currentTimeMillis();
        String msg = "001 " + t1 + " " + t2 + " " + t3;
        sendToBafe(msg, sequenceNumber++);
    }

    // Command 010: Set Time
    // Format masuk dari bafe: "010 <deltaRTT/2> <t1> <tsBS>"
    // Reply ke bafe:           "010 <delta_t3t2> <t3_baru>"
    public void prosesSinkronisasiWaktu(String[] split, long t2) throws Exception {
        long delta = Long.parseLong(split[1]);
        long t1    = Long.parseLong(split[2]);
        long deltat3t2 = Time.currentTimeMillis() - t2;
        Time.setCurrentTimeMillis(t1 + delta + deltat3t2);
        long t3 = Time.currentTimeMillis();
        System.out.println(stringFormatTime.SFFull(t3));
        // Format: "010 <deltat3t2> <dummy> <t3>"
        // progBS dispatch case 2 ambil split[3] = t3
        String msg = "010 " + deltat3t2 + " 0 " + t3;
        sendToBafe(msg, sequenceNumber++);
    }

    // Command 011: Tell Time
    // Format masuk dari bafe: "011 <t1> <tsBS>"
    // Reply ke bafe:           "011 <t1> <t2> <t3>"
    public void prosesDapatkanWaktu(String[] split, long t2) throws Exception {
        long t1 = Long.parseLong(split[1]);
        long t3 = Time.currentTimeMillis();
        String msg = "011 " + t1 + " " + t2 + " " + t3;
        sendToBafe(msg, sequenceNumber++);
    }

    // ----------------------------------------------------------------
    // SENSING (command 100) — hanya affe yang sensing
    // Data SENSE dikirim ke bafe, bafe relay ke BS
    // ----------------------------------------------------------------
    public void goSensing() throws Exception {
        new Thread() {
            public void run() {
                while (!exit) {
                    try {
                        long waktu = Time.currentTimeMillis();
                        float suhu = sensorSuhu.getTemperatureCelsius();
                        String data = Integer.toHexString(myAddress) + " " +
                                stringFormatTime.SFFull(waktu) +
                                " TEMP:" + suhu + "C";
                        int sn = sequenceNumber++;
                        if (sequenceNumber > 255) sequenceNumber = 0;
                        sendSenseToBafe("SENSE " + data, sn);
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
    // KIRIM KE BAFE
    // Semua komunikasi affe -> dunia luar lewat bafe
    // ----------------------------------------------------------------

    // Kirim reply command (001/010/011) ke bafe
    private void sendToBafe(final String msg, final int sn) {
        new Thread() {
            public void run() {
                boolean ok = false;
                while (!ok) {
                    try {
                        Frame frame = new Frame(Frame.TYPE_DATA | Frame.ACK_REQUEST
                                | Frame.DST_ADDR_16 | Frame.INTRA_PAN | Frame.SRC_ADDR_16);
                        frame.setSrcAddr(myAddress);
                        frame.setSrcPanId(panID);
                        frame.setDestAddr(nextHop); // bafe
                        frame.setDestPanId(panID);
                        frame.setSequenceNumber(sn);
                        frame.setPayload(msg.getBytes());
                        radio.setState(AT86RF231.STATE_TX_ARET_ON);
                        fio.transmit(frame);
                        System.out.println("sent to bafe: " + msg);
                        ok = true;
                    } catch (NoAckException e) {
                        // retry
                    } catch (ChannelBusyException e) {
                        // retry
                    } catch (Exception e) {
                        e.printStackTrace();
                        ok = true; // jangan loop infinite saat error lain
                    }
                }
            }
        }.start();
    }

    // Kirim data SENSE ke bafe
    private void sendSenseToBafe(final String msg, final int sn) throws InterruptedException {
        boolean ok = false;
        while (!ok) {
            try {
                Frame frame = new Frame(Frame.TYPE_DATA | Frame.ACK_REQUEST
                        | Frame.DST_ADDR_16 | Frame.INTRA_PAN | Frame.SRC_ADDR_16);
                frame.setSrcAddr(myAddress);
                frame.setSrcPanId(panID);
                frame.setDestAddr(nextHop); // bafe
                frame.setDestPanId(panID);
                frame.setSequenceNumber(sn);
                frame.setPayload(msg.getBytes());
                radio.setState(AT86RF231.STATE_TX_ARET_ON);
                fio.transmit(frame);
                ok = true;
            } catch (RadioDriverException e) {
                e.printStackTrace();
            } catch (NoAckException e) {
                // retry
            } catch (ChannelBusyException e) {
                // retry
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // ----------------------------------------------------------------
    // MAIN
    // ----------------------------------------------------------------
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