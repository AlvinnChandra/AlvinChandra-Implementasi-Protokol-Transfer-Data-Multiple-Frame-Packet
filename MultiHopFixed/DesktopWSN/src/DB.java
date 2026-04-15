import java.sql.*;

public class DB {

    private static final String URL = "jdbc:postgresql://localhost:5432/iot_tubes";
    private static final String USER = "postgres";
    private static final String PASS = "ayamjantanada2";

    static {
        try {
            Class.forName("org.postgresql.Driver");
            System.out.println("PostgreSQL Driver loaded successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void insertSensing(
            int nodeId,
            int seq,
            double x,
            double y,
            double z,
            double magG,
            double magMs2
    ) {

        String sql = "INSERT INTO sensor_readings " +
                "(node_id, sequence_number, x_g, y_g, z_g, magnitude_g, magnitude_ms2) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement st = conn.prepareStatement(sql)) {

            st.setInt(1, nodeId);
            st.setInt(2, seq);
            st.setDouble(3, x);
            st.setDouble(4, y);
            st.setDouble(5, z);
            st.setDouble(6, magG);
            st.setDouble(7, magMs2);

            st.executeUpdate();

        } catch (Exception e) {
            System.err.println("DB INSERT FAILED!");
            e.printStackTrace();
        }
    }
}