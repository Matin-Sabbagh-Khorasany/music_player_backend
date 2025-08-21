package com.musicplayer.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final UserService userService;
    private PrintWriter out;
    private BufferedReader in;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        this.userService = new UserService();
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
}