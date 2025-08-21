package com.musicplayer.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class TestClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 12345;

    public static void main(String[] args) {
        System.out.println("Test Client is starting...");

        try (
                // یک سوکت برای اتصال به سرور ایجاد کن
                Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                // برای ارسال پیام به سرور
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                // برای دریافت پیام از سرور
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                // برای خواندن ورودی از کیبورد شما در ترمینال
                Scanner consoleScanner = new Scanner(System.in)) {
            System.out.println("Connected to the server. You can now send commands.");
            System.out.println("Type 'exit' to quit.");
            System.out.println("-----------------------------------------------------");

            String userInput;
            // یک حلقه برای اینکه بتوانید چندین دستور را پشت سر هم بفرستید
            while (true) {
                System.out.print("Enter command: ");
                userInput = consoleScanner.nextLine(); // ورودی را از شما می‌گیرد

                if ("exit".equalsIgnoreCase(userInput)) {
                    break; // اگر 'exit' تایپ کردید، از حلقه خارج شو
                }

                // دستور شما را برای سرور می‌فرستد
                out.println(userInput);
                System.out.println("Sent to server: " + userInput);

                // منتظر پاسخ از سرور می‌ماند و آن را چاپ می‌کند
                String serverResponse = in.readLine();
                System.out.println("Received from server: " + serverResponse);
                System.out.println("-----------------------------------------------------");
            }

        } catch (IOException e) {
            System.err.println("Client exception: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("Test Client has finished.");
    }
}
