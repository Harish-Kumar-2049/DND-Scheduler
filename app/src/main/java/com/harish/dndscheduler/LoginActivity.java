package com.harish.dndscheduler;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;

import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.*;

import okhttp3.*;

public class LoginActivity extends AppCompatActivity {

    private EditText etRegister, etPassword, etCaptcha;
    private ImageView imgCaptcha;
    private ImageButton btnRefreshCaptcha;
    private Button btnLogin;
    private ProgressBar progressBar;

    private OkHttpClient client;
    private CookieManager cookieManager;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final String BASE_URL = "https://webstream.sastra.edu/sastrapwi/";
    private static final int CAPTCHA_DELAY_MS = 800; // Increased delay
    private static final int HUMAN_DELAY_MS = 1200; // Delay between major requests

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initializeViews();
        setupHttpClient();
        fetchLoginPageAndCaptcha();
    }

    private void initializeViews() {
        etRegister = findViewById(R.id.et_register_number);
        etPassword = findViewById(R.id.et_password);
        etCaptcha = findViewById(R.id.et_captcha);
        imgCaptcha = findViewById(R.id.img_captcha);
        btnRefreshCaptcha = findViewById(R.id.btn_refresh_captcha);
        btnLogin = findViewById(R.id.btn_login);
        progressBar = findViewById(R.id.progress_bar);

        btnRefreshCaptcha.setOnClickListener(v -> {
            clearCaptcha();
            fetchLoginPageAndCaptcha();
        });

        btnLogin.setOnClickListener(v -> {
            if (validateInputs()) {
                performLogin();
            }
        });
    }

    private void setupHttpClient() {
        cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

        client = new OkHttpClient.Builder()
                .cookieJar(new JavaNetCookieJar(cookieManager))
                .connectTimeout(30, TimeUnit.SECONDS) // Increased timeout
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(new RetryInterceptor(3)) // Increased retries
                .followRedirects(false) // Handle redirects manually
                .followSslRedirects(false)
                .build();
    }

    private void fetchLoginPageAndCaptcha() {
        showLoading(true);

        executorService.execute(() -> {
            try {
                // Step 1: GET login page to initialize session
                Request loginPageRequest = new Request.Builder()
                        .url(BASE_URL)
                        .header("User-Agent", getBrowserUserAgent())
                        .build();

                try (Response response = client.newCall(loginPageRequest).execute()) {
                    logCookies("After login page GET");
                }

                // Step 2: Trigger captcha generation
                Request captchaInitRequest = new Request.Builder()
                        .url(BASE_URL + "stickyImg")
                        .header("User-Agent", getBrowserUserAgent())
                        .header("Referer", BASE_URL)
                        .build();

                try (Response response = client.newCall(captchaInitRequest).execute()) {
                    logCookies("After captcha init GET");
                }

                // Small delay to mimic human behavior
                Thread.sleep(CAPTCHA_DELAY_MS);

                // Step 3: Fetch captcha image
                String captchaUrl = BASE_URL + "stickyImg?ms=" + System.currentTimeMillis();
                Request captchaRequest = new Request.Builder()
                        .url(captchaUrl)
                        .header("User-Agent", getBrowserUserAgent())
                        .header("Accept", "image/webp,image/apng,image/*,*/*;q=0.8")
                        .header("Referer", BASE_URL)
                        .build();

                try (Response captchaResponse = client.newCall(captchaRequest).execute()) {
                    if (captchaResponse.isSuccessful() && captchaResponse.body() != null) {
                        InputStream is = captchaResponse.body().byteStream();
                        Bitmap bitmap = BitmapFactory.decodeStream(is);

                        mainHandler.post(() -> {
                            imgCaptcha.setImageBitmap(bitmap);
                            showLoading(false);
                        });
                    } else {
                        throw new Exception("Captcha fetch failed: " + captchaResponse.code());
                    }
                }
            } catch (Exception e) {
                Log.e("CaptchaError", "Captcha loading error", e);
                mainHandler.post(() -> {
                    showLoading(false);
                    // Error loading captcha - removed toast
                });
            }
        });
    }

    private void performLogin() {
        final String regNo = etRegister.getText().toString().trim();
        final String password = etPassword.getText().toString().trim();
        final String captcha = etCaptcha.getText().toString().trim();

        showLoading(true);

        executorService.execute(() -> {
            try {
                Log.d("LoginFlow", "Step 3: Performing login");
                logCookies("Before login POST");

                // Human-like delay before login
                Thread.sleep(500);

                FormBody formBody = new FormBody.Builder()
                        .add("txtRegNumber", regNo)
                        .add("txtPwd", password)
                        .add("answer", captcha)
                        .add("txtPA", "1")
                        .build();

                Request loginRequest = new Request.Builder()
                        .url(BASE_URL)
                        .post(formBody)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Origin", "https://webstream.sastra.edu")
                        .header("Referer", BASE_URL)
                        .header("User-Agent", getBrowserUserAgent())
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.5")
                        .header("Accept-Encoding", "gzip, deflate")
                        .header("DNT", "1")
                        .header("Connection", "keep-alive")
                        .header("Upgrade-Insecure-Requests", "1")
                        .build();

                try (Response loginResponse = client.newCall(loginRequest).execute()) {
                    Log.d("LoginFlow", "Login response: " + loginResponse.code());

                    // Handle redirect manually
                    if (loginResponse.code() == 302) {
                        String location = loginResponse.header("Location");
                        Log.d("LoginFlow", "Redirect location: " + location);

                        if (location != null && location.contains("home.jsp")) {
                            Log.d("LoginFlow", "Login successful - redirecting to home");
                            // Follow the redirect
                            followRedirectAndFetchTimetable(location);
                        } else {
                            handleLoginFailure("Login failed - unexpected redirect");
                        }
                    } else {
                        String responseBody = loginResponse.body() != null ? loginResponse.body().string() : "";
                        Log.d("LoginFlow", "Login response body length: " + responseBody.length());

                        if (isLoginSuccessful(loginResponse, responseBody)) {
                            fetchTimetableAndProceed();
                        } else {
                            handleLoginFailure(responseBody);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("LoginError", "Login error", e);
                mainHandler.post(() -> {
                    showLoading(false);
                    // Show network error to user
                    Toast.makeText(LoginActivity.this, "Login failed. Please check your internet connection and try again.", Toast.LENGTH_LONG).show();
                    fetchLoginPageAndCaptcha();
                });
            }
        });
    }

    private void followRedirectAndFetchTimetable(String redirectUrl) {
        try {
            // Handle relative URLs
            String fullUrl = redirectUrl.startsWith("http") ? redirectUrl : BASE_URL + redirectUrl;

            Log.d("LoginFlow", "Following redirect to: " + fullUrl);

            Request homeRequest = new Request.Builder()
                    .url(fullUrl)
                    .header("User-Agent", getBrowserUserAgent())
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .header("Accept-Encoding", "gzip, deflate")
                    .header("DNT", "1")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .build();

            try (Response homeResponse = client.newCall(homeRequest).execute()) {
                Log.d("LoginFlow", "Home page response: " + homeResponse.code());

                if (homeResponse.isSuccessful()) {
                    // Small delay before fetching timetable
                    Thread.sleep(HUMAN_DELAY_MS);
                    fetchTimetableAndProceed();
                } else {
                    throw new Exception("Home page fetch failed: " + homeResponse.code());
                }
            }
        } catch (Exception e) {
            Log.e("LoginError", "Redirect handling error", e);
            mainHandler.post(() -> {
                showLoading(false);
                // Error following redirect - removed toast
            });
        }
    }

    private boolean isLoginSuccessful(Response response, String body) {
        // Check for various success indicators
        boolean hasHomeJsp = body.contains("home.jsp");
        boolean hasSuccessIndicator = body.contains("welcome") || body.contains("Welcome") || body.contains("dashboard");
        boolean isRedirectToHome = response.isRedirect() && response.header("Location") != null
                && response.header("Location").contains("home.jsp");

        // Check for failure indicators
        boolean hasError = body.toLowerCase().contains("invalid") ||
                body.toLowerCase().contains("error") ||
                body.toLowerCase().contains("incorrect");

        return (hasHomeJsp || hasSuccessIndicator || isRedirectToHome) && !hasError;
    }

    private void handleLoginFailure(String responseBody) {
        Log.d("LoginFlow", "Login failed. Response body: " + responseBody.substring(0, Math.min(500, responseBody.length())));

        mainHandler.post(() -> {
            showLoading(false);

            String errorMessage = "Login failed. Please check your credentials.";

            if (responseBody.toLowerCase().contains("captcha")) {
                errorMessage = "Invalid CAPTCHA. Please try again.";
            } else if (responseBody.toLowerCase().contains("invalid") || responseBody.toLowerCase().contains("incorrect")) {
                errorMessage = "Invalid credentials. Please check your registration number and password.";
            }

            // Show error message to user
            Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            fetchLoginPageAndCaptcha(); // Refresh captcha
        });
    }

    private void fetchTimetableAndProceed() {
        executorService.execute(() -> {
            try {
                Log.d("LoginFlow", "Step 4: Fetching timetable");

                Request timetableRequest = new Request.Builder()
                        .url(BASE_URL + "academy/frmStudentTimetable.jsp")
                        .header("User-Agent", getBrowserUserAgent())
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.5")
                        .header("Accept-Encoding", "gzip, deflate")
                        .header("Referer", BASE_URL + "usermanager/home.jsp")
                        .header("DNT", "1")
                        .header("Connection", "keep-alive")
                        .header("Upgrade-Insecure-Requests", "1")
                        .build();

                try (Response response = client.newCall(timetableRequest).execute()) {
                    Log.d("LoginFlow", "Timetable response: " + response.code());

                    if (response.isSuccessful()) {
                        String fullHtml = response.body() != null ? response.body().string() : "";

                        // ✅ Extract only the inner <table cellspacing="1" ...> ... </table>
                        String timetableData = "";
                        int startIndex = fullHtml.indexOf("<table cellspacing=\"1\"");
                        int endIndex = fullHtml.indexOf("</table>", startIndex);
                        if (startIndex != -1 && endIndex != -1) {
                            timetableData = fullHtml.substring(startIndex, endIndex + "</table>".length());
                            Log.d("TimetableFix", "Timetable table extracted, length: " + timetableData.length());
                        } else {
                            Log.w("TimetableFix", "Timetable <table> not found");
                            timetableData = fullHtml;  // fallback to full HTML
                        }

                        // ✅ Save extracted HTML
                        SharedPreferences prefs = getSharedPreferences("dnd_prefs", MODE_PRIVATE);
                        prefs.edit()
                                .putString("timetable_html", timetableData)
                                .putLong("timetable_fetch_time", System.currentTimeMillis())
                                .apply();

                        mainHandler.post(() -> {
                            showLoading(false);
                            // Timetable saved - removed toast
                            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                            startActivity(intent);
                            finish();
                        });
                    } else {
                        throw new Exception("Timetable fetch failed: " + response.code());
                    }
                }
            } catch (Exception e) {
                Log.e("TimetableError", "Timetable fetch error", e);
                mainHandler.post(() -> {
                    showLoading(false);
                    // Error fetching timetable - removed toast
                });
            }
        });
    }

    /**
     * ✅ Helper method to validate timetable data
     */
    private boolean validateTimetableData(String timetableHtml) {
        if (timetableHtml == null || timetableHtml.trim().isEmpty()) {
            Log.d("TimetableValidation", "Timetable HTML is null or empty");
            return false;
        }

        // Check for common timetable indicators
        boolean hasTable = timetableHtml.toLowerCase().contains("<table") ||
                timetableHtml.toLowerCase().contains("timetable");

        boolean hasTimeFormat = timetableHtml.matches(".*\\d{1,2}:\\d{2}.*") || // HH:MM format
                timetableHtml.matches(".*\\d{1,2}\\s*[AP]M.*"); // AM/PM format

        boolean hasClassInfo = timetableHtml.toLowerCase().contains("class") ||
                timetableHtml.toLowerCase().contains("subject") ||
                timetableHtml.toLowerCase().contains("period");

        Log.d("TimetableValidation", "Has table: " + hasTable);
        Log.d("TimetableValidation", "Has time format: " + hasTimeFormat);
        Log.d("TimetableValidation", "Has class info: " + hasClassInfo);

        return hasTable && (hasTimeFormat || hasClassInfo);
    }

    // Utility methods
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!show);
        btnRefreshCaptcha.setEnabled(!show);
    }

    private boolean validateInputs() {
        if (etRegister.getText().toString().trim().isEmpty()) {
            etRegister.setError("Registration number is required");
            etRegister.requestFocus();
            return false;
        }
        if (etPassword.getText().toString().trim().isEmpty()) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return false;
        }
        if (etCaptcha.getText().toString().trim().isEmpty()) {
            etCaptcha.setError("CAPTCHA is required");
            etCaptcha.requestFocus();
            return false;
        }
        return true;
    }

    private void clearCaptcha() {
        etCaptcha.setText("");
        imgCaptcha.setImageBitmap(null);
    }

    private String getBrowserUserAgent() {
        // Use the same mobile user agent as your original code
        return "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36";
    }

    private void logCookies(String context) {
        List<HttpCookie> cookies = cookieManager.getCookieStore().getCookies();
        Log.d("CookieDebug", context + " - Total cookies: " + cookies.size());
        for (HttpCookie c : cookies) {
            Log.d("CookieDebug", context + ": " + c.getName() + "=" + c.getValue());
        }
    }

    // Enhanced Retry Interceptor
    private static class RetryInterceptor implements Interceptor {
        private final int maxRetries;

        RetryInterceptor(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            Response response = null;
            IOException exception = null;

            for (int i = 0; i <= maxRetries; i++) {
                try {
                    response = chain.proceed(request);

                    // Don't retry on client errors (4xx) except for specific cases
                    if (response.code() >= 400 && response.code() < 500 && response.code() != 429) {
                        return response;
                    }

                    if (response.isSuccessful() || response.code() == 302) {
                        return response;
                    }

                } catch (IOException e) {
                    exception = e;
                    Log.w("RetryInterceptor", "Attempt " + (i + 1) + " failed: " + e.getMessage());
                }

                if (i < maxRetries) {
                    try {
                        // Exponential backoff
                        long delay = (long) (1000 * Math.pow(2, i));
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            if (exception != null) throw exception;
            if (response != null) return response;
            throw new IOException("Maximum retries exceeded");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}