package mfec.fixprotocol.acceptor.app;

import quickfix.*;

import quickfix.field.AvgPx;
import quickfix.field.CumQty;
import quickfix.field.ExecID;

import quickfix.field.ExecType;
import quickfix.field.LeavesQty;
import quickfix.field.OrdStatus;
import quickfix.field.OrderID;
import quickfix.field.Side;
import quickfix.field.OrdType;
import mfec.fixprotocol.acceptor.app.FixServerApp;

public class App {
    public static void main(String[] args) throws Exception {
        // 1. โหลด Config และ Env
        // Load .env explicitly. Try finding it in current dir or "fix.acceptor" subdir
        io.github.cdimascio.dotenv.Dotenv dotenv;
        try {
            dotenv = io.github.cdimascio.dotenv.Dotenv.configure().load();
        } catch (io.github.cdimascio.dotenv.DotenvException e) {
            // Try subfolder
            dotenv = io.github.cdimascio.dotenv.Dotenv.configure()
                    .directory("fix.acceptor")
                    .load();
        }

        java.io.InputStream inputStream = App.class.getClassLoader().getResourceAsStream("acceptor.cfg");
        if (inputStream == null) {
            System.err.println("acceptor.cfg not found in classpath!");
            return;
        }

        // อ่านไฟล์ Config มาเป็น String เพื่อแทนค่าตัวแปร
        java.util.Scanner s = new java.util.Scanner(inputStream).useDelimiter("\\A");
        String configString = s.hasNext() ? s.next() : "";
        inputStream.close();

        // Regex เพื่อค้นหาและแทนที่ ${VAR_NAME}
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$\\{([^}]+)\\}");
        java.util.regex.Matcher matcher = pattern.matcher(configString);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String key = matcher.group(1);
            // 1. ลองหาจาก Dotenv (ซึ่งปกติจะอ่าน System Env ด้วยถ้า Config ไว้)
            String value = dotenv.get(key);
            // 2. ถ้าไม่มีใน Dotenv ให้ลองหาจาก System.getenv() โดยตรง (สำหรับ Docker env)
            if (value == null) {
                value = System.getenv(key);
            }
            // 3. ถ้าหาไม่เจอเลย ให้ใส่ค่าเดิมไว้ หรือแจ้งเตือน
            if (value != null) {
                matcher.appendReplacement(sb, value);
            } else {
                System.out.println("WARNING: Variable " + key + " not found in environment. Keeping placeholder.");
                // matcher.appendReplacement(sb, ""); // ไม่แทนที่ ปล่อยให้ QuickFIX error
                // เองหรือใช้ค่าเดิม
            }
        }
        matcher.appendTail(sb);
        configString = sb.toString();

        System.out.println("DEBUG: Final Config content:\n" + configString);

        // แปลง String กลับเป็น Stream เพื่อให้ QuickFIX ใช้งาน
        java.io.InputStream configStream = new java.io.ByteArrayInputStream(
                configString.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        SessionSettings settings = new SessionSettings(configStream);
        configStream.close();

        // 2. ตั้งค่า Application และระบบจัดเก็บข้อมูล (Store)
        Application myApp = new FixServerApp();
        MessageStoreFactory storeFactory = new FileStoreFactory(settings);
        LogFactory logFactory = new ScreenLogFactory(settings);
        MessageFactory messageFactory = new DefaultMessageFactory();

        // 3. สร้าง Acceptor
        Acceptor acceptor = new ThreadedSocketAcceptor(
                myApp, storeFactory, settings, logFactory, messageFactory);

        // 4. เริ่มเดินเครื่อง!
        System.out.println("FIX Server is running on port " + settings.getString("SocketAcceptPort") + "...");
        acceptor.start();

        System.out.println("--- ระบบส่ง Transaction แบบ Manual เริ่มทำงาน ---");

        // Create a scheduler for automated message sending
        java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors
                .newSingleThreadScheduledExecutor();

        System.out.println("--- เริ่มส่ง Transaction แบบอัตโนมัติทุกๆ 10 วินาที ---");

        scheduler.scheduleAtFixedRate(() -> {
            try {
                // --- ส่วนของ Logic การส่ง Message ---
                if (!acceptor.getSessions().isEmpty()) {
                    SessionID sessionId = acceptor.getSessions().get(0);

                    // สร้าง Execution Report (FIX 5.0) ตามตัวอย่างที่ให้มา
                    // 8=FIXT.1.1 9=... 35=8 55=AOT ...
                    quickfix.fix50.ExecutionReport executionReport = new quickfix.fix50.ExecutionReport(
                            new OrderID("0B045C42000104DB"), // OrderID
                            new ExecID("13150"), // ExecID
                            new ExecType(ExecType.NEW), // ExecType = 0 (New)
                            new OrdStatus(OrdStatus.NEW), // OrdStatus = 0 (New)
                            new Side(Side.BUY), // Side = 1 (Buy) - Default as example, user example had 54=1
                            new LeavesQty(100), // LeavesQty (User example: 151=100)
                            new CumQty(0) // CumQty (User example: 14=0)
                    );

                    // Field อื่นๆ ที่ไม่ได้อยู่ใน Constructor ต้อง set แยกต่างหาก
                    executionReport.set(new quickfix.field.Symbol("AOT")); // 55=AOT
                    executionReport.set(new quickfix.field.SecurityID("65553")); // 48=65553
                    executionReport.set(new quickfix.field.SecurityIDSource("M")); // 22=M
                    executionReport.set(new quickfix.field.OrderQty(100)); // 38=100
                    executionReport.set(new quickfix.field.OrdType(OrdType.LIMIT)); // 40=2 (Limit)
                    executionReport.set(new AvgPx(0)); // 6=0
                    executionReport.set(new quickfix.field.Price(61.25)); // 44=61.25
                    executionReport.set(new quickfix.field.ClOrdID("049N20IA6PT0004")); // 11=...
                    executionReport.set(new quickfix.field.TimeInForce(quickfix.field.TimeInForce.DAY)); // 59=0

                    // Timestamp (Manual string to match format if needed, but quickfix handles 60
                    // automatically if not set, or we can force it)
                    // executionReport.setString(60, "20241129-03:09:35.901780055");
                    // Timestamp
                    executionReport.set(new quickfix.field.TransactTime(java.time.LocalDateTime.now())); // Realtime
                                                                                                         // timestamp
                                                                                                         // preferred

                    // Custom Fields (String)
                    executionReport.setString(30001, "N");
                    executionReport.setString(30009, "N");
                    executionReport.setString(2362, "992133217");
                    executionReport.setString(30010, "T");
                    executionReport.setString(30012, "N");
                    executionReport.setString(581, "1"); // AccountType?
                    executionReport.setString(797, "Y"); // CopyMsgIndicator

                    // Parties Group (Tag 453)
                    // Group 1: 448=9221332174 447=D 452=24
                    quickfix.fix50.ExecutionReport.NoPartyIDs group1 = new quickfix.fix50.ExecutionReport.NoPartyIDs();
                    group1.set(new quickfix.field.PartyID("9221332174"));
                    group1.set(new quickfix.field.PartyIDSource('D'));
                    group1.set(new quickfix.field.PartyRole(24));
                    executionReport.addGroup(group1);

                    // Group 2: 448=9221332174 447=D 452=83
                    quickfix.fix50.ExecutionReport.NoPartyIDs group2 = new quickfix.fix50.ExecutionReport.NoPartyIDs();
                    group2.set(new quickfix.field.PartyID("9221332174"));
                    group2.set(new quickfix.field.PartyIDSource('D'));
                    group2.set(new quickfix.field.PartyRole(83));
                    executionReport.addGroup(group2);

                    // Group 3: 448=C023 447=D 452=4
                    quickfix.fix50.ExecutionReport.NoPartyIDs group3 = new quickfix.fix50.ExecutionReport.NoPartyIDs();
                    group3.set(new quickfix.field.PartyID("C023"));
                    group3.set(new quickfix.field.PartyIDSource('D'));
                    group3.set(new quickfix.field.PartyRole(4));
                    executionReport.addGroup(group3);

                    // Group 4: 448=I1111 447=D 452=12
                    quickfix.fix50.ExecutionReport.NoPartyIDs group4 = new quickfix.fix50.ExecutionReport.NoPartyIDs();
                    group4.set(new quickfix.field.PartyID("I1111"));
                    group4.set(new quickfix.field.PartyIDSource('D'));
                    group4.set(new quickfix.field.PartyRole(12));
                    executionReport.addGroup(group4);

                    try {
                        Session.sendToTarget(executionReport, sessionId);
                        System.out.println("✅ ส่ง ExecutionReport (AOT) เรียบร้อย!");
                    } catch (SessionNotFound e) {
                        System.out.println("❌ หา Session ไม่เจอ: " + e.getMessage());
                    } catch (Exception e) {
                        System.out.println("❌ Error sending message: " + e.getMessage());
                        e.printStackTrace();
                    }

                } else {
                    System.out.println("Wait for connection...");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 1, java.util.concurrent.TimeUnit.MINUTES);

        // รันค้างไว้จนกว่าจะกดปุ่มหยุด
        System.out.println("Press Enter to close the server.");
        System.in.read();
        acceptor.stop();
    }
}