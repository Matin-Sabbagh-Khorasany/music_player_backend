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

    // مسیر پوشه داده‌ها
    private static final String USER_DATA_DIRECTORY = "data";
    // نام فایل واحد برای ذخیره اطلاعات تمام کاربران
    private static final String USER_DATA_FILE = USER_DATA_DIRECTORY + "/users.txt";
    // جداکننده اطلاعات در هر خط
    private static final String SEPARATOR = "::";

    // یک قفل برای مدیریت دسترسی همزمان به فایل، تا از تداخل Thread ها جلوگیری شود
    private static final ReadWriteLock lock = new ReentrantReadWriteLock();

    // سازنده کلاس: در هنگام ساخته شدن، پوشه و فایل مورد نیاز را ایجاد می‌کند
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
            System.err.println("Error initializing user data storage.");
            e.printStackTrace();
        }
    }

    /**
     * متد ثبت‌نام کاربر جدید
     * ابتدا بررسی می‌کند که نام کاربری یا ایمیل تکراری نباشد، سپس کاربر جدید را به انتهای فایل اضافه می‌کند.
     */
    public String registerUser(String username, String email, String password) {
        lock.writeLock().lock(); // قفل نوشتن را فعال کن تا ترد دیگری همزمان فایل را تغییر ندهد
        try {
            // بررسی اینکه آیا کاربری با این نام کاربری یا ایمیل از قبل وجود دارد یا نه
            if (isUserExists(username, email)) {
                return "REGISTER_FAILED::USERNAME_OR_EMAIL_EXISTS";
            }

            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

            // ساختن خط جدید برای کاربر با اطلاعات اولیه
            // ساختار: username::email::hashed_password::credit::subscription_tier::subscription_expiry
            String newUserLine = String.join(SEPARATOR,
                    username,
                    email,
                    hashedPassword,
                    "0.0",          // اعتبار اولیه
                    "none",         // اشتراک اولیه
                    "null"          // تاریخ انقضای اولیه
            );

            // افزودن (Append) کاربر جدید به انتهای فایل
            try (FileWriter fw = new FileWriter(USER_DATA_FILE, true);
                 BufferedWriter bw = new BufferedWriter(fw);
                 java.io.PrintWriter out = new java.io.PrintWriter(bw)) {
                out.println(newUserLine);
                System.out.println("User registered successfully: " + username);
                return "REGISTER_SUCCESS";
            } catch (IOException e) {
                e.printStackTrace();
                return "REGISTER_FAILED::SERVER_ERROR";
            }

        } finally {
            lock.writeLock().unlock(); // حتما قفل را در انتها آزاد کن
        }
    }

    /**
     * متد ورود کاربر
     * فایل را خط به خط می‌خواند تا کاربر را پیدا کرده و رمز عبور را تایید کند.
     */
    public String loginUser(String username, String password) {
        lock.readLock().lock(); // قفل خواندن را فعال کن (چندین ترد می‌توانند همزمان بخوانند)
        try {
            File userFile = new File(USER_DATA_FILE);
            if (!userFile.exists()) {
                return "LOGIN_FAILED::SERVER_ERROR"; // فایل اصلا وجود ندارد
            }


            try (BufferedReader reader = new BufferedReader(new FileReader(userFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(SEPARATOR, -1); // -1 برای اینکه فیلدهای خالی هم در نظر گرفته شوند
                    // اندیس 0: username, اندیس 1: email, اندیس 2: password
                    if (parts.length > 2 && parts[0].equals(username)) {
                        String storedHashedPassword = parts[2];
                        if (BCrypt.checkpw(password, storedHashedPassword)) {
                            System.out.println("User logged in successfully: " + username);
                            return "LOGIN_SUCCESS";
                        } else {
                            System.out.println("Failed login attempt (invalid password) for user: " + username);
                            return "LOGIN_FAILED::INVALID_PASSWORD";
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                return "LOGIN_FAILED::SERVER_ERROR";
            }

            System.out.println("Failed login attempt (user not found) for user: " + username);
            return "LOGIN_FAILED::USER_NOT_FOUND";

        } finally {
            lock.readLock().unlock(); // قفل را آزاد کن
        }
    }

    /**
     * متد کمکی برای بررسی وجود کاربر بر اساس نام کاربری یا ایمیل
     * این متد به صورت private است و فقط داخل این کلاس استفاده می‌شود.
     */
    private boolean isUserExists(String username, String email) {
        // این متد نیازی به قفل‌گذاری جداگانه ندارد چون همیشه از داخل متدهای registerUser یا loginUser که خودشان قفل دارند، فراخوانی می‌شود.
        File userFile = new File(USER_DATA_FILE);
        if (!userFile.exists()) {
            return false;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(userFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(SEPARATOR, -1);
                if (parts.length > 1) {
                    // آیا نام کاربری یا ایمیل با ورودی مطابقت دارد؟ (بدون در نظر گرفتن بزرگی و کوچکی حروف برای ایمیل)
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
