package com.harish.dndscheduler;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Edge-to-edge handling
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // DND Manager setup
        DNDManager dndManager = new DNDManager(this);
        Button btnEnableDND = findViewById(R.id.btn_enable);
        Button btnDisableDND = findViewById(R.id.btn_disable);

        // Null check for buttons (safety measure)
        if (btnEnableDND != null && btnDisableDND != null) {
            btnEnableDND.setOnClickListener(v -> {
                dndManager.enableDND();
                Toast.makeText(this, "DND Enabled", Toast.LENGTH_SHORT).show();
            });

            btnDisableDND.setOnClickListener(v -> {
                dndManager.disableDND();
                Toast.makeText(this, "DND Disabled", Toast.LENGTH_SHORT).show();
            });
        } else {
            Toast.makeText(this, "Error: Buttons not found", Toast.LENGTH_LONG).show();
        }
    }
}