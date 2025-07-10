package com.harish.dndscheduler;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.*;

public class LoginActivity extends AppCompatActivity {

    private EditText etRegister, etPassword, etCaptcha;
    private ImageView imgCaptcha;
    private Button btnRefreshCaptcha, btnLogin;
    private ProgressBar progressBar;

    private OkHttpClient client;
    private CookieManager cookieManager;

    private String captchaUrl = "";
    private String baseUrl = "https://webstream.sastra.edu/sastrapwi/";

    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etRegister = findViewById(R.id.et_register_number);
        etPassword = findViewById(R.id.et_password);
        etCaptcha = findViewById(R.id.et_captcha);
        imgCaptcha = findViewById(R.id.img_captcha);
        btnRefreshCaptcha = findViewById(R.id.btn_refresh_captcha);
        btnLogin = findViewById(R.id.btn_login);
        progressBar = findViewById(R.id.progress_bar);

        cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        client = new OkHttpClient.Builder()
                .cookieJar(new JavaNetCookieJar(cookieManager))
                .build();

        fetchLoginPageAndCaptcha(); // Initial load

        btnRefreshCaptcha.setOnClickListener(v -> fetchLoginPageAndCaptcha());
        btnLogin.setOnClickListener(v -> performLogin());
    }

    private void fetchLoginPageAndCaptcha() {
        progressBar.setVisibility(View.VISIBLE);

        executorService.execute(() -> {
            try {
                // Step 1: GET login page to initiate session
                Request request = new Request.Builder()
                        .url(baseUrl)
                        .build();

                Response response = client.newCall(request).execute();
                String html = response.body().string();
                response.close();

                // Log cookies for debugging
                List<java.net.HttpCookie> cookies = cookieManager.getCookieStore().getCookies();
                for (java.net.HttpCookie c : cookies) {
                    Log.d("CookieDebug", "Cookie after login page GET: " + c.toString());
                }

                // Step 2: GET stickyImg once WITHOUT ms to trigger captcha generation
                Request initCaptchaRequest = new Request.Builder()
                        .url(baseUrl + "stickyImg")
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .addHeader("Referer", baseUrl)
                        .build();

                Response initCaptchaResponse = client.newCall(initCaptchaRequest).execute();
                initCaptchaResponse.close();
                Log.d("CaptchaDebug", "Initial captcha generation GET done.");

                // Small delay to mimic JS timeout
                Thread.sleep(500);

                // Step 3: Generate correct ms parameter (0-999)
                Calendar calendar = Calendar.getInstance();
                int ms = calendar.get(Calendar.MILLISECOND);

                // Final captcha URL
                captchaUrl = baseUrl + "stickyImg?ms=" + ms;
                Log.d("CaptchaDebug", "Final captcha URL: " + captchaUrl);

                // Step 4: Fetch captcha image
                Request captchaRequest = new Request.Builder()
                        .url(captchaUrl)
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .addHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                        .addHeader("Accept-Encoding", "gzip, deflate, br")
                        .addHeader("Referer", baseUrl)
                        .build();

                Response captchaResponse = client.newCall(captchaRequest).execute();
                try {
                    if (!captchaResponse.isSuccessful()) {
                        Log.d("CaptchaDebug", "Captcha request failed: " + captchaResponse.code());
                        throw new Exception("Captcha request failed with code " + captchaResponse.code());
                    }

                    InputStream inputStream = captchaResponse.body().byteStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                    if (bitmap != null) {
                        mainHandler.post(() -> {
                            imgCaptcha.setImageBitmap(bitmap);
                            progressBar.setVisibility(View.GONE);
                        });
                    } else {
                        throw new Exception("Bitmap decoding failed (null bitmap)");
                    }
                } finally {
                    captchaResponse.close();
                }

            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    Toast.makeText(this, "Error loading captcha. Try again.", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                });
            }
        });
    }

    private void performLogin() {
        String regNo = etRegister.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String captcha = etCaptcha.getText().toString().trim();

        if (regNo.isEmpty() || password.isEmpty() || captcha.isEmpty()) {
            Toast.makeText(this, "Please fill all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        executorService.execute(() -> {
            try {
                // Build form body
                FormBody formBody = new FormBody.Builder()
                        .add("regno", regNo)
                        .add("passwd", password)
                        .add("vrfcd", captcha)
                        .build();

                Request request = new Request.Builder()
                        .url(baseUrl + "loginValidate")
                        .post(formBody)
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .addHeader("Referer", baseUrl)
                        .build();

                Response response = client.newCall(request).execute();
                String responseHtml = response.body().string();
                response.close();

                if (responseHtml.contains("Invalid") || responseHtml.contains("incorrect")) {
                    mainHandler.post(() -> {
                        Toast.makeText(this, "Login failed. Check credentials or captcha.", Toast.LENGTH_SHORT).show();
                        fetchLoginPageAndCaptcha();
                        progressBar.setVisibility(View.GONE);
                    });
                } else {
                    // Login successful. Fetch timetable
                    fetchTimetableAndProceed();
                }

            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    Toast.makeText(this, "Login error. Retry.", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                });
            }
        });
    }

    private void fetchTimetableAndProceed() {
        executorService.execute(() -> {
            try {
                Request timetableRequest = new Request.Builder()
                        .url(baseUrl + "student/timetable")
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .addHeader("Referer", baseUrl)
                        .build();

                Response timetableResponse = client.newCall(timetableRequest).execute();
                String timetableHtml = timetableResponse.body().string();
                timetableResponse.close();

                // TODO: Parse timetable HTML here and save data as needed

                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                });

            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    Toast.makeText(this, "Error fetching timetable.", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                });
            }
        });
    }
}
