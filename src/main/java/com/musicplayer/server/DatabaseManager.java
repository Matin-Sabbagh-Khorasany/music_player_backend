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
    public List<String> getSongsByCategory(String category, String sortCriteria) {
        List<String> songDataStrings = new ArrayList<>();
        String orderByClause = ""; // Default is no sorting

        // --- NEW SORTING LOGIC ---
        // We use a switch statement to safely build the sorting part of our SQL query.
        // This prevents a security issue called SQL Injection.
        switch (sortCriteria) {
            case "rating_desc": // "desc" means descending, or High to Low
                orderByClause = " ORDER BY averageRating DESC";
                break;
            case "rating_asc": // "asc" means ascending, or Low to High
                orderByClause = " ORDER BY averageRating ASC";
                break;
            // We can add more sort options here later, like by price or title.
        }
        // -------------------------

        // We add our safe orderByClause to the end of the main SQL command.
        String sql = "SELECT * FROM songs WHERE category = ?" + orderByClause;

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, category);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String songString = String.join("::",
                        rs.getString("title"),
                        rs.getString("artist"),
                        rs.getString("coverImagePath"),
                        rs.getString("audioUrl"),
                        rs.getString("sampleAudioUrl"),
                        String.valueOf(rs.getDouble("price")),
                        rs.getString("requiredAccessTier"),
                        String.valueOf(rs.getDouble("averageRating"))
                );
                songDataStrings.add(songString);
            }
            System.out.println("DatabaseManager: Found " + songDataStrings.size() + " songs for category '" + category + "' sorted by '" + sortCriteria + "'");

        } catch (SQLException e) {
            System.err.println("DatabaseManager: Error when fetching songs.");
            e.printStackTrace();
        }
        return songDataStrings;
    }
}