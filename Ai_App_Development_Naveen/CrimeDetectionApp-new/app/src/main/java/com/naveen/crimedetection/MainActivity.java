package com.naveen.crimedetection;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.RecordingStats;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private PreviewView previewView;
    private TextView statusText, timerText;
    private Button recordButton, uploadButton;
    private VideoCapture<Recorder> videoCapture;
    private Recording recording;
    private File lastRecordedFile;

    private final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };

    // Launcher for the file picker
    private final ActivityResultLauncher<String> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    handleSelectedFile(uri);
                } else {
                    Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        statusText = findViewById(R.id.statusText);
        timerText = findViewById(R.id.timerText);
        recordButton = findViewById(R.id.recordButton);
        uploadButton = findViewById(R.id.uploadButton);

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, 100);
        }

        recordButton.setOnClickListener(v -> {
            if (recording != null) {
                stopRecording();
            } else {
                startRecording();
            }
        });

        uploadButton.setOnClickListener(v -> openFilePicker());
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HD))
                        .build();

                videoCapture = VideoCapture.withOutput(recorder);

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        videoCapture
                );

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void startRecording() {
        if (videoCapture == null) return;

        recordButton.setEnabled(false);

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(System.currentTimeMillis());

        lastRecordedFile = new File(
                getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                timeStamp + ".mp4"
        );

        FileOutputOptions options = new FileOutputOptions.Builder(lastRecordedFile).build();

        recording = videoCapture.getOutput()
                .prepareRecording(this, options)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(this), recordEvent -> {
                    if (recordEvent instanceof VideoRecordEvent.Start) {
                        statusText.setText("Status: RECORDING");
                        recordButton.setText("Stop Recording");
                        recordButton.setEnabled(true);
                        Toast.makeText(this, "Recording Started", Toast.LENGTH_SHORT).show();
                    } else if (recordEvent instanceof VideoRecordEvent.Status) {
                        RecordingStats stats = recordEvent.getRecordingStats();
                        long durationNanos = stats.getRecordedDurationNanos();
                        long seconds = TimeUnit.NANOSECONDS.toSeconds(durationNanos);
                        long minutes = seconds / 60;
                        seconds = seconds % 60;
                        timerText.setText(String.format(Locale.getDefault(), "Recording: %02d:%02d", minutes, seconds));
                    } else if (recordEvent instanceof VideoRecordEvent.Finalize) {
                        statusText.setText("Status: READY");
                        recordButton.setText("Start Recording");
                        recordButton.setEnabled(true);
                        
                        VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) recordEvent;
                        if (!finalizeEvent.hasError()) {
                            Toast.makeText(this, "Video Saved: " + lastRecordedFile.getName(), Toast.LENGTH_LONG).show();
                        } else {
                            recording = null;
                            Toast.makeText(this, "Error recording: " + finalizeEvent.getError(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void stopRecording() {
        if (recording != null) {
            recording.stop();
            recording = null;
        }
    }

    private void openFilePicker() {
        // Launches the system file picker to select a video
        filePickerLauncher.launch("video/*");
    }

    private void handleSelectedFile(Uri uri) {
        statusText.setText("Status: UPLOADING...");
        
        // Simulating upload of the selected file
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            statusText.setText("Status: READY");
            Toast.makeText(this, "Selected clip uploaded successfully!", Toast.LENGTH_SHORT).show();
        }, 2000);
    }
}