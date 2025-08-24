package com.musicplayer.server;

import org.mindrot.jbcrypt.BCrypt;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class UserService {

    private static final String USER_DATA_PATH = "data/users/";

    public String registerUser(String username, String email, String password) {
        File userFile = new File(USER_DATA_PATH + username + ".txt");
        if (userFile.exists()) {
            return "REGISTER_FAILED::USERNAME_EXISTS";
        }

        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

        try (FileWriter writer = new FileWriter(userFile)) {
            writer.write("password=" + hashedPassword + "\n");
            writer.write("email=" + email + "\n");
            writer.write("credit=0.0\n");
            writer.write("subscription_tier=none\n");
            writer.write("subscription_expiry=\n");

            System.out.println("User file created for: " + username);
            return "REGISTER_SUCCESS";
        } catch (IOException e) {
            e.printStackTrace();
            return "REGISTER_FAILED::SERVER_ERROR";
        }
    }

    public String loginUser(String username, String password) {
        File userFile = new File(USER_DATA_PATH + username + ".txt");
        if (!userFile.exists()) {
            return "LOGIN_FAILED::USER_NOT_FOUND";
        }

        try {
            List<String> lines = Files.readAllLines(Paths.get(userFile.getPath()));
            String storedHashedPassword = "";
            for (String line : lines) {
                if (line.startsWith("password=")) {
                    storedHashedPassword = line.substring("password=".length());
                    break;
                }
            }

            if (BCrypt.checkpw(password, storedHashedPassword)) {
                return "LOGIN_SUCCESS";
            } else {
                return "LOGIN_FAILED::INVALID_PASSWORD";
            }

        } catch (IOException e) {
            e.printStackTrace();
            return "LOGIN_FAILED::SERVER_ERROR";
        }
    }

    public String addCreditToUser(String username, double amountToAdd) {
        lock.writeLock().lock(); // Lock the file for writing

        File originalFile = new File(USER_DATA_FILE);
        // Create a temporary file in the same directory
        File tempFile = new File(USER_DATA_DIRECTORY + "/users.tmp");

        if (!originalFile.exists()) {
            lock.writeLock().unlock();
            return "ADD_CREDIT_FAILED::SERVER_ERROR";
        }

        boolean userFound = false;
        double newTotalCredit = 0.0;

        try (BufferedReader reader = new BufferedReader(new FileReader(originalFile));
             BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {

            String currentLine;
            while ((currentLine = reader.readLine()) != null) {
                String[] parts = currentLine.split(SEPARATOR, -1);

                // Check if this is the user we want to update
                if (!userFound && parts.length > 3 && parts[0].equals(username)) {
                    userFound = true;
                    try {
                        double currentCredit = Double.parseDouble(parts[3]);
                        newTotalCredit = currentCredit + amountToAdd;
                        parts[3] = String.valueOf(newTotalCredit); // Update the credit

                        // Write the MODIFIED line to the temp file
                        writer.write(String.join(SEPARATOR, parts) + System.lineSeparator());

                    } catch (NumberFormatException e) {
                        // This happens if parts[3] is not a valid number in the file
                        System.err.println("!!! Data format error for user: " + username);
                        e.printStackTrace();
                        lock.writeLock().unlock();
                        // Clean up the temp file before exiting
                        tempFile.delete();
                        return "ADD_CREDIT_FAILED::MALFORMED_DATA_FILE";
                    }
                } else {
                    // If it's not the user, write the original line to the temp file
                    writer.write(currentLine + System.lineSeparator());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            lock.writeLock().unlock();
            // Clean up the temp file
            tempFile.delete();
            return "ADD_CREDIT_FAILED::FILE_IO_ERROR";
        }

        // After successfully writing to the temp file, replace the original file
        if (userFound) {
            if (originalFile.delete()) {
                if (!tempFile.renameTo(originalFile)) {
                    // This is a critical error state, the original file is gone but temp couldn't be renamed
                    System.err.println("CRITICAL: Could not rename temp file. User data might be in users.tmp");
                    lock.writeLock().unlock();
                    return "ADD_CREDIT_FAILED::CRITICAL_SERVER_ERROR";
                }
            } else {
                System.err.println("Error: Could not delete original user file.");
                lock.writeLock().unlock();
                return "ADD_CREDIT_FAILED::FILE_LOCK_ERROR";
            }

            System.out.println("Successfully updated credit for user '" + username + "' to " + newTotalCredit);
            lock.writeLock().unlock(); // Unlock AFTER all file operations are done
            return "ADD_CREDIT_SUCCESS" + SEPARATOR + newTotalCredit;

        } else {
            // If the user was never found, just clean up and unlock
            tempFile.delete();
            lock.writeLock().unlock();
            return "ADD_CREDIT_FAILED::USER_NOT_FOUND";
        }
    }
}
