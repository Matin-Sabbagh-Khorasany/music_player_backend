package com.musicplayer.server;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    // --- IMPORTANT: This part connects to the database you made in XAMPP ---
    private static final String DB_URL = "jdbc:mysql://localhost:3306/music_player_db";

    // The default username for XAMPP is "root".
    private static final String DB_USER = "root";

    // The default password for XAMPP is blank (an empty string).
    private static final String DB_PASSWORD = "";
    // --------------------------------------------------------------------

    /**
     * This method connects to the database, finds all songs that match the
     * category, and returns them as a list of simple text strings.
     * @param category The name of the category to search for (e.g., "Pop Stars 🎤")
     * @return A list of song data strings.
     */
    public List<String> getSongsByCategory(String category) {
        List<String> songDataStrings = new ArrayList<>();
        String sql = "SELECT * FROM songs WHERE category = ?";

        // This "try-with-resources" block is a safe way to connect to the database.
        // It automatically closes the connection when it's done.
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, category); // This safely adds the category name to the query
            ResultSet rs = pstmt.executeQuery(); // This runs the query

            // This loop goes through each song that the database found
            while (rs.next()) {
                // We build a single line of text with all the song's info, separated by "::"
                String songString = String.join("::",
                        rs.getString("title"),
                        rs.getString("artist"),
                        rs.getString("coverImagePath"),
                        rs.getString("audioUrl"),
                        String.valueOf(rs.getDouble("price")),
                        rs.getString("requiredAccessTier")
                );
                songDataStrings.add(songString); // Add the song's text to our list
            }
            System.out.println("DatabaseManager: Found " + songDataStrings.size() + " songs for category: " + category);

        } catch (SQLException e) {
            System.err.println("DatabaseManager: Error when fetching songs for category: " + category);
            e.printStackTrace();
        }
        return songDataStrings; // Return the final list of songs
    }
}