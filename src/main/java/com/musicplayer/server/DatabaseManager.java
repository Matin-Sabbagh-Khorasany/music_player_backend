package com.musicplayer.server;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    // --- This part tells Java how to connect to the Laragon database ---
    // The database name "music_player_db" must be exactly what you created.
    private static final String DB_URL = "jdbc:mysql://localhost:3306/music_player_db?useSSL=false&serverTimezone=UTC";

    // The default username for Laragon is "root".
    private static final String DB_USER = "root";

    // The default password for Laragon is blank (an empty string).
    private static final String DB_PASSWORD = "";
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

        // This "try-with-resources" block is a safe way to connect to the database.
        // It automatically closes the connection when it's done, which is very important.
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, category); // This safely adds the category name to the SQL query
            ResultSet rs = pstmt.executeQuery(); // This runs the query to get the songs

            // This loop goes through each song that the database found
            while (rs.next()) {
                // We build a single line of text with all the song's info, separated by "::"
                // This format is easy for our Flutter app to understand.
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