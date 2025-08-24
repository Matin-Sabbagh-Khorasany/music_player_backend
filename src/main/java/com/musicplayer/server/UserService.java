package com.musicplayer.server;

import org.mindrot.jbcrypt.BCrypt;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class UserService {

    // --- The single source of truth for our file path and format ---
    private static final String USER_DATA_DIRECTORY = "data";
    private static final String USER_DATA_FILE = USER_DATA_DIRECTORY + "/users.txt";
    private static final String SEPARATOR = "::";
    // ----------------------------------------------------------------

    // A single, static lock to protect our single file from being corrupted
    // by multiple users trying to write to it at the same time.
    private static final ReadWriteLock lock = new ReentrantReadWriteLock();

    // Constructor: This runs when UserService is created.
    // It makes sure the "data" directory and the "users.txt" file exist.
    public UserService() {
        try {
            Path dirPath = Paths.get(USER_DATA_DIRECTORY);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                System.out.println("Directory created: " + USER_DATA_DIRECTORY);
            }
            File userFile = new File(USER_DATA_FILE);
            if (userFile.createNewFile()) {
                System.out.println("User data file created: " + USER_DATA_FILE);
            }
        } catch (IOException e) {
            System.err.println("Error initializing user data storage file.");
            e.printStackTrace();
        }
    }

    /**
     * Registers a new user.
     * This method now uses the single users.txt file.
     */
    public String registerUser(String username, String email, String password) {
        lock.writeLock().lock(); // Lock the file for writing
        try {
            if (isUserExists(username, email)) {
                return "REGISTER_FAILED::USERNAME_OR_EMAIL_EXISTS";
            }

            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

            // Structure: username::email::hashed_password::credit::subscription_tier::subscription_expiry
            String newUserLine = String.join(SEPARATOR, username, email, hashedPassword, "0.0",      // Initial credit
                    "none",     // Initial subscription
                    "null"      // Initial expiry date
            );

            // Append the new user to the end of the single file
            try (FileWriter fw = new FileWriter(USER_DATA_FILE, true); BufferedWriter bw = new BufferedWriter(fw); java.io.PrintWriter out = new java.io.PrintWriter(bw)) {
                out.println(newUserLine);
                System.out.println("User registered successfully: " + username);
                return "REGISTER_SUCCESS";
            } catch (IOException e) {
                e.printStackTrace();
                return "REGISTER_FAILED::SERVER_ERROR";
            }
        } finally {
            lock.writeLock().unlock(); // Always unlock the file
        }
    }

    /**
     * Logs in a user.
     * This method now reads from the single users.txt file.
     */
    public String loginUser(String username, String password) {
        lock.readLock().lock();
        try {
            File userFile = new File(USER_DATA_FILE);
            if (!userFile.exists()) {
                return "LOGIN_FAILED::SERVER_ERROR";
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(userFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(SEPARATOR, -1);

                    // This now ONLY checks the username (at index 0)
                    if (parts.length > 2 && parts[0].equals(username)) {
                        String storedHashedPassword = parts[2];
                        if (BCrypt.checkpw(password, storedHashedPassword)) {
                            System.out.println("User logged in successfully: " + parts[0]);
                            String foundUsername = parts[0];
                            String foundEmail = parts[1];
                            // Return the success message WITH the user's data
                            return "LOGIN_SUCCESS::" + foundUsername + "::" + foundEmail;
                        } else {
                            System.out.println("Failed login attempt (invalid password) for user: " + parts[0]);
                            return "LOGIN_FAILED::INVALID_PASSWORD";
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                return "LOGIN_FAILED::SERVER_ERROR";
            }

            System.out.println("Failed login attempt (user not found): " + username);
            return "LOGIN_FAILED::USER_NOT_FOUND";

        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Adds credit to a user's account.
     * This is the robust "temp file" method we discussed, which works perfectly with our single file system.
     */
    public String addCreditToUser(String username, double amountToAdd) {
        lock.writeLock().lock(); // Lock the file for writing

        File originalFile = new File(USER_DATA_FILE);
        File tempFile = new File(USER_DATA_DIRECTORY + "/users.tmp");

        if (!originalFile.exists()) {
            lock.writeLock().unlock();
            return "ADD_CREDIT_FAILED::SERVER_ERROR";
        }

        boolean userFound = false;
        double newTotalCredit = 0.0;

        try (BufferedReader reader = new BufferedReader(new FileReader(originalFile)); BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {

            String currentLine;
            while ((currentLine = reader.readLine()) != null) {
                String[] parts = currentLine.split(SEPARATOR, -1);

                if (!userFound && parts.length > 3 && parts[0].equals(username)) {
                    userFound = true;
                    try {
                        double currentCredit = Double.parseDouble(parts[3]);
                        newTotalCredit = currentCredit + amountToAdd;
                        parts[3] = String.valueOf(newTotalCredit);
                        writer.write(String.join(SEPARATOR, parts) + System.lineSeparator());
                    } catch (NumberFormatException e) {
                        System.err.println("!!! Data format error in users.txt for user: " + username);
                        e.printStackTrace();
                        lock.writeLock().unlock();
                        tempFile.delete();
                        return "ADD_CREDIT_FAILED::MALFORMED_DATA_FILE";
                    }
                } else {
                    writer.write(currentLine + System.lineSeparator());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            lock.writeLock().unlock();
            tempFile.delete();
            return "ADD_CREDIT_FAILED::FILE_IO_ERROR";
        }

        if (userFound) {
            if (originalFile.delete()) {
                if (!tempFile.renameTo(originalFile)) {
                    System.err.println("CRITICAL: Could not rename temp file. User data may be in users.tmp");
                    lock.writeLock().unlock();
                    return "ADD_CREDIT_FAILED::CRITICAL_SERVER_ERROR";
                }
            } else {
                System.err.println("Error: Could not delete original user file.");
                lock.writeLock().unlock();
                return "ADD_CREDIT_FAILED::FILE_LOCK_ERROR";
            }

            System.out.println("Successfully updated credit for user '" + username + "' to " + newTotalCredit);
            lock.writeLock().unlock();
            return "ADD_CREDIT_SUCCESS" + SEPARATOR + newTotalCredit;

        } else {
            tempFile.delete();
            lock.writeLock().unlock();
            return "ADD_CREDIT_FAILED::USER_NOT_FOUND";
        }
    }

    /**
     * A private helper method to check if a user exists by username or email.
     * This is used by registerUser to prevent duplicates.
     */
    private boolean isUserExists(String username, String email) {
        // This method is called from within other methods that already have a lock,
        // so it doesn't need its own lock.
        File userFile = new File(USER_DATA_FILE);
        if (!userFile.exists()) {
            return false;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(userFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(SEPARATOR, -1);
                if (parts.length > 1) {
                    if (parts[0].equals(username) || parts[1].equalsIgnoreCase(email)) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}