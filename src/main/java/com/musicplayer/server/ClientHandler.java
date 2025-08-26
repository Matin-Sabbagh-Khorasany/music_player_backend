package com.musicplayer.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final UserService userService;
    private final DatabaseManager dbManager;
    private PrintWriter out;
    private BufferedReader in;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        this.userService = new UserService();
        this.dbManager = new DatabaseManager();
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("Received from client: " + inputLine);
                processCommand(inputLine);
            }

        } catch (IOException e) {
            System.out.println("An error occurred with client " + clientSocket.getInetAddress().getHostAddress() + ": "
                    + e.getMessage());
        } finally {
            try {
                in.close();
                out.close();
                clientSocket.close();
                System.out.println("Client disconnected: " + clientSocket.getInetAddress().getHostAddress());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void processCommand(String command) {
        System.out.println("SERVER RECEIVED RAW COMMAND: [" + command + "]");
        // مرحله 1: اگر ورودی null است، خارج شو
        if (command == null) {
            return;
        }

        // مرحله 2: حذف تمام فضاهای خالی از ابتدا و انتها
        String trimmedCommand = command.trim();

        // مرحله 3: اگر دستور بعد از تمیز کردن خالی بود، آن را نادیده بگیر
        if (trimmedCommand.isEmpty()) {
            return;
        }

        // مرحله 4 (مهم): حذف هرگونه کاراکتر نامرئی یا کنترلی از ابتدای دستور
        // این کار کد ما را در برابر ورودی‌های کثیف از telnet مقاوم می‌کند
        String cleanCommand = trimmedCommand.replaceAll("^\\s*\\p{C}+", "");

        // مرحله 5: تقسیم کردن دستور تمیز شده
        String[] parts = cleanCommand.split("::");
        String commandType = parts[0];

        // (اختیاری) اضافه کردن لاگ برای دیباگ کردن در آینده
        // System.out.println("Processing command type: [" + commandType + "]");

        try {
            switch (commandType) {
                case "REGISTER":
                    if (parts.length == 4) {
                        handleRegister(parts[1], parts[2], parts[3]);
                    } else {
                        out.println("ERROR::INVALID_REGISTER_FORMAT");
                    }
                    break;

                case "LOGIN":
                    if (parts.length == 3) {
                        handleLogin(parts[1], parts[2]);
                    } else {
                        out.println("ERROR::INVALID_LOGIN_FORMAT");
                    }
                    break;

                case "ADD_CREDIT":
                    if (parts.length == 3) {
                        handleUpdateCredit(parts[1], parts[2]);
                    } else {
                        out.println("ERROR::INVALID_CREDIT_FORMAT");
                    }
                    break;

                case "GET_SONGS_BY_CATEGORY":
                    if (parts.length == 3) {
                        // New command with sorting: GET_SONGS_BY_CATEGORY::Category::rating_desc
                        handleGetSongsByCategory(parts[1], parts[2]);
                    } else if (parts.length == 2) {
                        // Old command with default sorting
                        handleGetSongsByCategory(parts[1], "default");
                    } else {
                        out.println("ERROR::INVALID_SONG_REQUEST_FORMAT");
                    }
                    break;

                case "DELETE_ACCOUNT":
                    if (parts.length == 2) {
                        handleDeleteAccount(parts[1]);
                    } else {
                        out.println("ERROR::INVALID_DELETE_FORMAT");
                    }
                    break;

                case "UPDATE_PROFILE":
                    if (parts.length == 4) {
                        handleUpdateProfile(parts[1], parts[2], parts[3]);
                    } else {
                        out.println("ERROR::INVALID_UPDATE_FORMAT");
                    }
                    break;

                case "CHANGE_PASSWORD":
                    if (parts.length == 4) {
                        handleChangePassword(parts[1], parts[2], parts[3]);
                    } else {
                        out.println("ERROR::INVALID_PASSWORD_CHANGE_FORMAT");
                    }
                    break;

                default:
                    out.println("ERROR::UNKNOWN_COMMAND::" + commandType);
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error processing command: " + cleanCommand);
            e.printStackTrace();
            out.println("ERROR::COMMAND_PROCESSING_FAILED");
        }
    }

    private void handleRegister(String username, String email, String password) {
        String response = userService.registerUser(username, email, password);
        out.println(response);
    }

    private void handleLogin(String username, String password) {
        String response = userService.loginUser(username, password);
        out.println(response);
    }

    private void handleUpdateCredit(String username, String amountStr) {
        try {
            double amount = Double.parseDouble(amountStr);
            String response = userService.addCreditToUser(username, amount);
            out.println(response);
        } catch (NumberFormatException e) {
            out.println("ADD_CREDIT_FAILED::INVALID_AMOUNT");
        }
    }

    /**
     * This is the "recipe" for handling a request for songs.
     * It uses the DatabaseManager to get the songs and sends them to the app.
     *
     * @param category The category name received from the app.
     */
    private void handleGetSongsByCategory(String category, String sortCriteria) {
        // Pass the category and the sort instruction to the DatabaseManager
        List<String> songs = dbManager.getSongsByCategory(category, sortCriteria);

        System.out.println("SERVER FOUND " + songs.size() + " SONGS. NOW SENDING TO APP...");
        for (String songData : songs) {
            out.println("SONG_DATA::" + songData);
        }
        out.println("SONGS_END");
        out.flush();
    }

    private void handleDeleteAccount(String username) {
        String response = userService.deleteUser(username);
        out.println(response);
    }

    private void handleUpdateProfile(String oldUsername, String newFullName, String newEmail) {
        String response = userService.updateProfile(oldUsername, newFullName, newEmail);
        out.println(response);
    }

    private void handleChangePassword(String username, String oldPassword, String newPassword) {
        String response = userService.changePassword(username, oldPassword, newPassword);
        out.println(response);
    }
}