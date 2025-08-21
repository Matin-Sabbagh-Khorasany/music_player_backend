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
}
