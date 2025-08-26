package com.musicplayer.server;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    // --- This part tells Java how to connect to the Laragon database ---
    // The database name "music_player_db" must be exactly what you created.
    private static final String DB_URL = "jdbc:mariadb://127.0.0.1:3306/music_player_db?allowPublicKeyRetrieval=true";
    private static final String DB_USER = "musicuser";

    // The default password for Laragon is blank (an empty string).
    private static final String DB_PASSWORD = "password123";
    // --------------------------------------------------------------------

    /**
     * This method connects to the database, finds all songs that match a
     * specific category, and returns them as a list of simple text strings.
     *
     * @param category The name of the category to search for (e.g., "ایرانی")
     * @return A list of song data strings, ready to be sent over the socket.
     */
    public List<String> getSongsByCategory(String category) {
        List<String> songDataStrings = new ArrayList<>();
        String sql = "SELECT * FROM songs WHERE category = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, category);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                // This version just gets the data and joins it, without any image processing.
                String songString = String.join("::",
                        rs.getString("title"),
                        rs.getString("artist"),
                        rs.getString("coverImagePath"),
                        rs.getString("audioUrl"),
                        rs.getString("sampleAudioUrl"), 
                        String.valueOf(rs.getDouble("price")),
                        rs.getString("requiredAccessTier")
                );
                songDataStrings.add(songString);
            }
            System.out.println("DatabaseManager: Found " + songDataStrings.size() + " songs for category: " + category);

        } catch (SQLException e) {
            System.err.println("DatabaseManager: Error when fetching songs for category: " + category);
            e.printStackTrace();
        }
        return songDataStrings;
    }
}