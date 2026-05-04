import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Hospital Management System — Single-file Java implementation
 * Database: MySQL (configure DB_URL, DB_USER, DB_PASS below)
 *
 * Compile:  javac HospitalManagementSystem.java
 * Run:      java HospitalManagementSystem
 *
 * Required MySQL driver on classpath:
 *   javac -cp mysql-connector-j-*.jar HospitalManagementSystem.java
 *   java  -cp .:mysql-connector-j-*.jar HospitalManagementSystem
 */
public class HospitalManagementSystem {

    // ─── Database configuration ────────────────────────────────────────────────
    private static final String DB_URL  = "jdbc:mysql://localhost:3306/hospital_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "password";  // ← change this

    // ─── Singleton connection ──────────────────────────────────────────────────
    private static Connection conn;

    public static Connection getConnection() throws SQLException {
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        }
        return conn;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  DOMAIN MODELS
    // ══════════════════════════════════════════════════════════════════════════

    static class Patient {
        int id;
        String name, phone, email, bloodGroup, address;
        LocalDate dob;

        Patient(int id, String name, String phone, String email,
                String bloodGroup, String address, LocalDate dob) {
            this.id = id; this.name = name; this.phone = phone;
            this.email = email; this.bloodGroup = bloodGroup;
            this.address = address; this.dob = dob;
        }

        @Override public String toString() {
            return String.format("ID:%-4d | %-25s | %-15s | %-10s | DOB:%s",
                    id, name, phone, bloodGroup, dob);
        }
    }

    static class Doctor {
        int id;
        String name, specialization, phone, email;
        double consultationFee;

        Doctor(int id, String name, String specialization,
               String phone, String email, double fee) {
            this.id = id; this.name = name;
            this.specialization = specialization;
            this.phone = phone; this.email = email;
            this.consultationFee = fee;
        }

        @Override public String toString() {
            return String.format("ID:%-4d | Dr. %-22s | %-20s | Fee: KES %.0f",
                    id, name, specialization, consultationFee);
        }
    }

    static class Appointment {
        int id, patientId, doctorId;
        String patientName, doctorName, status, notes;
        LocalDateTime dateTime;

        Appointment(int id, int patientId, String patientName,
                    int doctorId, String doctorName,
                    LocalDateTime dateTime, String status, String notes) {
            this.id = id; this.patientId = patientId;
            this.patientName = patientName; this.doctorId = doctorId;
            this.doctorName = doctorName; this.dateTime = dateTime;
            this.status = status; this.notes = notes;
        }

        @Override public String toString() {
            return String.format("ID:%-4d | %-20s → Dr.%-18s | %s | %-10s",
                    id, patientName, doctorName,
                    dateTime.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")),
                    status);
        }
    }

    static class Bed {
        int id;
        String ward, type;
        boolean occupied;
        Integer patientId;
        String patientName;

        Bed(int id, String ward, String type,
            boolean occupied, Integer patientId, String patientName) {
            this.id = id; this.ward = ward; this.type = type;
            this.occupied = occupied; this.patientId = patientId;
            this.patientName = patientName;
        }

        @Override public String toString() {
            String status = occupied
                    ? "OCCUPIED by " + patientName
                    : "AVAILABLE";
            return String.format("ID:%-4d | %-15s | %-12s | %s", id, ward, type, status);
        }
    }

    static class Prescription {
        int id, appointmentId, patientId;
        String patientName, doctorName, medication, dosage, instructions;
        LocalDate prescribedDate;

        Prescription(int id, int appointmentId, int patientId,
                     String patientName, String doctorName,
                     String medication, String dosage,
                     String instructions, LocalDate prescribedDate) {
            this.id = id; this.appointmentId = appointmentId;
            this.patientId = patientId; this.patientName = patientName;
            this.doctorName = doctorName; this.medication = medication;
            this.dosage = dosage; this.instructions = instructions;
            this.prescribedDate = prescribedDate;
        }

        @Override public String toString() {
            return String.format("ID:%-4d | %-20s | %-25s | %-15s | %s",
                    id, patientName, medication, dosage, prescribedDate);
        }
    }

    static class Invoice {
        int id, patientId;
        String patientName, status;
        double totalAmount, paidAmount;
        LocalDate issueDate;

        Invoice(int id, int patientId, String patientName,
                double totalAmount, double paidAmount,
                String status, LocalDate issueDate) {
            this.id = id; this.patientId = patientId;
            this.patientName = patientName; this.totalAmount = totalAmount;
            this.paidAmount = paidAmount; this.status = status;
            this.issueDate = issueDate;
        }

        @Override public String toString() {
            return String.format("ID:%-4d | %-20s | Total:KES%-8.0f | Paid:KES%-8.0f | %-10s | %s",
                    id, patientName, totalAmount, paidAmount, status, issueDate);
        }
    }

    static class Staff {
        int id;
        String name, role, department, phone, email;
        double salary;

        Staff(int id, String name, String role, String department,
              String phone, String email, double salary) {
            this.id = id; this.name = name; this.role = role;
            this.department = department; this.phone = phone;
            this.email = email; this.salary = salary;
        }

        @Override public String toString() {
            return String.format("ID:%-4d | %-25s | %-15s | %-20s | Salary:KES%.0f",
                    id, name, role, department, salary);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  DATABASE INITIALIZER
    // ══════════════════════════════════════════════════════════════════════════

    static void initDatabase() throws SQLException {
        Connection c = getConnection();
        Statement st = c.createStatement();

        st.execute("""
            CREATE TABLE IF NOT EXISTS patients (
                patient_id   INT AUTO_INCREMENT PRIMARY KEY,
                name         VARCHAR(100) NOT NULL,
                phone        VARCHAR(20)  UNIQUE,
                email        VARCHAR(100),
                blood_group  VARCHAR(5),
                address      TEXT,
                dob          DATE
            )""");

        st.execute("""
            CREATE TABLE IF NOT EXISTS doctors (
                doctor_id         INT AUTO_INCREMENT PRIMARY KEY,
                name              VARCHAR(100) NOT NULL,
                specialization    VARCHAR(100),
                phone             VARCHAR(20),
                email             VARCHAR(100),
                consultation_fee  DECIMAL(10,2) DEFAULT 0
            )""");

        st.execute("""
            CREATE TABLE IF NOT EXISTS appointments (
                appointment_id  INT AUTO_INCREMENT PRIMARY KEY,
                patient_id      INT NOT NULL,
                doctor_id       INT NOT NULL,
                appt_datetime   DATETIME NOT NULL,
                status          ENUM('SCHEDULED','COMPLETED','CANCELLED') DEFAULT 'SCHEDULED',
                notes           TEXT,
                FOREIGN KEY (patient_id) REFERENCES patients(patient_id),
                FOREIGN KEY (doctor_id)  REFERENCES doctors(doctor_id)
            )""");

        st.execute("""
            CREATE TABLE IF NOT EXISTS beds (
                bed_id      INT AUTO_INCREMENT PRIMARY KEY,
                ward        VARCHAR(50)  NOT NULL,
                type        VARCHAR(30)  DEFAULT 'General',
                occupied    BOOLEAN      DEFAULT FALSE,
                patient_id  INT,
                FOREIGN KEY (patient_id) REFERENCES patients(patient_id)
            )""");

        st.execute("""
            CREATE TABLE IF NOT EXISTS prescriptions (
                prescription_id  INT AUTO_INCREMENT PRIMARY KEY,
                appointment_id   INT,
                patient_id       INT NOT NULL,
                doctor_id        INT NOT NULL,
                medication       VARCHAR(200) NOT NULL,
                dosage           VARCHAR(100),
                instructions     TEXT,
                prescribed_date  DATE DEFAULT (CURRENT_DATE),
                FOREIGN KEY (patient_id) REFERENCES patients(patient_id),
                FOREIGN KEY (doctor_id)  REFERENCES doctors(doctor_id)
            )""");

        st.execute("""
            CREATE TABLE IF NOT EXISTS invoices (
                invoice_id    INT AUTO_INCREMENT PRIMARY KEY,
                patient_id    INT NOT NULL,
                total_amount  DECIMAL(12,2) DEFAULT 0,
                paid_amount   DECIMAL(12,2) DEFAULT 0,
                status        ENUM('PENDING','PAID','PARTIAL') DEFAULT 'PENDING',
                issue_date    DATE DEFAULT (CURRENT_DATE),
                FOREIGN KEY (patient_id) REFERENCES patients(patient_id)
            )""");

        st.execute("""
            CREATE TABLE IF NOT EXISTS invoice_items (
                item_id     INT AUTO_INCREMENT PRIMARY KEY,
                invoice_id  INT NOT NULL,
                description VARCHAR(200),
                amount      DECIMAL(10,2),
                FOREIGN KEY (invoice_id) REFERENCES invoices(invoice_id)
            )""");

        st.execute("""
            CREATE TABLE IF NOT EXISTS staff (
                staff_id    INT AUTO_INCREMENT PRIMARY KEY,
                name        VARCHAR(100) NOT NULL,
                role        VARCHAR(50),
                department  VARCHAR(100),
                phone       VARCHAR(20),
                email       VARCHAR(100),
                salary      DECIMAL(12,2) DEFAULT 0
            )""");

        st.close();
        System.out.println("✔  Database schema ready.");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PATIENT SERVICE
    // ══════════════════════════════════════════════════════════════════════════

    static void registerPatient(Scanner sc) throws SQLException {
        System.out.println("\n─── Register New Patient ───");
        System.out.print("Full name    : "); String name = sc.nextLine().trim();
        System.out.print("Phone        : "); String phone = sc.nextLine().trim();
        System.out.print("Email        : "); String email = sc.nextLine().trim();
        System.out.print("Blood group  : "); String blood = sc.nextLine().trim();
        System.out.print("Address      : "); String addr = sc.nextLine().trim();
        System.out.print("Date of birth (YYYY-MM-DD): "); String dob = sc.nextLine().trim();

        String sql = "INSERT INTO patients(name,phone,email,blood_group,address,dob) VALUES(?,?,?,?,?,?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name); ps.setString(2, phone);
            ps.setString(3, email); ps.setString(4, blood);
            ps.setString(5, addr);
            ps.setDate(6, dob.isEmpty() ? null : java.sql.Date.valueOf(dob));
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) System.out.println("✔  Patient registered with ID: " + rs.getInt(1));
        }
    }

    static void listPatients() throws SQLException {
        System.out.println("\n─── All Patients ───");
        String sql = "SELECT * FROM patients ORDER BY patient_id";
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            boolean found = false;
            while (rs.next()) {
                found = true;
                LocalDate dob = rs.getDate("dob") != null
                        ? rs.getDate("dob").toLocalDate() : null;
                System.out.println(new Patient(
                        rs.getInt("patient_id"), rs.getString("name"),
                        rs.getString("phone"), rs.getString("email"),
                        rs.getString("blood_group"), rs.getString("address"), dob));
            }
            if (!found) System.out.println("No patients found.");
        }
    }

    static void searchPatient(Scanner sc) throws SQLException {
        System.out.print("\nSearch by name or phone: ");
        String q = "%" + sc.nextLine().trim() + "%";
        String sql = "SELECT * FROM patients WHERE name LIKE ? OR phone LIKE ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, q); ps.setString(2, q);
            ResultSet rs = ps.executeQuery();
            boolean found = false;
            while (rs.next()) {
                found = true;
                LocalDate dob = rs.getDate("dob") != null
                        ? rs.getDate("dob").toLocalDate() : null;
                System.out.println(new Patient(
                        rs.getInt("patient_id"), rs.getString("name"),
                        rs.getString("phone"), rs.getString("email"),
                        rs.getString("blood_group"), rs.getString("address"), dob));
            }
            if (!found) System.out.println("No matching patients.");
        }
    }

    static void updatePatient(Scanner sc) throws SQLException {
        System.out.print("\nEnter patient ID to update: ");
        int id = Integer.parseInt(sc.nextLine().trim());
        System.out.print("New phone (leave blank to skip): "); String phone = sc.nextLine().trim();
        System.out.print("New email (leave blank to skip): "); String email = sc.nextLine().trim();
        System.out.print("New address (leave blank to skip): "); String addr = sc.nextLine().trim();

        StringBuilder sb = new StringBuilder("UPDATE patients SET patient_id=patient_id");
        List<String> vals = new ArrayList<>();
        if (!phone.isEmpty()) { sb.append(",phone=?"); vals.add(phone); }
        if (!email.isEmpty()) { sb.append(",email=?"); vals.add(email); }
        if (!addr.isEmpty())  { sb.append(",address=?"); vals.add(addr); }
        sb.append(" WHERE patient_id=?");

        try (PreparedStatement ps = getConnection().prepareStatement(sb.toString())) {
            int i = 1;
            for (String v : vals) ps.setString(i++, v);
            ps.setInt(i, id);
            int rows = ps.executeUpdate();
            System.out.println(rows > 0 ? "✔  Patient updated." : "✘  Patient not found.");
        }
    }

    static void deletePatient(Scanner sc) throws SQLException {
        System.out.print("\nEnter patient ID to delete: ");
        int id = Integer.parseInt(sc.nextLine().trim());
        try (PreparedStatement ps = getConnection().prepareStatement(
                "DELETE FROM patients WHERE patient_id=?")) {
            ps.setInt(1, id);
            int rows = ps.executeUpdate();
            System.out.println(rows > 0 ? "✔  Patient deleted." : "✘  Patient not found.");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  DOCTOR SERVICE
    // ══════════════════════════════════════════════════════════════════════════

    static void addDoctor(Scanner sc) throws SQLException {
        System.out.println("\n─── Add New Doctor ───");
        System.out.print("Full name         : "); String name = sc.nextLine().trim();
        System.out.print("Specialization    : "); String spec = sc.nextLine().trim();
        System.out.print("Phone             : "); String phone = sc.nextLine().trim();
        System.out.print("Email             : "); String email = sc.nextLine().trim();
        System.out.print("Consultation fee  : "); double fee = Double.parseDouble(sc.nextLine().trim());

        String sql = "INSERT INTO doctors(name,specialization,phone,email,consultation_fee) VALUES(?,?,?,?,?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name); ps.setString(2, spec);
            ps.setString(3, phone); ps.setString(4, email);
            ps.setDouble(5, fee);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) System.out.println("✔  Doctor added with ID: " + rs.getInt(1));
        }
    }

    static void listDoctors() throws SQLException {
        System.out.println("\n─── All Doctors ───");
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM doctors ORDER BY doctor_id")) {
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.println(new Doctor(
                        rs.getInt("doctor_id"), rs.getString("name"),
                        rs.getString("specialization"), rs.getString("phone"),
                        rs.getString("email"), rs.getDouble("consultation_fee")));
            }
            if (!found) System.out.println("No doctors found.");
        }
    }

    static void deleteDoctor(Scanner sc) throws SQLException {
        System.out.print("\nEnter doctor ID to remove: ");
        int id = Integer.parseInt(sc.nextLine().trim());
        try (PreparedStatement ps = getConnection().prepareStatement(
                "DELETE FROM doctors WHERE doctor_id=?")) {
            ps.setInt(1, id);
            int rows = ps.executeUpdate();
            System.out.println(rows > 0 ? "✔  Doctor removed." : "✘  Doctor not found.");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  APPOINTMENT SERVICE
    // ══════════════════════════════════════════════════════════════════════════

    static void bookAppointment(Scanner sc) throws SQLException {
        System.out.println("\n─── Book Appointment ───");
        listPatients();
        System.out.print("\nPatient ID  : "); int pid = Integer.parseInt(sc.nextLine().trim());
        listDoctors();
        System.out.print("\nDoctor ID   : "); int did = Integer.parseInt(sc.nextLine().trim());
        System.out.print("Date & time (YYYY-MM-DD HH:MM): "); String dt = sc.nextLine().trim();
        System.out.print("Notes       : "); String notes = sc.nextLine().trim();

        // Check for slot conflict
        String check = "SELECT COUNT(*) FROM appointments WHERE doctor_id=? AND appt_datetime=? AND status='SCHEDULED'";
        try (PreparedStatement ps = getConnection().prepareStatement(check)) {
            ps.setInt(1, did);
            ps.setTimestamp(2, Timestamp.valueOf(dt + ":00"));
            ResultSet rs = ps.executeQuery();
            rs.next();
            if (rs.getInt(1) > 0) {
                System.out.println("✘  That slot is already booked for this doctor.");
                return;
            }
        }

        String sql = "INSERT INTO appointments(patient_id,doctor_id,appt_datetime,notes) VALUES(?,?,?,?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, pid); ps.setInt(2, did);
            ps.setTimestamp(3, Timestamp.valueOf(dt + ":00"));
            ps.setString(4, notes);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) System.out.println("✔  Appointment booked with ID: " + rs.getInt(1));
        }
    }

    static void listAppointments() throws SQLException {
        System.out.println("\n─── All Appointments ───");
        String sql = """
            SELECT a.appointment_id, a.patient_id, p.name AS pname,
                   a.doctor_id, d.name AS dname, a.appt_datetime, a.status, a.notes
            FROM appointments a
            JOIN patients p ON a.patient_id = p.patient_id
            JOIN doctors  d ON a.doctor_id  = d.doctor_id
            ORDER BY a.appt_datetime DESC""";
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.println(new Appointment(
                        rs.getInt("appointment_id"), rs.getInt("patient_id"),
                        rs.getString("pname"), rs.getInt("doctor_id"),
                        rs.getString("dname"),
                        rs.getTimestamp("appt_datetime").toLocalDateTime(),
                        rs.getString("status"), rs.getString("notes")));
            }
            if (!found) System.out.println("No appointments found.");
        }
    }

    static void updateAppointmentStatus(Scanner sc) throws SQLException {
        System.out.print("\nAppointment ID : "); int id = Integer.parseInt(sc.nextLine().trim());
        System.out.print("New status (SCHEDULED / COMPLETED / CANCELLED): ");
        String status = sc.nextLine().trim().toUpperCase();
        try (PreparedStatement ps = getConnection().prepareStatement(
                "UPDATE appointments SET status=? WHERE appointment_id=?")) {
            ps.setString(1, status); ps.setInt(2, id);
            int rows = ps.executeUpdate();
            System.out.println(rows > 0 ? "✔  Status updated." : "✘  Appointment not found.");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  BED / WARD SERVICE
    // ══════════════════════════════════════════════════════════════════════════

    static void addBed(Scanner sc) throws SQLException {
        System.out.print("\nWard name : "); String ward = sc.nextLine().trim();
        System.out.print("Bed type (General/ICU/Private): "); String type = sc.nextLine().trim();
        try (PreparedStatement ps = getConnection().prepareStatement(
                "INSERT INTO beds(ward,type) VALUES(?,?)")) {
            ps.setString(1, ward); ps.setString(2, type);
            ps.executeUpdate();
            System.out.println("✔  Bed added.");
        }
    }

    static void listBeds() throws SQLException {
        System.out.println("\n─── Bed Status ───");
        String sql = """
            SELECT b.bed_id, b.ward, b.type, b.occupied, b.patient_id, p.name
            FROM beds b LEFT JOIN patients p ON b.patient_id = p.patient_id
            ORDER BY b.ward, b.bed_id""";
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            boolean found = false;
            while (rs.next()) {
                found = true;
                Integer pid = rs.getObject("patient_id") != null ? rs.getInt("patient_id") : null;
                System.out.println(new Bed(rs.getInt("bed_id"), rs.getString("ward"),
                        rs.getString("type"), rs.getBoolean("occupied"),
                        pid, rs.getString("name")));
            }
            if (!found) System.out.println("No beds found.");
        }
    }

    static void admitPatient(Scanner sc) throws SQLException {
        System.out.println("\n─── Admit Patient ───");
        listBeds();
        System.out.print("\nBed ID     : "); int bedId = Integer.parseInt(sc.nextLine().trim());
        listPatients();
        System.out.print("\nPatient ID : "); int pid = Integer.parseInt(sc.nextLine().trim());
        try (PreparedStatement ps = getConnection().prepareStatement(
                "UPDATE beds SET occupied=TRUE, patient_id=? WHERE bed_id=? AND occupied=FALSE")) {
            ps.setInt(1, pid); ps.setInt(2, bedId);
            int rows = ps.executeUpdate();
            System.out.println(rows > 0 ? "✔  Patient admitted." : "✘  Bed not found or already occupied.");
        }
    }

    static void dischargePatient(Scanner sc) throws SQLException {
        System.out.print("\nBed ID to discharge: "); int bedId = Integer.parseInt(sc.nextLine().trim());
        try (PreparedStatement ps = getConnection().prepareStatement(
                "UPDATE beds SET occupied=FALSE, patient_id=NULL WHERE bed_id=?")) {
            ps.setInt(1, bedId);
            int rows = ps.executeUpdate();
            System.out.println(rows > 0 ? "✔  Patient discharged." : "✘  Bed not found.");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PRESCRIPTION SERVICE
    // ══════════════════════════════════════════════════════════════════════════

    static void addPrescription(Scanner sc) throws SQLException {
        System.out.println("\n─── Issue Prescription ───");
        listPatients();
        System.out.print("\nPatient ID      : "); int pid = Integer.parseInt(sc.nextLine().trim());
        listDoctors();
        System.out.print("\nDoctor ID       : "); int did = Integer.parseInt(sc.nextLine().trim());
        System.out.print("Appointment ID (0 if none): "); String apptStr = sc.nextLine().trim();
        Integer apptId = apptStr.equals("0") ? null : Integer.parseInt(apptStr);
        System.out.print("Medication      : "); String med = sc.nextLine().trim();
        System.out.print("Dosage          : "); String dosage = sc.nextLine().trim();
        System.out.print("Instructions    : "); String instr = sc.nextLine().trim();

        String sql = "INSERT INTO prescriptions(appointment_id,patient_id,doctor_id,medication,dosage,instructions) VALUES(?,?,?,?,?,?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (apptId == null) ps.setNull(1, Types.INTEGER); else ps.setInt(1, apptId);
            ps.setInt(2, pid); ps.setInt(3, did);
            ps.setString(4, med); ps.setString(5, dosage); ps.setString(6, instr);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) System.out.println("✔  Prescription issued with ID: " + rs.getInt(1));
        }
    }

    static void listPrescriptions() throws SQLException {
        System.out.println("\n─── All Prescriptions ───");
        String sql = """
            SELECT pr.prescription_id, pr.appointment_id, pr.patient_id,
                   p.name AS pname, d.name AS dname,
                   pr.medication, pr.dosage, pr.instructions, pr.prescribed_date
            FROM prescriptions pr
            JOIN patients p ON pr.patient_id = p.patient_id
            JOIN doctors  d ON pr.doctor_id  = d.doctor_id
            ORDER BY pr.prescribed_date DESC""";
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            boolean found = false;
            while (rs.next()) {
                found = true;
                LocalDate date = rs.getDate("prescribed_date") != null
                        ? rs.getDate("prescribed_date").toLocalDate() : LocalDate.now();
                System.out.println(new Prescription(
                        rs.getInt("prescription_id"), rs.getInt("appointment_id"),
                        rs.getInt("patient_id"), rs.getString("pname"),
                        rs.getString("dname"), rs.getString("medication"),
                        rs.getString("dosage"), rs.getString("instructions"), date));
            }
            if (!found) System.out.println("No prescriptions found.");
        }
    }

    static void searchPrescriptionsByPatient(Scanner sc) throws SQLException {
        System.out.print("\nPatient ID: "); int pid = Integer.parseInt(sc.nextLine().trim());
        String sql = """
            SELECT pr.*, p.name AS pname, d.name AS dname
            FROM prescriptions pr
            JOIN patients p ON pr.patient_id = p.patient_id
            JOIN doctors  d ON pr.doctor_id  = d.doctor_id
            WHERE pr.patient_id=?
            ORDER BY pr.prescribed_date DESC""";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, pid);
            ResultSet rs = ps.executeQuery();
            boolean found = false;
            while (rs.next()) {
                found = true;
                LocalDate date = rs.getDate("prescribed_date") != null
                        ? rs.getDate("prescribed_date").toLocalDate() : LocalDate.now();
                System.out.println(new Prescription(
                        rs.getInt("prescription_id"), rs.getInt("appointment_id"),
                        rs.getInt("patient_id"), rs.getString("pname"),
                        rs.getString("dname"), rs.getString("medication"),
                        rs.getString("dosage"), rs.getString("instructions"), date));
            }
            if (!found) System.out.println("No prescriptions for this patient.");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  BILLING SERVICE
    // ══════════════════════════════════════════════════════════════════════════

    static void createInvoice(Scanner sc) throws SQLException {
        System.out.println("\n─── Create Invoice ───");
        listPatients();
        System.out.print("\nPatient ID : "); int pid = Integer.parseInt(sc.nextLine().trim());

        String sql = "INSERT INTO invoices(patient_id) VALUES(?)";
        int invoiceId;
        try (PreparedStatement ps = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, pid); ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys(); rs.next();
            invoiceId = rs.getInt(1);
        }

        double total = 0;
        System.out.println("Add line items (type 'done' to finish):");
        while (true) {
            System.out.print("  Description (or 'done'): "); String desc = sc.nextLine().trim();
            if (desc.equalsIgnoreCase("done")) break;
            System.out.print("  Amount (KES)           : "); double amt = Double.parseDouble(sc.nextLine().trim());
            try (PreparedStatement ps = getConnection().prepareStatement(
                    "INSERT INTO invoice_items(invoice_id,description,amount) VALUES(?,?,?)")) {
                ps.setInt(1, invoiceId); ps.setString(2, desc); ps.setDouble(3, amt);
                ps.executeUpdate();
            }
            total += amt;
        }

        try (PreparedStatement ps = getConnection().prepareStatement(
                "UPDATE invoices SET total_amount=? WHERE invoice_id=?")) {
            ps.setDouble(1, total); ps.setInt(2, invoiceId); ps.executeUpdate();
        }
        System.out.printf("✔  Invoice #%d created. Total: KES %.2f%n", invoiceId, total);
    }

    static void listInvoices() throws SQLException {
        System.out.println("\n─── All Invoices ───");
        String sql = """
            SELECT i.invoice_id, i.patient_id, p.name, i.total_amount,
                   i.paid_amount, i.status, i.issue_date
            FROM invoices i JOIN patients p ON i.patient_id = p.patient_id
            ORDER BY i.issue_date DESC""";
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            boolean found = false;
            while (rs.next()) {
                found = true;
                LocalDate date = rs.getDate("issue_date") != null
                        ? rs.getDate("issue_date").toLocalDate() : LocalDate.now();
                System.out.println(new Invoice(
                        rs.getInt("invoice_id"), rs.getInt("patient_id"),
                        rs.getString("name"), rs.getDouble("total_amount"),
                        rs.getDouble("paid_amount"), rs.getString("status"), date));
            }
            if (!found) System.out.println("No invoices found.");
        }
    }

    static void recordPayment(Scanner sc) throws SQLException {
        System.out.print("\nInvoice ID   : "); int id = Integer.parseInt(sc.nextLine().trim());
        System.out.print("Amount paid  : "); double amount = Double.parseDouble(sc.nextLine().trim());

        String fetchSql = "SELECT total_amount, paid_amount FROM invoices WHERE invoice_id=?";
        double total, paid;
        try (PreparedStatement ps = getConnection().prepareStatement(fetchSql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) { System.out.println("✘  Invoice not found."); return; }
            total = rs.getDouble("total_amount");
            paid  = rs.getDouble("paid_amount");
        }

        double newPaid = paid + amount;
        String status = newPaid >= total ? "PAID" : "PARTIAL";
        try (PreparedStatement ps = getConnection().prepareStatement(
                "UPDATE invoices SET paid_amount=?, status=? WHERE invoice_id=?")) {
            ps.setDouble(1, newPaid); ps.setString(2, status); ps.setInt(3, id);
            ps.executeUpdate();
        }
        System.out.printf("✔  Payment recorded. Total paid: KES %.2f / KES %.2f (%s)%n",
                newPaid, total, status);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  STAFF SERVICE
    // ══════════════════════════════════════════════════════════════════════════

    static void addStaff(Scanner sc) throws SQLException {
        System.out.println("\n─── Add Staff Member ───");
        System.out.print("Full name   : "); String name = sc.nextLine().trim();
        System.out.print("Role        : "); String role = sc.nextLine().trim();
        System.out.print("Department  : "); String dept = sc.nextLine().trim();
        System.out.print("Phone       : "); String phone = sc.nextLine().trim();
        System.out.print("Email       : "); String email = sc.nextLine().trim();
        System.out.print("Salary      : "); double salary = Double.parseDouble(sc.nextLine().trim());

        try (PreparedStatement ps = getConnection().prepareStatement(
                "INSERT INTO staff(name,role,department,phone,email,salary) VALUES(?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name); ps.setString(2, role); ps.setString(3, dept);
            ps.setString(4, phone); ps.setString(5, email); ps.setDouble(6, salary);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) System.out.println("✔  Staff added with ID: " + rs.getInt(1));
        }
    }

    static void listStaff() throws SQLException {
        System.out.println("\n─── All Staff ───");
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM staff ORDER BY department,name")) {
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.println(new Staff(
                        rs.getInt("staff_id"), rs.getString("name"),
                        rs.getString("role"), rs.getString("department"),
                        rs.getString("phone"), rs.getString("email"),
                        rs.getDouble("salary")));
            }
            if (!found) System.out.println("No staff found.");
        }
    }

    static void deleteStaff(Scanner sc) throws SQLException {
        System.out.print("\nEnter staff ID to remove: ");
        int id = Integer.parseInt(sc.nextLine().trim());
        try (PreparedStatement ps = getConnection().prepareStatement(
                "DELETE FROM staff WHERE staff_id=?")) {
            ps.setInt(1, id);
            int rows = ps.executeUpdate();
            System.out.println(rows > 0 ? "✔  Staff removed." : "✘  Staff not found.");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  REPORTS
    // ══════════════════════════════════════════════════════════════════════════

    static void showDashboard() throws SQLException {
        Connection c = getConnection();
        System.out.println("\n╔══════════════════════════════════════════════╗");
        System.out.println("║         HOSPITAL DASHBOARD SUMMARY          ║");
        System.out.println("╚══════════════════════════════════════════════╝");

        String[] queries = {
            "SELECT COUNT(*) FROM patients",
            "SELECT COUNT(*) FROM doctors",
            "SELECT COUNT(*) FROM appointments WHERE status='SCHEDULED'",
            "SELECT COUNT(*) FROM beds WHERE occupied=TRUE",
            "SELECT COUNT(*) FROM beds",
            "SELECT COUNT(*) FROM invoices WHERE status='PENDING'",
            "SELECT IFNULL(SUM(total_amount),0) FROM invoices",
            "SELECT IFNULL(SUM(paid_amount),0) FROM invoices"
        };
        String[] labels = {
            "Total patients", "Total doctors",
            "Scheduled appointments", "Occupied beds",
            "Total beds", "Pending invoices",
            "Total billed (KES)", "Total collected (KES)"
        };

        for (int i = 0; i < queries.length; i++) {
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(queries[i])) {
                rs.next();
                System.out.printf("  %-30s : %s%n", labels[i], rs.getString(1));
            }
        }
    }

    static void todayAppointments() throws SQLException {
        System.out.println("\n─── Today's Appointments ───");
        String sql = """
            SELECT a.appointment_id, p.name AS pname, d.name AS dname,
                   a.appt_datetime, a.status, a.notes
            FROM appointments a
            JOIN patients p ON a.patient_id = p.patient_id
            JOIN doctors  d ON a.doctor_id  = d.doctor_id
            WHERE DATE(a.appt_datetime) = CURDATE()
            ORDER BY a.appt_datetime""";
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.printf("  [%d] %-20s → Dr.%-18s @ %s [%s]%n",
                        rs.getInt("appointment_id"),
                        rs.getString("pname"), rs.getString("dname"),
                        rs.getTimestamp("appt_datetime").toLocalDateTime()
                                .format(DateTimeFormatter.ofPattern("HH:mm")),
                        rs.getString("status"));
            }
            if (!found) System.out.println("No appointments today.");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  MENUS
    // ══════════════════════════════════════════════════════════════════════════

    static void patientMenu(Scanner sc) throws SQLException {
        while (true) {
            System.out.println("""
                \n─── Patient Management ───
                1. Register patient
                2. List all patients
                3. Search patient
                4. Update patient
                5. Delete patient
                0. Back""");
            System.out.print("Choice: ");
            switch (sc.nextLine().trim()) {
                case "1" -> registerPatient(sc);
                case "2" -> listPatients();
                case "3" -> searchPatient(sc);
                case "4" -> updatePatient(sc);
                case "5" -> deletePatient(sc);
                case "0" -> { return; }
                default  -> System.out.println("Invalid option.");
            }
        }
    }

    static void doctorMenu(Scanner sc) throws SQLException {
        while (true) {
            System.out.println("""
                \n─── Doctor Management ───
                1. Add doctor
                2. List all doctors
                3. Remove doctor
                0. Back""");
            System.out.print("Choice: ");
            switch (sc.nextLine().trim()) {
                case "1" -> addDoctor(sc);
                case "2" -> listDoctors();
                case "3" -> deleteDoctor(sc);
                case "0" -> { return; }
                default  -> System.out.println("Invalid option.");
            }
        }
    }

    static void appointmentMenu(Scanner sc) throws SQLException {
        while (true) {
            System.out.println("""
                \n─── Appointment Management ───
                1. Book appointment
                2. List all appointments
                3. Today's appointments
                4. Update appointment status
                0. Back""");
            System.out.print("Choice: ");
            switch (sc.nextLine().trim()) {
                case "1" -> bookAppointment(sc);
                case "2" -> listAppointments();
                case "3" -> todayAppointments();
                case "4" -> updateAppointmentStatus(sc);
                case "0" -> { return; }
                default  -> System.out.println("Invalid option.");
            }
        }
    }

    static void bedMenu(Scanner sc) throws SQLException {
        while (true) {
            System.out.println("""
                \n─── Ward / Bed Management ───
                1. Add bed
                2. List beds
                3. Admit patient to bed
                4. Discharge patient
                0. Back""");
            System.out.print("Choice: ");
            switch (sc.nextLine().trim()) {
                case "1" -> addBed(sc);
                case "2" -> listBeds();
                case "3" -> admitPatient(sc);
                case "4" -> dischargePatient(sc);
                case "0" -> { return; }
                default  -> System.out.println("Invalid option.");
            }
        }
    }

    static void prescriptionMenu(Scanner sc) throws SQLException {
        while (true) {
            System.out.println("""
                \n─── Pharmacy / Prescriptions ───
                1. Issue prescription
                2. List all prescriptions
                3. Prescriptions by patient
                0. Back""");
            System.out.print("Choice: ");
            switch (sc.nextLine().trim()) {
                case "1" -> addPrescription(sc);
                case "2" -> listPrescriptions();
                case "3" -> searchPrescriptionsByPatient(sc);
                case "0" -> { return; }
                default  -> System.out.println("Invalid option.");
            }
        }
    }

    static void billingMenu(Scanner sc) throws SQLException {
        while (true) {
            System.out.println("""
                \n─── Billing ───
                1. Create invoice
                2. List all invoices
                3. Record payment
                0. Back""");
            System.out.print("Choice: ");
            switch (sc.nextLine().trim()) {
                case "1" -> createInvoice(sc);
                case "2" -> listInvoices();
                case "3" -> recordPayment(sc);
                case "0" -> { return; }
                default  -> System.out.println("Invalid option.");
            }
        }
    }

    static void staffMenu(Scanner sc) throws SQLException {
        while (true) {
            System.out.println("""
                \n─── Staff / HR ───
                1. Add staff member
                2. List all staff
                3. Remove staff member
                0. Back""");
            System.out.print("Choice: ");
            switch (sc.nextLine().trim()) {
                case "1" -> addStaff(sc);
                case "2" -> listStaff();
                case "3" -> deleteStaff(sc);
                case "0" -> { return; }
                default  -> System.out.println("Invalid option.");
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ENTRY POINT
    // ══════════════════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║    HOSPITAL MANAGEMENT SYSTEM v1.0       ║");
        System.out.println("╚══════════════════════════════════════════╝");

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            initDatabase();
        } catch (ClassNotFoundException e) {
            System.err.println("✘  MySQL driver not found. Add mysql-connector-j to your classpath.");
            return;
        } catch (SQLException e) {
            System.err.println("✘  Database error: " + e.getMessage());
            return;
        }

        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.println("""
                \n╔══════════════════════════════════╗
                ║         MAIN MENU                ║
                ╠══════════════════════════════════╣
                ║  1. Patients                     ║
                ║  2. Doctors                      ║
                ║  3. Appointments                 ║
                ║  4. Wards & Beds                 ║
                ║  5. Prescriptions                ║
                ║  6. Billing                      ║
                ║  7. Staff / HR                   ║
                ║  8. Dashboard                    ║
                ║  0. Exit                         ║
                ╚══════════════════════════════════╝""");
            System.out.print("Choice: ");
            try {
                switch (sc.nextLine().trim()) {
                    case "1" -> patientMenu(sc);
                    case "2" -> doctorMenu(sc);
                    case "3" -> appointmentMenu(sc);
                    case "4" -> bedMenu(sc);
                    case "5" -> prescriptionMenu(sc);
                    case "6" -> billingMenu(sc);
                    case "7" -> staffMenu(sc);
                    case "8" -> showDashboard();
                    case "0" -> {
                        System.out.println("Goodbye.");
                        if (conn != null) try { conn.close(); } catch (SQLException ignored) {}
                        return;
                    }
                    default -> System.out.println("Invalid option.");
                }
            } catch (SQLException e) {
                System.err.println("✘  DB error: " + e.getMessage());
            } catch (NumberFormatException e) {
                System.err.println("✘  Invalid number entered.");
            }
        }
    }
}