package com.harish.dndscheduler;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.FormBody;
import okhttp3.Interceptor;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

/**
 * CaptchaRefreshManager handles timetable refresh by performing a complete login flow
 * behind the scenes. The university website requires a fresh session for each login,
 * so this class:
 * 1. Starts a completely fresh session (clears cookies)
 * 2. Fetches the login page to initialize session
 * 3. Gets a fresh captcha for the user to solve
 * 4. Performs complete login using stored credentials + user-provided captcha
 * 5. Fetches and saves the updated timetable data
 * 
 * From the user's perspective, they only need to enter the captcha, but behind the
 * scenes, this performs the exact same login flow as LoginActivity.
 * 
 * All methods are duplicated exactly from LoginActivity to ensure 100% consistency.
 */
public class CaptchaRefreshManager {
    private static final String TAG = "CaptchaRefreshManager";
    private static final String BASE_URL = "https://webstream.sastra.edu/sastrapwi/";
    private static final int CAPTCHA_DELAY_MS = 300;  // Reduced from 800ms for faster response
    private static final int HUMAN_DELAY_MS = 500;   // Reduced from 1500ms for faster refresh

    private final Context context;
    private final ExecutorService executorService;
    private final Handler mainHandler;
    private final OkHttpClient client;
    private final SharedPreferences prefs;
    private final CookieManager cookieManager;

    public interface CaptchaRefreshCallback {
        void onCaptchaFetched(Bitmap captchaBitmap);
        void onRefreshSuccess();
        void onRefreshError(String error);
        void onCredentialsRequired(); // When stored credentials are not available
    }

    public CaptchaRefreshManager(Context context) {
        this.context = context;
        this.executorService = Executors.newFixedThreadPool(3);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.prefs = context.getSharedPreferences("dnd_prefs", Context.MODE_PRIVATE);
        
        // Initialize cookie manager same as LoginActivity
        this.cookieManager = new CookieManager();
        this.cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        
        // Initialize OkHttpClient with proper cookie management same as LoginActivity
        this.client = new OkHttpClient.Builder()
                .cookieJar(new JavaNetCookieJar(cookieManager))
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(new RetryInterceptor(3))
                .followRedirects(false) // Handle redirects manually
                .followSslRedirects(false)
                .build();
    }

    public void fetchCaptcha(CaptchaRefreshCallback callback) {
        executorService.execute(() -> {
            try {
                // Clear any existing cookies to start fresh
                cookieManager.getCookieStore().removeAll();

                // Step 1: GET login page to initialize session
                Request loginPageRequest = new Request.Builder()
                        .url(BASE_URL)
                        .header("User-Agent", getBrowserUserAgent())
                        .build();

                try (Response response = client.newCall(loginPageRequest).execute()) {
                    if (!response.isSuccessful()) {
                        throw new Exception("Login page fetch failed: " + response.code());
                    }
                }

                // Step 2: Trigger captcha generation
                Request captchaInitRequest = new Request.Builder()
                        .url(BASE_URL + "stickyImg")
                        .header("User-Agent", getBrowserUserAgent())
                        .header("Referer", BASE_URL)
                        .build();

                try (Response response = client.newCall(captchaInitRequest).execute()) {
                    // Captcha init request completed
                }

                // Small delay to mimic human behavior (exactly like LoginActivity)
                Thread.sleep(CAPTCHA_DELAY_MS);

                // Step 3: Fetch captcha image (exactly like LoginActivity)
                String captchaUrl = BASE_URL + "stickyImg?ms=" + System.currentTimeMillis();
                Request captchaRequest = new Request.Builder()
                        .url(captchaUrl)
                        .header("User-Agent", getBrowserUserAgent())
                        .header("Accept", "image/webp,image/apng,image/*,*/*;q=0.8")
                        .header("Referer", BASE_URL)
                        .build();

                try (Response captchaResponse = client.newCall(captchaRequest).execute()) {
                    Log.d(TAG, "Captcha response code: " + captchaResponse.code());
                    
                    if (captchaResponse.isSuccessful() && captchaResponse.body() != null) {
                        InputStream is = captchaResponse.body().byteStream();
                        Bitmap bitmap = BitmapFactory.decodeStream(is);

                        if (bitmap != null) {
                            Log.d(TAG, "Captcha bitmap loaded successfully: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                            // Session is now established and ready for login - DO NOT clear cookies after this point!
                            mainHandler.post(() -> callback.onCaptchaFetched(bitmap));
                        } else {
                            throw new Exception("Failed to decode captcha bitmap");
                        }
                    } else {
                        String errorBody = captchaResponse.body() != null ? captchaResponse.body().string() : "No body";
                        Log.e(TAG, "Captcha fetch failed - Code: " + captchaResponse.code() + ", Body: " + errorBody);
                        throw new Exception("Captcha fetch failed: " + captchaResponse.code());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching captcha", e);
                mainHandler.post(() -> callback.onRefreshError("Failed to load captcha"));
            }
        });
    }

    public void performRefreshWithCaptcha(String captcha, CaptchaRefreshCallback callback) {
        // Get stored credentials from last successful login
        final String regNo = prefs.getString("last_username", "");
        final String password = prefs.getString("last_password", "");

        if (regNo.isEmpty() || password.isEmpty()) {
            mainHandler.post(() -> callback.onCredentialsRequired());
            return;
        }

        executorService.execute(() -> {
            try {
                // Human-like delay before login (reduced for faster performance)
                Thread.sleep(200);

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
                    // Handle redirect manually
                    if (loginResponse.code() == 302) {
                        String location = loginResponse.header("Location");

                        if (location != null && location.contains("home.jsp")) {
                            // Follow the redirect (exactly like LoginActivity)
                            followRedirectAndFetchTimetable(location, callback);
                        } else {
                            handleRefreshFailure("Login failed - unexpected redirect", callback);
                        }
                    } else {
                        String responseBody = loginResponse.body() != null ? loginResponse.body().string() : "";

                        if (isLoginSuccessful(loginResponse, responseBody)) {
                            fetchTimetableAndSave(callback);
                        } else {
                            handleLoginFailure(responseBody, callback);
                        }
                    }
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onRefreshError("Login failed. Please check your internet connection and try again."));
            }
        });
    }

    private void followRedirectAndFetchTimetable(String redirectUrl, CaptchaRefreshCallback callback) {
        try {
            // Handle relative URLs (exactly like LoginActivity)
            String fullUrl = redirectUrl.startsWith("http") ? redirectUrl : BASE_URL + redirectUrl;

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
                if (homeResponse.isSuccessful()) {
                    // Small delay before fetching timetable (exactly like LoginActivity)
                    Thread.sleep(HUMAN_DELAY_MS);
                    fetchTimetableAndSave(callback);
                } else {
                    throw new Exception("Home page fetch failed: " + homeResponse.code());
                }
            }
        } catch (Exception e) {
            mainHandler.post(() -> callback.onRefreshError("Error accessing home page"));
        }
    }

    private void fetchTimetableAndSave(CaptchaRefreshCallback callback) {
        try {
            String timetableUrl = BASE_URL + "academy/frmStudentTimetable.jsp";
            Request timetableRequest = new Request.Builder()
                    .url(timetableUrl)
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
                if (response.isSuccessful()) {
                    String fullHtml = response.body() != null ? response.body().string() : "";

                    // Extract only the inner <table cellspacing="1" ...> ... </table> (exactly like LoginActivity)
                    String timetableData = "";
                    int startIndex = fullHtml.indexOf("<table cellspacing=\"1\"");
                    int endIndex = fullHtml.indexOf("</table>", startIndex);
                    if (startIndex != -1 && endIndex != -1) {
                        timetableData = fullHtml.substring(startIndex, endIndex + "</table>".length());
                    } else {
                        timetableData = fullHtml;  // fallback to full HTML (exactly like LoginActivity)
                    }

                    // Get stored credentials to re-save them (exactly like LoginActivity)
                    String username = prefs.getString("last_username", "");
                    String password = prefs.getString("last_password", "");

                    // Save extracted HTML and store credentials (exactly like LoginActivity)
                    prefs.edit()
                            .putString("timetable_html", timetableData)
                            .putLong("timetable_fetch_time", System.currentTimeMillis())
                            .putString("last_username", username)
                            .putString("last_password", password)
                            .apply();

                    mainHandler.post(() -> callback.onRefreshSuccess());
                } else {
                    throw new Exception("Timetable fetch failed: " + response.code());
                }
            }
        } catch (Exception e) {
            mainHandler.post(() -> callback.onRefreshError("Failed to fetch timetable"));
        }
    }

    private boolean isLoginSuccessful(Response response, String body) {
        // Check for various success indicators (exactly like LoginActivity)
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

    private void handleRefreshFailure(String message, CaptchaRefreshCallback callback) {
        mainHandler.post(() -> callback.onRefreshError(message));
    }

    private void handleLoginFailure(String responseBody, CaptchaRefreshCallback callback) {
        mainHandler.post(() -> {
            if (responseBody != null) {
                // For quick refresh, most likely failure is invalid captcha since credentials are stored
                callback.onRefreshError("Invalid captcha. Please try again.");
            } else {
                callback.onRefreshError("Network error. Please check your connection and try again.");
            }
            // Note: In LoginActivity, this would call fetchLoginPageAndCaptcha() to refresh captcha
            // In our case, the dialog will handle showing a new captcha
        });
    }

    private String getBrowserUserAgent() {
        return "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36";
    }

    private void logCookies(String context) {
        // Enhanced cookie logging for debugging refresh issues (exactly like LoginActivity would do)
        try {
            if (cookieManager != null && cookieManager.getCookieStore() != null) {
                java.util.List<HttpCookie> cookies = cookieManager.getCookieStore().getCookies();
                Log.d(TAG, context + " - Cookie count: " + cookies.size());
                for (HttpCookie cookie : cookies) {
                    String value = cookie.getValue();
                    String shortValue = value.length() > 10 ? value.substring(0, 10) + "..." : value;
                    Log.d(TAG, context + " - Cookie: " + cookie.getName() + "=" + shortValue);
                }
            } else {
                Log.d(TAG, context + " - No cookie manager available");
            }
        } catch (Exception e) {
            Log.d(TAG, context + " - Error logging cookies: " + e.getMessage());
        }
    }

    // Method to store credentials when user logs in successfully
    public void storeCredentials(String username, String password) {
        prefs.edit()
                .putString("last_username", username)
                .putString("last_password", password)
                .apply();
        Log.d(TAG, "Credentials stored for future refresh");
    }

    // Method to clear stored credentials
    public void clearCredentials() {
        prefs.edit()
                .remove("last_username")
                .remove("last_password")
                .apply();
        Log.d(TAG, "Stored credentials cleared");
    }

    // Check if credentials are available for refresh
    public boolean hasStoredCredentials() {
        String username = prefs.getString("last_username", "");
        String password = prefs.getString("last_password", "");
        return !username.isEmpty() && !password.isEmpty();
    }

    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

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
}